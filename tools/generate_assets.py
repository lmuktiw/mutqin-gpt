#!/usr/bin/env python3
"""
Membuat aset offline Mutqin (dijalankan oleh workflow "Generate Assets", cukup sekali):

  app/src/main/assets/pages/pNNN.json   teks Uthmani per halaman (604 berkas)
  app/src/main/assets/audio/pNNN.mp3    3,5 detik pertama ayat pembuka tiap halaman (604 berkas)

Sumber teks : api.quran.com (verses/by_page, kata per kata)
Sumber audio: everyayah.com (Alafasy_128kbps), dipotong & dikompres dengan ffmpeg.

Pemakaian:
  python tools/generate_assets.py                 # teks + audio + validasi
  python tools/generate_assets.py --text          # hanya teks
  python tools/generate_assets.py --audio         # hanya audio
  python tools/generate_assets.py --validate-only # hanya validasi berkas yang ada
  python tools/generate_assets.py --force         # unduh ulang walau berkas sudah ada

Validasi akan GAGAL (exit code 1) bila ada halaman kosong, ayat kurang/dobel, halaman tidak
berawal pada ayat yang tercatat di page_start_ayahs.json, atau klip audio rusak/tidak ada.
"""
import argparse
import json
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
PAGES_DIR = ASSETS / "pages"
AUDIO_DIR = ASSETS / "audio"
TOTAL_PAGES = 604
TOTAL_VERSES = 6236
CLIP_SECONDS = 3.5

API_URL = (
    "https://api.quran.com/api/v4/verses/by_page/{page}"
    "?words=true&word_fields=text_uthmani,line_number&per_page=50&page={n}"
)
AUDIO_URL = "https://everyayah.com/data/Alafasy_128kbps/{s:03d}{a:03d}.mp3"
HEADERS = {"User-Agent": "mutqin-asset-generator/1.0", "Accept": "application/json"}


def load_json(path: Path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


START_AYAHS = load_json(ASSETS / "page_start_ayahs.json")  # indeks 0 = halaman 1, isi [surat, ayat]
SURAH_TOTALS = {s["number"]: s["totalVerses"] for s in load_json(ASSETS / "surahs.json")}


def page_name(page: int, ext: str) -> str:
    return f"p{page:03d}.{ext}"


def http_get(url: str, retries: int = 5, timeout: int = 40) -> bytes:
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.read()
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Gagal mengunduh {url}: {last}")


# --------------------------------------------------------------------------- teks

def parse_verses(payload: dict):
    """Mengubah respons API menjadi daftar {'s','a','w':[kata,...],'l':[nomor baris,...]} (penanda akhir ayat dibuang).

    'l' = nomor baris (1..15) tiap kata pada halaman cetak mushaf Madinah; dipakai untuk menampilkan tata letak halaman.
    Jika API tidak memberi nomor baris untuk sebuah ayat, 'l' dihilangkan (aplikasi memakai tata letak mengalir)."""
    out = []
    for v in payload.get("verses", []):
        key = str(v.get("verse_key", ""))
        parts = key.split(":")
        if len(parts) != 2 or not parts[0].isdigit() or not parts[1].isdigit():
            continue
        words = []
        lines = []
        for w in v.get("words", []):
            if w.get("char_type_name") == "end":
                continue
            text = (w.get("text_uthmani") or w.get("text") or "").strip()
            if text:
                words.append(text)
                ln = w.get("line_number")
                lines.append(int(ln) if isinstance(ln, int) or (isinstance(ln, str) and ln.isdigit()) else 0)
        if words:
            row = {"s": int(parts[0]), "a": int(parts[1]), "w": words}
            if all(x > 0 for x in lines):
                row["l"] = lines
            out.append(row)
    return out


def fetch_page_text(page: int):
    verses = []
    n = 1
    while n <= 10:
        payload = json.loads(http_get(API_URL.format(page=page, n=n)))
        verses.extend(parse_verses(payload))
        nxt = (payload.get("pagination") or {}).get("next_page")
        if not nxt or nxt <= n:
            break
        n = nxt
    return verses


def page_has_lines(path: Path) -> bool:
    """True jika berkas halaman sudah memuat nomor baris ('l') — berkas lama (tanpa nomor baris) dibuat ulang."""
    try:
        rows = load_json(path)
        return bool(rows) and all("l" in r for r in rows)
    except Exception:  # noqa: BLE001
        return False


def generate_text(force: bool):
    PAGES_DIR.mkdir(parents=True, exist_ok=True)

    def work(page: int):
        target = PAGES_DIR / page_name(page, "json")
        if target.exists() and not force and page_has_lines(target):
            return page, "lewati"
        verses = fetch_page_text(page)
        if not verses:
            raise RuntimeError(f"Halaman {page}: tidak ada ayat dari API")
        with open(target, "w", encoding="utf-8") as f:
            json.dump(verses, f, ensure_ascii=False, separators=(",", ":"))
        return page, "ok"

    run_parallel(work, "teks", workers=4)


# --------------------------------------------------------------------------- audio

def ffprobe_duration(path: Path) -> float:
    r = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)],
        capture_output=True, text=True,
    )
    return float(r.stdout.strip())


def generate_audio(force: bool):
    AUDIO_DIR.mkdir(parents=True, exist_ok=True)

    def work(page: int):
        target = AUDIO_DIR / page_name(page, "mp3")
        if target.exists() and target.stat().st_size > 2000 and not force:
            return page, "lewati"
        s, a = START_AYAHS[page - 1]
        data = http_get(AUDIO_URL.format(s=s, a=a))
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "src.mp3"
            src.write_bytes(data)
            out = Path(tmp) / "clip.mp3"
            cmd = [
                "ffmpeg", "-y", "-loglevel", "error", "-i", str(src),
                "-t", str(CLIP_SECONDS), "-ac", "1", "-ar", "44100",
                "-c:a", "libmp3lame", "-b:a", "48k", "-map_metadata", "-1",
                "-af", "afade=t=out:st=3.2:d=0.3",
                str(out),
            ]
            r = subprocess.run(cmd, capture_output=True, text=True)
            if r.returncode != 0 or not out.exists():
                raise RuntimeError(f"ffmpeg gagal untuk halaman {page}: {r.stderr.strip()}")
            target.write_bytes(out.read_bytes())
        return page, "ok"

    run_parallel(work, "audio", workers=6)


# --------------------------------------------------------------------------- umum

def run_parallel(work, label: str, workers: int):
    errors = []
    done = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(work, p): p for p in range(1, TOTAL_PAGES + 1)}
        for fut, page in futures.items():
            try:
                fut.result()
            except Exception as e:  # noqa: BLE001
                errors.append(f"halaman {page}: {e}")
            done += 1
            if done % 50 == 0:
                print(f"[{label}] {done}/{TOTAL_PAGES}", flush=True)
    if errors:
        print(f"[{label}] {len(errors)} halaman gagal:", file=sys.stderr)
        for line in errors[:30]:
            print("  - " + line, file=sys.stderr)
        sys.exit(1)
    print(f"[{label}] selesai ({TOTAL_PAGES} halaman)")


# --------------------------------------------------------------------------- validasi

def validate_text(pages_dir: Path = PAGES_DIR, start_ayahs=START_AYAHS, surah_totals=SURAH_TOTALS):
    problems = []
    flat = []  # urutan seluruh ayat sesuai urutan halaman
    for page in range(1, TOTAL_PAGES + 1):
        path = pages_dir / page_name(page, "json")
        if not path.exists():
            problems.append(f"halaman {page}: berkas teks tidak ada")
            continue
        try:
            verses = load_json(path)
        except Exception as e:  # noqa: BLE001
            problems.append(f"halaman {page}: JSON rusak ({e})")
            continue
        if not verses:
            problems.append(f"halaman {page}: kosong")
            continue
        first = (verses[0]["s"], verses[0]["a"])
        expected = tuple(start_ayahs[page - 1])
        if first != expected:
            problems.append(f"halaman {page}: ayat pertama {first} != {expected} (page_start_ayahs.json)")
        for v in verses:
            if not v.get("w"):
                problems.append(f"halaman {page}: ayat {v.get('s')}:{v.get('a')} tanpa kata")
            flat.append((v["s"], v["a"]))

    if not problems:
        if len(flat) != TOTAL_VERSES:
            problems.append(f"jumlah ayat {len(flat)} != {TOTAL_VERSES}")
        if len(set(flat)) != len(flat):
            problems.append("ada ayat ganda antar halaman")
        # kontinuitas: tiap ayat = ayat berikutnya dari ayat sebelumnya
        for prev, cur in zip(flat, flat[1:]):
            s, a = prev
            nxt = (s, a + 1) if a < surah_totals[s] else (s + 1, 1)
            if cur != nxt:
                problems.append(f"urutan ayat putus: {prev} diikuti {cur}, seharusnya {nxt}")
                break
        if flat and (flat[0] != (1, 1) or flat[-1] != (114, surah_totals[114])):
            problems.append("ayat pertama/terakhir Al-Qur'an tidak sesuai")
    return problems


def line_warnings(pages_dir: Path = PAGES_DIR):
    """Peringatan (bukan kegagalan): halaman tanpa nomor baris atau dengan urutan baris yang janggal."""
    warnings = []
    missing = 0
    for page in range(1, TOTAL_PAGES + 1):
        path = pages_dir / page_name(page, "json")
        if not path.exists():
            continue
        try:
            rows = load_json(path)
        except Exception:  # noqa: BLE001
            continue
        if not rows or not all("l" in r for r in rows):
            missing += 1
            continue
        flat = [ln for r in rows for ln in r["l"]]
        if any(ln < 1 or ln > 15 for ln in flat) or any(b < a for a, b in zip(flat, flat[1:])):
            warnings.append(f"halaman {page}: nomor baris janggal")
    if missing:
        warnings.append(f"{missing} halaman tanpa nomor baris (tata letak halaman memakai cadangan mengalir)")
    return warnings


def validate_audio(audio_dir: Path = AUDIO_DIR):
    problems = []
    for page in range(1, TOTAL_PAGES + 1):
        path = audio_dir / page_name(page, "mp3")
        if not path.exists():
            problems.append(f"halaman {page}: berkas audio tidak ada")
            continue
        if path.stat().st_size < 2000:
            problems.append(f"halaman {page}: audio terlalu kecil ({path.stat().st_size} B)")
            continue
        try:
            dur = ffprobe_duration(path)
        except Exception as e:  # noqa: BLE001
            problems.append(f"halaman {page}: ffprobe gagal ({e})")
            continue
        if not (1.0 <= dur <= CLIP_SECONDS + 0.25):
            problems.append(f"halaman {page}: durasi audio {dur:.2f} dtk di luar batas")
    return problems


def validate_all():
    problems = validate_text() + validate_audio()
    if problems:
        print(f"VALIDASI GAGAL ({len(problems)} masalah):", file=sys.stderr)
        for line in problems[:40]:
            print("  - " + line, file=sys.stderr)
        sys.exit(1)
    for w in line_warnings():
        print("PERINGATAN: " + w, file=sys.stderr)
    total = sum(p.stat().st_size for p in AUDIO_DIR.glob("*.mp3")) + sum(p.stat().st_size for p in PAGES_DIR.glob("*.json"))
    print(f"VALIDASI OK: 604 halaman teks, 6236 ayat, 604 klip audio ({total / 1_048_576:.1f} MB)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--text", action="store_true")
    ap.add_argument("--audio", action="store_true")
    ap.add_argument("--validate-only", action="store_true")
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    if not args.validate_only:
        do_text = args.text or not args.audio
        do_audio = args.audio or not args.text
        if do_text:
            generate_text(args.force)
        if do_audio:
            generate_audio(args.force)
    validate_all()


if __name__ == "__main__":
    main()
