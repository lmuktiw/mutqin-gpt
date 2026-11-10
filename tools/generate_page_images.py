#!/usr/bin/env python3
"""
Membuat aset gambar halaman Al-Qur'an + kotak kata untuk Mutqin (dijalankan sekali oleh workflow "Generate Page Images"):

  app/src/main/assets/pages_img/pNNN.webp   gambar halaman utuh (tata letak persis mushaf Madinah cetak)
  app/src/main/assets/page_boxes/pNNN.json  posisi tiap kata + penanda akhir ayat (dinormalkan 0..10000)

Gambar diambil dari sumber penyedia gambar per halaman (BUKAN dibuat sendiri). Sumber ditentukan lewat:
  --url "https://.../page{page:03d}.png"   (atau variabel lingkungan MUTQIN_PAGE_IMAGE_URL)  -> diunduh per halaman
  --source-dir folder                       -> memakai gambar yang sudah ada (nama berkas memuat nomor halaman, mis. 001.png / page001.png / 1.png)

Gambar HARUS cetakan 15 baris mushaf Madinah 1405 (sama dengan sumber teks assets/pages: api.quran.com / QCF v1), karena
posisi kata dicari dengan mencocokkan nomor baris dan jumlah kata di assets/pages/pNNN.json.
Perhatikan lisensi sumber gambar (banyak yang hanya bebas untuk pemakaian nonkomersial) dan cantumkan atribusinya.

Sumber ketiga (disarankan): quran-ws/quran-svg (SVG halaman mushaf KFQC + JSON poligon tiap ayat, CC BY 4.0 untuk poligon):
  --svg-base "https://cdn.quran.ws/svg/pages/v1.1.1/hafs-kfqc/{page:03d}"   -> mengunduh {page}.svg dan {page}.json, SVG dirender
  ke gambar (butuh `pip install cairosvg` dan libcairo2). Poligon ayat dipakai sebagai petunjuk pasti untuk pita baris dan rentang
  tiap ayat per baris, sehingga kotak kata jauh lebih terarah daripada deteksi dari gambar saja. Halaman 1-2 (poligon berbentuk
  tangga) memakai deteksi gambar biasa pada render yang sudah dipangkas ke area tulisan.

Pemakaian:
  python tools/generate_page_images.py --svg-base "https://cdn.quran.ws/svg/pages/v1.1.1/hafs-kfqc/{page:03d}"
  python tools/generate_page_images.py --url "https://host/path/page{page:03d}.png"
  python tools/generate_page_images.py --source-dir ./gambar --width 1260
  python tools/generate_page_images.py --validate-only
  python tools/generate_page_images.py --pages 1,2,3 --debug-dir /tmp/debug     # uji beberapa halaman + gambar debug

Keluar dengan kode 1 bila ada halaman yang kotaknya gagal dibuat / jumlah token tidak cocok dengan teks.
Halaman dengan "potongan fallback" (celah antarkata tidak jelas) hanya diberi peringatan dan dicatat di tools/page_images_report.json.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import page_boxes as pb  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
TEXT_DIR = ASSETS / "pages"
IMG_DIR = ASSETS / "pages_img"
BOX_DIR = ASSETS / "page_boxes"
REPORT = ROOT / "tools" / "page_images_report.json"
TOTAL_PAGES = 604
HEADERS = {"User-Agent": "mutqin-asset-generator/1.0"}
# Pola bawaan (kumpulan gambar Mushaf Madinah milik proyek Quran for Android). Belum diuji dari sisi pembuat skrip: ganti bila perlu.
DEFAULT_URL = "https://android.quran.com/data/width_1260/page{page:03d}.png"


def name(page: int, ext: str) -> str:
    return f"p{page:03d}.{ext}"


def http_get(url: str, retries: int = 5, timeout: int = 60) -> bytes:
    last = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=timeout) as r:
                return r.read()
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Gagal mengunduh {url}: {last}")


def find_local(source_dir: Path, page: int) -> Path:
    exts = {".png", ".webp", ".jpg", ".jpeg"}
    for f in source_dir.iterdir():
        if f.suffix.lower() in exts:
            digits = re.findall(r"\d+", f.stem)
            if digits and int(digits[-1]) == page:
                return f
    raise FileNotFoundError(f"Gambar halaman {page} tidak ditemukan di {source_dir}")


def load_rows(page: int) -> list[dict]:
    return json.loads((TEXT_DIR / name(page, "json")).read_text(encoding="utf-8"))


def expected_counts(rows: list[dict]) -> tuple[int, int]:
    return sum(len(r["w"]) for r in rows), len(rows)


def render_svg(svg_bytes: bytes, view: tuple[float, float, float, float], width: int) -> Image.Image:
    """Merender SVG halaman pada area `view`, tanpa lapisan poligon ayat, di atas latar putih."""
    import xml.etree.ElementTree as ET

    import cairosvg

    ET.register_namespace("", "http://www.w3.org/2000/svg")
    ET.register_namespace("xlink", "http://www.w3.org/1999/xlink")
    root = ET.fromstring(svg_bytes)
    for parent in root.iter():
        for child in list(parent):
            if "ayahPolygon" in (child.get("class") or ""):
                parent.remove(child)
    vx, vy, vw, vh = view
    root.set("viewBox", f"{vx} {vy} {vw} {vh}")
    for attr in ("width", "height"):
        root.attrib.pop(attr, None)
    png = cairosvg.svg2png(bytestring=ET.tostring(root), output_width=width, background_color="white")
    return Image.open(io.BytesIO(png))


def svg_view(svg_bytes: bytes, ayahs: list[dict]) -> tuple[float, float, float, float]:
    m = re.search(rb'viewBox="\s*([-\d.]+)[ ,]+([-\d.]+)[ ,]+([-\d.]+)[ ,]+([-\d.]+)', svg_bytes)
    if not m:
        raise RuntimeError("SVG tanpa viewBox")
    vx, vy, vw, vh = (float(g) for g in m.groups())
    bb = pb.polygon_union_bbox(ayahs)
    # halaman pembuka: tulisan hanya ~20% kotak -> pangkas ke area tulisan agar gambar tidak kosong
    if bb and ((bb[2] - bb[0]) * (bb[3] - bb[1])) / (vw * vh) < 0.6:
        m_ = 6.0
        return bb[0] - m_, bb[1] - m_, (bb[2] - bb[0]) + 2 * m_, (bb[3] - bb[1]) + 2 * m_
    return vx, vy, vw, vh


def process_page(page: int, args, debug_dir: Path | None):
    rows = load_rows(page)
    if args.svg_base:
        base = args.svg_base.format(page=page)
        svg = http_get(base + ".svg")
        ayahs = json.loads(http_get(base + ".json").decode("utf-8"))
        view = svg_view(svg, ayahs)
        out_img = render_svg(svg, view, args.width).convert("L")
        gray = pb.load_gray(out_img.convert("RGB"))
        result = pb.detect_page_polygons(gray, rows, ayahs, view)
        if result is None:  # data poligon tidak cocok / halaman pembuka: deteksi dari gambar saja
            result = pb.detect_page(gray, rows)
            result.problems = list(result.problems)
        IMG_DIR.mkdir(parents=True, exist_ok=True)
        BOX_DIR.mkdir(parents=True, exist_ok=True)
        out_img.save(IMG_DIR / name(page, "webp"), "WEBP", quality=args.quality, method=6)
        (BOX_DIR / name(page, "json")).write_text(json.dumps(result.to_json(), separators=(",", ":")), encoding="utf-8")
        if debug_dir is not None:
            debug_dir.mkdir(parents=True, exist_ok=True)
            pb.draw_debug(gray, result).save(debug_dir / name(page, "png"))
        return page, result
    if args.source_dir:
        im = Image.open(find_local(Path(args.source_dir), page))
        im.load()
    else:
        im = Image.open(io.BytesIO(http_get(args.url.format(page=page))))
        im.load()
    gray_full = pb.load_gray(im)
    # Satukan ke lebar target (gambar sumber bisa lebih lebar); deteksi dijalankan pada gambar yang akan dibundel.
    scale = args.width / gray_full.shape[1]
    new_size = (args.width, max(1, round(gray_full.shape[0] * scale)))
    base = im.convert("RGBA") if im.mode in ("RGBA", "LA", "P") else im.convert("RGB")
    if base.mode == "RGBA":
        bg = Image.new("RGBA", base.size, (255, 255, 255, 255))
        bg.alpha_composite(base)
        base = bg.convert("RGB")
    out_img = base.resize(new_size, Image.LANCZOS)
    # Halaman hampir hitam-putih -> simpan abu-abu (jauh lebih kecil)
    arr_rgb = out_img.convert("RGB")
    gray = pb.load_gray(arr_rgb)
    result = pb.detect_page(gray, rows)

    IMG_DIR.mkdir(parents=True, exist_ok=True)
    BOX_DIR.mkdir(parents=True, exist_ok=True)
    out_img.convert("L").save(IMG_DIR / name(page, "webp"), "WEBP", quality=args.quality, method=6)
    data = result.to_json()
    (BOX_DIR / name(page, "json")).write_text(json.dumps(data, separators=(",", ":")), encoding="utf-8")
    if debug_dir is not None:
        debug_dir.mkdir(parents=True, exist_ok=True)
        pb.draw_debug(gray, result).save(debug_dir / name(page, "png"))
    return page, result


def check_page(page: int) -> list[str]:
    """Validasi berkas yang sudah ada: jumlah kata/ayat cocok dengan teks, koordinat sah."""
    problems = []
    img, box = IMG_DIR / name(page, "webp"), BOX_DIR / name(page, "json")
    if not img.exists() or img.stat().st_size < 2000:
        problems.append("gambar tidak ada/rusak")
    if not box.exists():
        return problems + ["berkas kotak tidak ada"]
    try:
        data = json.loads(box.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        return problems + [f"JSON kotak rusak: {e}"]
    words, ayahs = expected_counts(load_rows(page))
    if len(data.get("t", [])) != words:
        problems.append(f"jumlah kotak kata {len(data.get('t', []))} != {words}")
    if len(data.get("e", [])) != ayahs:
        problems.append(f"jumlah penanda ayat {len(data.get('e', []))} != {ayahs}")
    for row in data.get("t", []):
        x0, y0, x1, y1 = row[3:7]
        if not (0 <= x0 < x1 <= pb.NORM and 0 <= y0 < y1 <= pb.NORM):
            problems.append(f"koordinat tidak sah {row}")
            break
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default=os.environ.get("MUTQIN_PAGE_IMAGE_URL") or DEFAULT_URL)
    ap.add_argument("--svg-base", default=os.environ.get("MUTQIN_SVG_BASE") or None,
                    help="pola alamat quran-svg tanpa ekstensi, mis. https://cdn.quran.ws/svg/pages/v1.1.1/hafs-kfqc/{page:03d}")
    ap.add_argument("--source-dir")
    ap.add_argument("--width", type=int, default=1260, help="lebar gambar yang dibundel (px)")
    ap.add_argument("--quality", type=int, default=85, help="mutu WebP 1..100")
    ap.add_argument("--pages", help="daftar halaman dipisah koma (bawaan: semua 604)")
    ap.add_argument("--force", action="store_true", help="buat ulang walau berkas sudah ada")
    ap.add_argument("--validate-only", action="store_true")
    ap.add_argument("--debug-dir", help="simpan gambar debug (kotak di atas halaman) ke folder ini")
    ap.add_argument("--workers", type=int, default=4)
    args = ap.parse_args()

    pages = [int(x) for x in args.pages.split(",")] if args.pages else list(range(1, TOTAL_PAGES + 1))
    debug_dir = Path(args.debug_dir) if args.debug_dir else None
    report: dict[str, dict] = {}
    failed: list[str] = []

    if not args.validate_only:
        todo = [p for p in pages if args.force or not (IMG_DIR / name(p, "webp")).exists() or not (BOX_DIR / name(p, "json")).exists()]
        print(f"Memproses {len(todo)} halaman (sumber: {args.svg_base or ('folder ' + args.source_dir if args.source_dir else args.url)})")

        def work(p):
            try:
                return process_page(p, args, debug_dir)
            except Exception as e:  # noqa: BLE001
                return p, e

        done = 0
        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            for p, res in ex.map(work, todo):
                done += 1
                if isinstance(res, Exception):
                    failed.append(f"halaman {p}: {res}")
                    print(f"  [{done}/{len(todo)}] p{p:03d} GAGAL: {res}")
                    continue
                report[str(p)] = {"fallback": res.fallback_cuts, "problems": res.problems, "band": res.bands.method}
                if done % 50 == 0 or done == len(todo):
                    print(f"  [{done}/{len(todo)}]")

    # ---- validasi
    bad = 0
    for p in pages:
        probs = check_page(p)
        if probs:
            bad += 1
            failed.append(f"halaman {p}: " + "; ".join(probs))
    imgs = len(list(IMG_DIR.glob("p*.webp"))) if IMG_DIR.exists() else 0
    boxes = len(list(BOX_DIR.glob("p*.json"))) if BOX_DIR.exists() else 0
    fb_pages = sorted(int(k) for k, v in report.items() if v["fallback"] > 0)
    REPORT.write_text(json.dumps({"fallback_pages": fb_pages, "pages": report}, indent=1), encoding="utf-8")
    print(f"\nGambar: {imgs}/{TOTAL_PAGES} | kotak: {boxes}/{TOTAL_PAGES} | halaman bermasalah: {bad}")
    if fb_pages:
        print(f"Peringatan: {len(fb_pages)} halaman punya potongan fallback (posisi kata diperkirakan): {fb_pages[:40]}{' ...' if len(fb_pages) > 40 else ''}")
        print("  Periksa dengan --pages N --debug-dir DIR (kotak di atas halaman).")
    if failed:
        print("\nGAGAL:")
        for line in failed[:60]:
            print("  -", line)
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
