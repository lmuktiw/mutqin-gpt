#!/usr/bin/env python3
"""
Uji mandiri detektor kotak (tools/page_boxes.py) TANPA internet: halaman mushaf disintesis dari
app/src/main/assets/pages/*.json memakai font Scheherazade (res/font), dengan tata letak 15 baris, baris penuh diratakan
(spasi direnggangkan), baris pendek di tengah, banner/basmalah sebagai baris berbingkai, dan nomor ayat dalam lingkaran.
Hasil deteksi dibandingkan dengan posisi sebenarnya (IoU sumbu-x per kotak).

Catatan: ini menguji ALGORITMA, bukan gambar asli sumber. Kualitas pada gambar asli diperiksa lewat laporan
yang dibuat generate_page_images.py (jumlah potongan fallback + gambar debug).

Pemakaian:  python tools/test_page_boxes.py [--pages 1,2,3,...|--all] [--debug-dir DIR]
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import page_boxes as pb  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
PAGES = ROOT / "app" / "src" / "main" / "assets" / "pages"
FONT = ROOT / "app" / "src" / "main" / "res" / "font" / "scheherazade_new_regular.ttf"

W, PITCH, MARGIN = 1000, 92, 70


def render(page: int, rows: list[dict]):
    n_lines = max(l for r in rows for l in r["l"])
    h = int(MARGIN * 2 + PITCH * max(n_lines, 15))
    im = Image.new("L", (W, h), 255)
    d = ImageDraw.Draw(im)
    body_w = W - 2 * MARGIN
    lines = pb.line_tokens(rows)

    def font(sz):
        return ImageFont.truetype(str(FONT), sz, layout_engine=ImageFont.Layout.RAQM)

    # ukuran huruf: baris terpanjang muat 0.97 lebar badan
    size = 58
    while size > 20:
        f = font(size)
        worst = 0
        for toks in lines.values():
            tot = sum(f.getlength(t.text, direction="rtl") if t.kind == "w" else 1.3 * size for t in toks)
            worst = max(worst, tot + 0.12 * size * (len(toks) - 1))
        if worst <= body_w * 0.97:
            break
        size -= 2
    f = font(size)
    truth = {}
    for ln in range(1, n_lines + 1):
        cy = MARGIN + (ln - 0.5) * PITCH
        toks = lines.get(ln)
        if not toks:
            d.rectangle([MARGIN, cy - PITCH * 0.38, W - MARGIN, cy + PITCH * 0.38], outline=0, width=3)
            d.text((W / 2, cy), "banner", font=f, fill=0, anchor="mm")
            continue
        widths = [f.getlength(t.text, direction="rtl") if t.kind == "w" else 1.3 * size for t in toks]
        natural = sum(widths) + 0.12 * size * (len(toks) - 1)
        if natural > 0.80 * body_w and len(toks) > 1:
            space = (body_w - sum(widths)) / (len(toks) - 1)
            x = W - MARGIN
        else:
            space = 0.12 * size
            x = (W + natural) / 2
        for t, wd in zip(toks, widths):
            x0, x1 = x - wd, x
            if t.kind == "w":
                d.text((x1, cy), t.text, font=f, fill=0, anchor="rm", direction="rtl")
            else:
                r = 0.55 * size
                c = (x0 + x1) / 2
                d.ellipse([c - r, cy - r, c + r, cy + r], outline=0, width=3)
                d.text((c, cy), str(t.ayah), font=ImageFont.truetype(str(FONT), int(size * 0.5)), fill=0, anchor="mm")
            truth[(t.kind, t.surah, t.ayah, t.index)] = (x0, x1, ln)
            x = x0 - space
    return np.asarray(im), truth


def fake_polygons(rows, truth, n_lines, img_w):
    """Meniru JSON poligon quran-svg: persegi panjang per ayat per baris; batas ayat di tengah baris = tepi kiri penanda."""
    lines = pb.line_tokens(rows)
    ayah_rects, markers = {}, {}
    for ln in sorted(lines):
        y0, y1 = MARGIN + (ln - 1) * PITCH, MARGIN + ln * PITCH
        toks = sorted(lines[ln], key=lambda t: -truth[(t.kind, t.surah, t.ayah, t.index)][1])
        right = float(img_w)
        groups = []
        for t in toks:
            if not groups or groups[-1][0] != (t.surah, t.ayah):
                groups.append([(t.surah, t.ayah), []])
            groups[-1][1].append(t)
        for gi, (key, gt) in enumerate(groups):
            mk = next((t for t in gt if t.kind == "e"), None)
            if mk is not None:
                m0, m1, _ = truth[(mk.kind, mk.surah, mk.ayah, mk.index)]
                left = m0
                markers[key] = ((m0 + m1) / 2, MARGIN + (ln - 0.5) * PITCH)
            else:
                left = 0.0 if gi == len(groups) - 1 else min(truth[(t.kind, t.surah, t.ayah, t.index)][0] for t in gt) - 1
            if gi == len(groups) - 1:
                left = 0.0
            ayah_rects.setdefault(key, []).append((left, y0, right, y1))
            right = left
    out = []
    for r in rows:
        key = (r["s"], r["a"])
        d = " ".join(f"M {a} {b} L {c} {b} L {c} {d_} L {a} {d_} Z" for a, b, c, d_ in ayah_rects[key])
        x, y = markers[key]
        out.append({"surahNumber": key[0], "ayahNumber": key[1], "x": x, "y": y, "polygon": d})
    return out


def iou_x(a0, a1, b0, b1):
    inter = max(0.0, min(a1, b1) - max(a0, b0))
    union = max(a1, b1) - min(a0, b0)
    return inter / union if union > 0 else 0.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pages", default="1,2,3,4,50,76,77,200,300,400,500,604")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--step", type=int, default=1, help="dengan --all: ambil tiap N halaman")
    ap.add_argument("--polygons", action="store_true", help="uji mode poligon ayat (meniru JSON quran-svg)")
    ap.add_argument("--debug-dir")
    args = ap.parse_args()
    pages = list(range(1, 605, args.step)) if args.all else [int(x) for x in args.pages.split(",")]

    tot = good = fb_pages = missing = 0
    worst = []
    for p in pages:
        rows = json.loads((PAGES / f"p{p:03d}.json").read_text(encoding="utf-8"))
        gray, truth = render(p, rows)
        if args.polygons:
            n_lines = max(l for r in rows for l in r["l"])
            res = pb.detect_page_polygons(gray, rows, fake_polygons(rows, truth, n_lines, gray.shape[1]), (0, 0, gray.shape[1], gray.shape[0]))
            if res is None:
                print(f"p{p}: mode poligon mengembalikan None (cadangan ke deteksi raster)")
                res = pb.detect_page(gray, rows)
        else:
            res = pb.detect_page(gray, rows)
        found = {}
        for ln in res.lines:
            for b in ln.boxes:
                key = (b.token.kind, b.token.surah, b.token.ayah, b.token.index)
                found[key] = (b.x0, b.x1)
        page_good = 0
        for key, (x0, x1, _ln) in truth.items():
            tot += 1
            if key not in found:
                missing += 1
                continue
            if iou_x(x0, x1, *found[key]) >= 0.6:
                good += 1
                page_good += 1
        frac = page_good / max(len(truth), 1)
        worst.append((frac, p, res.fallback_cuts, res.bands.method))
        fb_pages += 1 if res.fallback_cuts else 0
        if args.debug_dir:
            Path(args.debug_dir).mkdir(parents=True, exist_ok=True)
            pb.draw_debug(gray, res).save(Path(args.debug_dir) / f"p{p:03d}.png")
    worst.sort()
    print(f"halaman diuji: {len(pages)} | token: {tot} | cocok (IoU-x>=0.6): {good} ({100 * good / tot:.2f}%) | hilang: {missing}")
    print(f"halaman dengan potongan fallback: {fb_pages}")
    print("terburuk (frac, halaman, fallback, metode pita):", worst[:5])
    return 0 if good / tot >= 0.97 and missing == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
