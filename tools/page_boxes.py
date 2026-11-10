#!/usr/bin/env python3
"""
Deteksi kotak (bounding box) tiap kata dan nomor ayat pada GAMBAR halaman mushaf Madinah.

Dipakai oleh tools/generate_page_images.py. Hasilnya dibaca aplikasi untuk menaruh kotak transparan di atas gambar halaman.

Prinsip: kita sudah tahu PERSIS kata apa saja ada di tiap baris (assets/pages/pNNN.json punya nomor baris tiap kata),
jadi yang dicari hanyalah posisinya:
  1. Cari pita tiap baris cetak lewat profil jumlah piksel tinta per baris-piksel (pita ke-k = baris cetak ke-k).
  2. Pada tiap baris, cari celah kolom kosong (kandidat spasi antarkata), lalu pilih K-1 celah terbaik untuk K token
     (kata + penanda akhir ayat) dengan DP: celah lebar diutamakan, dekat posisi yang diharapkan dari proporsi jumlah huruf,
     dan tidak menghasilkan token yang terlalu sempit. Jika celah tidak cukup (huruf menyambung/diakritik menutup celah),
     posisi dipotong menurut proporsi huruf (ditandai sebagai 'fallback' agar bisa diperiksa).

Koordinat keluaran dinormalkan 0..10000 terhadap lebar/tinggi gambar:
  {"v":1,"w":W,"h":H,"t":[[surat,ayat,kata,x0,y0,x1,y1],...],"e":[[surat,ayat,x0,y0,x1,y1],...],"q":[...info mutu...]}
"""
from __future__ import annotations

import unicodedata
from dataclasses import dataclass, field

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter1d
from scipy.signal import find_peaks

NORM = 10000
MARKER_WEIGHT = 2.4  # lebar penanda akhir ayat dalam satuan "huruf"


# --------------------------------------------------------------------------- gambar -> tinta

def load_gray(path_or_image) -> np.ndarray:
    """Membaca gambar (PNG/WebP/JPG, boleh transparan) menjadi array abu-abu 0..255 (latar putih)."""
    im = path_or_image if isinstance(path_or_image, Image.Image) else Image.open(path_or_image)
    if im.mode in ("RGBA", "LA") or (im.mode == "P" and "transparency" in im.info):
        im = im.convert("RGBA")
        bg = Image.new("RGBA", im.size, (255, 255, 255, 255))
        bg.alpha_composite(im)
        im = bg
    rgb = np.asarray(im.convert("RGB"), dtype=np.uint8)
    # Teks berwarna (tajwid/merah) tetap dianggap tinta: pakai kanal terendah, bukan luminans.
    return rgb.min(axis=2)


def otsu(gray: np.ndarray) -> int:
    hist = np.bincount(gray.ravel(), minlength=256).astype(float)
    total = hist.sum()
    sum_all = (np.arange(256) * hist).sum()
    w0 = 0.0
    sum0 = 0.0
    best, thr = -1.0, 128
    for t in range(256):
        w0 += hist[t]
        if w0 == 0:
            continue
        w1 = total - w0
        if w1 == 0:
            break
        sum0 += t * hist[t]
        m0 = sum0 / w0
        m1 = (sum_all - sum0) / w1
        var = w0 * w1 * (m0 - m1) ** 2
        if var > best:
            best, thr = var, t
    return thr


def to_ink(gray: np.ndarray) -> np.ndarray:
    """True = tinta. Garis bingkai penuh (baris/kolom yang hampir seluruhnya tinta) dibuang."""
    thr = min(max(otsu(gray), 60), 200)
    ink = gray < thr
    rows = ink.mean(axis=1) > 0.85
    cols = ink.mean(axis=0) > 0.85
    ink[rows, :] = False
    ink[:, cols] = False
    return ink


# --------------------------------------------------------------------------- pita baris

@dataclass
class Bands:
    tops: list[float]
    bottoms: list[float]
    pitch: float
    method: str  # 'grid' | 'uniform'


def find_line_bands(ink: np.ndarray, n_lines: int) -> Bands:
    """Mencari n_lines pita baris berjarak seragam (baris cetak 1..n_lines dari atas) dengan mencocokkan GRID pada profil tinta.

    Mencocokkan grid (bukan menghitung puncak) membuat hasil tahan terhadap banner/bingkai bertinta ganda,
    nomor halaman, dan baris yang kosong di bagian bawah halaman.
    """
    h, _ = ink.shape
    prof = ink.sum(axis=1).astype(float)
    sm = gaussian_filter1d(prof, sigma=max(2.0, h * 0.008))
    peak_max = float(sm.max()) if sm.size else 0.0
    if peak_max <= 0:
        pitch = h / max(n_lines, 1)
        return Bands([i * pitch for i in range(n_lines)], [(i + 1) * pitch for i in range(n_lines)], pitch, "uniform")
    sm = sm / peak_max
    ys = np.arange(h, dtype=float)

    best = None  # (skor, c1, pitch)
    p_lo, p_hi = h * 0.030, h * 0.125
    for pitch in np.arange(p_lo, p_hi, 0.5):
        span = (n_lines - 1) * pitch
        c1s = np.arange(0.0, max(h - span, 1.0), 1.0)
        if len(c1s) == 0:
            continue
        ks = np.arange(n_lines)
        idx = c1s[:, None] + ks[None, :] * pitch
        if idx.max() > h - 1:
            keep = idx.max(axis=1) <= h - 1
            c1s, idx = c1s[keep], idx[keep]
            if len(c1s) == 0:
                continue
        peak = np.interp(idx, ys, sm).mean(axis=1)
        if n_lines > 1:
            vidx = idx[:, :-1] + 0.5 * pitch
            valley = np.interp(vidx, ys, sm).mean(axis=1)
        else:
            valley = np.zeros_like(peak)
        score = peak - 0.9 * valley
        i = int(np.argmax(score))
        if best is None or score[i] > best[0]:
            best = (float(score[i]), float(c1s[i]), float(pitch))

    if best is None:
        pitch = h / max(n_lines, 1)
        return Bands([i * pitch for i in range(n_lines)], [(i + 1) * pitch for i in range(n_lines)], pitch, "uniform")
    _, c1, pitch = best
    centers = [c1 + k * pitch for k in range(n_lines)]
    # geser tiap pusat ke puncak lokal terdekat (maks 0.2 pitch) bila ada
    snapped = []
    for c in centers:
        lo, hi = int(max(c - 0.2 * pitch, 0)), int(min(c + 0.2 * pitch, h - 1))
        snapped.append(float(lo + int(np.argmax(sm[lo:hi + 1]))) if hi > lo else c)
    centers = snapped
    bounds = [centers[0] - pitch / 2]
    for a, b in zip(centers[:-1], centers[1:]):
        lo, hi = int(a + 0.25 * pitch), int(b - 0.25 * pitch)
        seg = sm[lo:hi + 1]
        bounds.append(float(lo + int(np.argmin(seg))) if len(seg) else (a + b) / 2)
    bounds.append(centers[-1] + pitch / 2)
    bounds = [min(max(v, 0.0), float(h)) for v in bounds]
    return Bands(bounds[:-1], bounds[1:], pitch, "grid")


# --------------------------------------------------------------------------- token per baris

_MARKS = set(range(0x064B, 0x0660)) | {0x0670} | set(range(0x06D6, 0x06EE)) | set(range(0x08D3, 0x0900)) | {0x0640}


def letter_weight(word: str) -> float:
    n = sum(1 for ch in word if ord(ch) not in _MARKS and unicodedata.category(ch) != "Mn")
    return max(n, 1) + 0.3


@dataclass
class Token:
    kind: str  # 'w' | 'e'
    surah: int
    ayah: int
    index: int = 0  # nomor kata (1..n) untuk kind 'w'
    text: str = ""

    @property
    def weight(self) -> float:
        return letter_weight(self.text) if self.kind == "w" else MARKER_WEIGHT


def line_tokens(rows: list[dict]) -> dict[int, list[Token]]:
    """Mengelompokkan token (kata + penanda akhir ayat) per nomor baris, urut baca (kanan -> kiri)."""
    out: dict[int, list[Token]] = {}
    for r in rows:
        lines = r.get("l") or []
        words = r["w"]
        for i, w in enumerate(words):
            out.setdefault(lines[i], []).append(Token("w", r["s"], r["a"], i + 1, w))
        # Mushaf 1405: tiap halaman berakhir pada akhir ayat, jadi penanda selalu ada di baris kata terakhir ayat.
        out.setdefault(lines[-1], []).append(Token("e", r["s"], r["a"]))
    return out


@dataclass
class Box:
    token: Token
    x0: float
    y0: float
    x1: float
    y1: float
    fallback: bool = False


@dataclass
class LineResult:
    line: int
    boxes: list[Box] = field(default_factory=list)
    fallback_cuts: int = 0
    mean_dev: float = 0.0


def _gap_candidates(ink_core: np.ndarray, xl: int, xr: int, min_w: int = 2):
    """Celah kolom kosong antara xl..xr: daftar (pusat, lebar)."""
    cols = ink_core[:, xl:xr + 1].any(axis=0)
    gaps = []
    i, n = 0, len(cols)
    while i < n:
        if not cols[i]:
            j = i
            while j < n and not cols[j]:
                j += 1
            if j - i >= min_w:
                gaps.append((xl + (i + j - 1) / 2.0, float(j - i)))
            i = j
        else:
            i += 1
    return gaps


def _find_markers(ink: np.ndarray, y0: float, y1: float, pitch: float, skip_cols: np.ndarray, count: int):
    """Mencari `count` penanda akhir ayat (cincin berbentuk hampir persegi) lewat komponen terhubung.

    Mengembalikan daftar (x0, x1) urut kanan -> kiri, atau None bila jumlahnya tidak pas (jangan menebak)."""
    from scipy.ndimage import find_objects, label

    ya, yb = int(y0 + 0.04 * pitch), int(y1 - 0.04 * pitch)
    band = ink[ya:max(yb, ya + 1), :].copy()
    band[:, skip_cols] = False
    lab, n = label(band, structure=np.ones((3, 3)))
    cands = []
    for i, sl in enumerate(find_objects(lab), start=1):
        if sl is None:
            continue
        hh, ww = sl[0].stop - sl[0].start, sl[1].stop - sl[1].start
        if not (0.45 * pitch <= hh <= 1.15 * pitch and 0.45 * pitch <= ww <= 1.15 * pitch):
            continue
        asp = ww / hh
        if not (0.8 <= asp <= 1.25):
            continue
        comp = lab[sl] == i
        fill = float(comp.sum()) / (hh * ww)
        if not (0.08 <= fill <= 0.70):
            continue
        # cincin sejati: tengahnya kosong (angka nomor ayat adalah komponen lain) dan keempat sisinya bertinta
        cy0, cy1 = int(hh * 0.30), max(int(hh * 0.70), int(hh * 0.30) + 1)
        cx0, cx1 = int(ww * 0.30), max(int(ww * 0.70), int(ww * 0.30) + 1)
        if comp[cy0:cy1, cx0:cx1].mean() > 0.05:
            continue
        mh, mw = hh // 2, ww // 2
        if not (comp[:, :max(2, ww // 8)].any() and comp[:, -max(2, ww // 8):].any()
                and comp[:max(2, hh // 8), :].any() and comp[-max(2, hh // 8):, :].any()):
            continue
        score = abs(asp - 1.0) + abs(((hh + ww) / 2) / pitch - 0.75)
        cands.append((score, float(sl[1].start), float(sl[1].stop)))
    if len(cands) < count:
        return None
    cands.sort()
    chosen = cands[:count]
    if len(cands) > count and cands[count][0] < chosen[-1][0] * 1.15 + 0.02:
        return None  # ambigu: ada calon lain yang sama bagusnya
    return sorted(((c[1], c[2]) for c in chosen), key=lambda t: -t[0])


def _dp_split(core: np.ndarray, xl: int, xr: int, tokens: list[Token], pitch: float):
    """Membagi rentang [xl, xr] menjadi len(tokens) token. Mengembalikan (batas_x_kiri_kanan per token, jumlah fallback, simpangan)."""
    k = len(tokens)
    if k == 1:
        return [(float(xl), float(xr + 1))], 0, 0.0
    weights = np.array([t.weight for t in tokens], dtype=float)
    total_w = float(weights.sum())
    width = float(xr - xl + 1)
    cum = np.cumsum(weights)[:-1]
    expected = [xr + 1 - width * c / total_w for c in cum]  # posisi potongan ke-j (dari kanan)
    exp_w = width * weights / total_w

    gaps = _gap_candidates(core, xl, xr)
    cands = sorted([(c, g, False) for c, g in gaps] + [(e, 0.0, True) for e in expected], key=lambda t: -t[0])
    m = len(cands)
    A, LAM, MU = 6.0, 1.0, 8.0
    INF = float("inf")

    def own_cost(j: int, cand) -> float:
        c, g, _virt = cand
        return LAM * ((c - expected[j]) / pitch) ** 2 - A * min(g / pitch, 0.4)

    dp = [[INF] * m for _ in range(k - 1)]
    back = [[-1] * m for _ in range(k - 1)]
    for i, cand in enumerate(cands):
        wtok = (xr + 1) - cand[0]
        pen = MU * max(0.0, (0.35 * exp_w[0] - wtok) / pitch) ** 2
        dp[0][i] = own_cost(0, cand) + pen
    for j in range(1, k - 1):
        for i, cand in enumerate(cands):
            oc = own_cost(j, cand)
            best, arg = INF, -1
            for pi in range(i):
                prev = dp[j - 1][pi]
                if prev == INF:
                    continue
                wtok = cands[pi][0] - cand[0]
                if wtok < 2:
                    continue
                pen = MU * max(0.0, (0.35 * exp_w[j] - wtok) / pitch) ** 2
                v = prev + oc + pen
                if v < best:
                    best, arg = v, pi
            dp[j][i], back[j][i] = best, arg
    best, bi = INF, -1
    for i, cand in enumerate(cands):
        if dp[k - 2][i] == INF:
            continue
        wtok = cand[0] - xl
        pen = MU * max(0.0, (0.35 * exp_w[k - 1] - wtok) / pitch) ** 2
        v = dp[k - 2][i] + pen
        if v < best:
            best, bi = v, i
    if bi < 0:
        cuts = [(e, 0.0, True) for e in expected]
    else:
        chain = [bi]
        for j in range(k - 2, 0, -1):
            chain.append(back[j][chain[-1]])
        cuts = [cands[i] for i in reversed(chain)]
    fallback = sum(1 for c in cuts if c[2])
    dev = float(np.mean([abs(c[0] - e) for c, e in zip(cuts, expected)]) / pitch)
    edges = [xr + 1.0] + [c[0] for c in cuts] + [float(xl)]
    return [(edges[i + 1], edges[i]) for i in range(k)], fallback, dev


def split_line(ink: np.ndarray, top: float, bottom: float, pitch: float, tokens: list[Token], skip_cols: np.ndarray):
    """Membagi satu baris menjadi len(tokens) kotak. Mengembalikan (daftar Box, jumlah potongan fallback, simpangan rata-rata)."""
    h, w = ink.shape
    y0, y1 = int(max(top, 0)), int(min(bottom, h))
    core_pad = 0.14 * (y1 - y0)
    cy0, cy1 = int(y0 + core_pad), int(y1 - core_pad)
    core = ink[cy0:max(cy1, cy0 + 1), :].copy()
    core[:, skip_cols] = False
    xs = np.where(core.any(axis=0))[0]
    if len(xs) == 0:
        return [], 0, 0.0
    xl, xr = int(xs[0]), int(xs[-1])
    k = len(tokens)
    box_y0 = y0 + 0.06 * pitch
    box_y1 = y1 - 0.06 * pitch
    ink_cols = core.any(axis=0)

    spans: list[tuple[float, float]] = [(0.0, 0.0)] * k  # (kiri, kanan) per token
    fallback_total, devs = 0, []
    marker_idx = [i for i, t in enumerate(tokens) if t.kind == "e"]
    anchors = _find_markers(ink, y0, y1, pitch, skip_cols, len(marker_idx)) if marker_idx else None

    if anchors is not None:
        for i, (mx0, mx1) in zip(marker_idx, anchors):
            spans[i] = (mx0, mx1)
        # segmen kata di antara penanda
        seg_start = 0
        bounds_right = float(xr + 1)
        for pos in marker_idx + [k]:
            seg = list(range(seg_start, pos))
            seg_left = float(xl) if pos == k else spans[pos][1]
            if seg:
                a, b = int(max(seg_left, 0)), int(min(bounds_right, w))
                sub = np.where(ink_cols[a:b])[0]
                if len(sub):
                    sxl, sxr = a + int(sub[0]), a + int(sub[-1])
                    parts, fb, dev = _dp_split(core, sxl, sxr, [tokens[i] for i in seg], pitch)
                    for i, pr in zip(seg, parts):
                        spans[i] = pr
                    fallback_total += fb
                    devs.append(dev)
                else:
                    for i in seg:
                        spans[i] = (seg_left, bounds_right)
                    fallback_total += max(len(seg) - 1, 0)
            if pos < k:
                bounds_right = spans[pos][0]
                seg_start = pos + 1
    else:
        parts, fb, dev = _dp_split(core, xl, xr, tokens, pitch)
        spans = parts
        fallback_total, devs = fb, [dev]

    # kotak rapat pada tinta, lalu diperluas sedikit ke arah tetangga
    tight = []
    for (left, right) in spans:
        a, b = int(max(left, 0)), int(min(right, w))
        seg = np.where(ink_cols[a:b])[0]
        tight.append((a + float(seg[0]), a + float(seg[-1]) + 1) if len(seg) else (left, right))
    boxes = []
    for idx, t in enumerate(tokens):
        x0, x1 = tight[idx]
        lg = (x0 - tight[idx + 1][1]) / 2 if idx + 1 < k else 0.0
        rg = (tight[idx - 1][0] - x1) / 2 if idx > 0 else 0.0
        x0 -= min(max(lg, 0.0), 0.10 * pitch)
        x1 += min(max(rg, 0.0), 0.10 * pitch)
        if t.kind == "e" and (x1 - x0) < 0.8 * pitch:
            c = (x0 + x1) / 2
            x0, x1 = c - 0.4 * pitch, c + 0.4 * pitch
        boxes.append(Box(t, x0, box_y0, x1, box_y1, fallback=False))
    # baris yang memakai potongan perkiraan (tanpa celah nyata) ditandai penuh agar tampak merah pada gambar debug
    if fallback_total:
        for b in boxes:
            b.fallback = True
    return boxes, fallback_total, float(np.mean(devs)) if devs else 0.0


# --------------------------------------------------------------------------- halaman

@dataclass
class PageResult:
    width: int
    height: int
    lines: list[LineResult]
    bands: Bands
    n_lines: int
    problems: list[str]

    @property
    def fallback_cuts(self) -> int:
        return sum(l.fallback_cuts for l in self.lines)

    def to_json(self) -> dict:
        def nx(v: float) -> int:
            return int(round(min(max(v / self.width, 0.0), 1.0) * NORM))

        def ny(v: float) -> int:
            return int(round(min(max(v / self.height, 0.0), 1.0) * NORM))

        t, e = [], []
        for ln in self.lines:
            for b in ln.boxes:
                if b.token.kind == "w":
                    t.append([b.token.surah, b.token.ayah, b.token.index, nx(b.x0), ny(b.y0), nx(b.x1), ny(b.y1)])
                else:
                    e.append([b.token.surah, b.token.ayah, nx(b.x0), ny(b.y0), nx(b.x1), ny(b.y1)])
        return {"v": 1, "w": self.width, "h": self.height, "t": t, "e": e, "q": [self.fallback_cuts, len(self.problems)]}


def detect_page(gray: np.ndarray, rows: list[dict]) -> PageResult:
    h, w = gray.shape
    ink = to_ink(gray)
    by_line = line_tokens(rows)
    n_lines = max(by_line) if by_line else 1
    bands = find_line_bands(ink, n_lines)

    # kolom bingkai vertikal: tinta hampir di seluruh tinggi badan
    body_top, body_bot = int(bands.tops[0]), int(bands.bottoms[-1])
    body = ink[body_top:max(body_bot, body_top + 1), :]
    skip = body.mean(axis=0) > 0.9

    problems: list[str] = []
    results: list[LineResult] = []
    for line_no in sorted(by_line):
        tokens = by_line[line_no]
        idx = line_no - 1
        if idx >= len(bands.tops):
            problems.append(f"baris {line_no} di luar pita yang terdeteksi")
            continue
        boxes, fb, dev = split_line(ink, bands.tops[idx], bands.bottoms[idx], bands.pitch, tokens, skip)
        if len(boxes) != len(tokens):
            problems.append(f"baris {line_no}: tidak ada tinta ditemukan")
            continue
        results.append(LineResult(line_no, boxes, fb, dev))
    return PageResult(w, h, results, bands, n_lines, problems)


# --------------------------------------------------------------------------- mode poligon ayat (quran-svg)

import re as _re

_NUM = _re.compile(r"-?\d+(?:\.\d+)?")


def polygon_rects(polygon: str) -> list[tuple[float, float, float, float]] | None:
    """Poligon ayat quran-svg (halaman >= 3): path data, satu subpath persegi panjang per baris cetak -> daftar (x0,y0,x1,y1).
    None bila bukan path data (halaman 1-2 memakai daftar titik tangga)."""
    poly = polygon.strip()
    if not poly.startswith("M"):
        return None
    rects = []
    for sub in _re.split(r"(?=M)", poly):
        nums = list(map(float, _NUM.findall(sub)))
        if len(nums) < 8:
            continue
        xs, ys = nums[0::2], nums[1::2]
        rects.append((min(xs), min(ys), max(xs), max(ys)))
    return rects or None


def polygon_union_bbox(ayahs: list[dict]) -> tuple[float, float, float, float] | None:
    xs, ys = [], []
    for a in ayahs:
        nums = list(map(float, _NUM.findall(a.get("polygon", ""))))
        xs += nums[0::2]
        ys += nums[1::2]
    return (min(xs), min(ys), max(xs), max(ys)) if xs else None


def _group_bands(rects: list[tuple[float, float, float, float]]) -> list[tuple[float, float]]:
    """Menyatukan rentang-y semua persegi panjang menjadi pita baris (urut atas -> bawah)."""
    spans = sorted((r[1], r[3]) for r in rects)
    bands: list[list[float]] = []
    for y0, y1 in spans:
        if bands and y0 < bands[-1][1] - 1.0:
            bands[-1][0] = min(bands[-1][0], y0)
            bands[-1][1] = max(bands[-1][1], y1)
        else:
            bands.append([y0, y1])
    return [(a, b) for a, b in bands]


def _ring_near(ink: np.ndarray, cx: float, cy: float, pitch: float, skip_cols: np.ndarray):
    """Cincin penanda ayat di sekitar titik (cx, cy) dari JSON poligon: mengembalikan (x0, x1) piksel, atau None."""
    from scipy.ndimage import find_objects, label

    h, w = ink.shape
    ya, yb = int(max(cy - 0.8 * pitch, 0)), int(min(cy + 0.8 * pitch, h))
    xa, xb = int(max(cx - 1.1 * pitch, 0)), int(min(cx + 1.1 * pitch, w))
    if yb <= ya or xb <= xa:
        return None
    win = ink[ya:yb, xa:xb].copy()
    win[:, skip_cols[xa:xb]] = False
    lab, _n = label(win, structure=np.ones((3, 3)))
    best = None
    for i, sl in enumerate(find_objects(lab), start=1):
        if sl is None:
            continue
        hh, ww = sl[0].stop - sl[0].start, sl[1].stop - sl[1].start
        if not (0.4 * pitch <= hh <= 1.2 * pitch and 0.4 * pitch <= ww <= 1.2 * pitch and 0.75 <= ww / hh <= 1.33):
            continue
        comp = lab[sl] == i
        if not (0.08 <= comp.sum() / (hh * ww) <= 0.72):
            continue
        c0, c1 = int(hh * 0.30), max(int(hh * 0.70), int(hh * 0.30) + 1)
        d0, d1 = int(ww * 0.30), max(int(ww * 0.70), int(ww * 0.30) + 1)
        if comp[c0:c1, d0:d1].mean() > 0.05:
            continue
        mx, my = xa + (sl[1].start + sl[1].stop) / 2, ya + (sl[0].start + sl[0].stop) / 2
        dist = abs(mx - cx) + abs(my - cy)
        if best is None or dist < best[0]:
            best = (dist, float(xa + sl[1].start), float(xa + sl[1].stop))
    return (best[1], best[2]) if best and best[0] < 0.7 * pitch else None


def detect_page_polygons(gray: np.ndarray, rows: list[dict], ayahs: list[dict], view: tuple[float, float, float, float]) -> PageResult | None:
    """Deteksi memakai poligon ayat quran-svg: pita baris dan rentang-x tiap ayat per baris PASTI (dari JSON), posisi
    penanda ayat diketahui (x, y), sehingga pemotongan kata hanya terjadi di dalam satu ayat pada satu baris.

    gray adalah render SVG pada area `view` = (x, y, lebar, tinggi) dalam satuan SVG. Mengembalikan None bila data tidak cocok
    dengan teks halaman (jumlah baris/ayat berbeda): panggil detect_page() sebagai cadangan."""
    h, w = gray.shape
    vx, vy, vw, vh = view
    sx, sy = w / vw, h / vh

    by_line = line_tokens(rows)
    ay_rects: dict[tuple[int, int], list[tuple[float, float, float, float]]] = {}
    markers: dict[tuple[int, int], tuple[float, float]] = {}
    for a in ayahs:
        rects = polygon_rects(a["polygon"])
        if rects is None:
            return None
        key = (a["surahNumber"], a["ayahNumber"])
        ay_rects[key] = rects
        markers[key] = (a["x"], a["y"])
    text_keys = {(r["s"], r["a"]) for r in rows}
    if set(ay_rects) != text_keys:
        return None
    bands = _group_bands([r for rs in ay_rects.values() for r in rs])
    text_lines = sorted(by_line)
    if len(bands) != len(text_lines):
        return None

    ink = to_ink(gray)
    band_px = [((b0 - vy) * sy, (b1 - vy) * sy) for b0, b1 in bands]
    pitch = float(np.median([b1 - b0 for b0, b1 in band_px]))
    skip = ink[int(max(band_px[0][0], 0)):max(int(band_px[-1][1]), 1), :].mean(axis=0) > 0.9

    # setengah lebar penanda: dari penanda yang batas ayatnya di tengah baris (batas kiri poligon = tepi kiri penanda)
    halves = []
    for key, (mx, _my) in markers.items():
        for r in ay_rects[key]:
            if r[0] > 1.0 and r[0] < mx < r[2]:
                halves.append(mx - r[0])
    marker_half = float(np.median(halves)) * sx if halves else 0.27 * pitch

    problems: list[str] = []
    results: list[LineResult] = []
    for idx, line_no in enumerate(text_lines):
        top, bottom = band_px[idx]
        tokens = by_line[line_no]
        segs: dict[tuple[int, int], list[Token]] = {}
        for t in tokens:
            segs.setdefault((t.surah, t.ayah), []).append(t)
        height = bottom - top
        core_pad = 0.14 * height
        cy0, cy1 = int(top + core_pad), int(bottom - core_pad)
        core = ink[max(cy0, 0):max(cy1, cy0 + 1), :].copy()
        core[:, skip] = False
        ink_cols = core.any(axis=0)
        spans: dict[int, tuple[float, float]] = {}
        fallback = 0
        ok = True
        for key, toks in segs.items():
            rect = next((r for r in ay_rects[key] if abs(r[1] - bands[idx][0]) < 2.0 and abs(r[3] - bands[idx][1]) < 2.0), None)
            if rect is None:
                ok = False
                break
            xl, xr = (rect[0] - vx) * sx, (rect[2] - vx) * sx
            words = [t for t in toks if t.kind == "w"]
            mark = next((t for t in toks if t.kind == "e"), None)
            word_right_limit, word_left_limit = xr, xl
            if mark is not None:
                mcx, mcy = (markers[key][0] - vx) * sx, (markers[key][1] - vy) * sy
                ring = _ring_near(ink, mcx, mcy, height, skip)
                m0, m1 = ring if ring else (mcx - marker_half, mcx + marker_half)
                spans[id(mark)] = (m0, m1)
                # kata ayat ini berada di sisi kanan penanda (teks dibaca kanan -> kiri)
                word_left_limit = max(xl, m1)
            if not words:
                continue
            a, b = int(max(word_left_limit, 0)), int(min(word_right_limit, w))
            cols = np.where(ink_cols[a:b])[0]
            if len(cols) == 0:
                for t in words:
                    spans[id(t)] = (word_left_limit, word_right_limit)
                fallback += max(len(words) - 1, 0)
                continue
            ixl, ixr = a + int(cols[0]), a + int(cols[-1])
            parts, fb, _dev = _dp_split(core, ixl, ixr, words, pitch)
            for t, pr in zip(words, parts):
                spans[id(t)] = pr
            fallback += fb
        if not ok:
            problems.append(f"baris {line_no}: poligon tidak cocok dengan pita")
            return None
        # kotak: rapatkan pada tinta, perluas sedikit ke tetangga
        order = sorted(tokens, key=lambda t: -(spans[id(t)][0] + spans[id(t)][1]))
        tight = {}
        for t in order:
            left, right = spans[id(t)]
            a, b = int(max(left, 0)), int(min(right, w))
            seg = np.where(ink_cols[a:b])[0]
            tight[id(t)] = (a + float(seg[0]), a + float(seg[-1]) + 1) if len(seg) else (left, right)
        boxes = []
        for pos, t in enumerate(order):
            x0, x1 = tight[id(t)]
            lg = (x0 - tight[id(order[pos + 1])][1]) / 2 if pos + 1 < len(order) else 0.0
            rg = (tight[id(order[pos - 1])][0] - x1) / 2 if pos > 0 else 0.0
            x0 -= min(max(lg, 0.0), 0.10 * pitch)
            x1 += min(max(rg, 0.0), 0.10 * pitch)
            if t.kind == "e" and (x1 - x0) < 0.8 * pitch:
                c = (x0 + x1) / 2
                x0, x1 = c - 0.4 * pitch, c + 0.4 * pitch
            boxes.append(Box(t, x0, top + 0.06 * height, x1, bottom - 0.06 * height, fallback=bool(fallback)))
        results.append(LineResult(line_no, boxes, fallback, 0.0))
    return PageResult(w, h, results, Bands([b[0] for b in band_px], [b[1] for b in band_px], pitch, "polygons"), len(text_lines), problems)


def draw_debug(gray: np.ndarray, result: PageResult) -> Image.Image:
    """Gambar uji: kotak kata hijau (merah bila fallback), penanda ayat biru, garis pita kuning."""
    from PIL import ImageDraw

    im = Image.fromarray(gray).convert("RGB")
    d = ImageDraw.Draw(im, "RGBA")
    for y in result.bands.tops + [result.bands.bottoms[-1]]:
        d.line([(0, y), (im.width, y)], fill=(255, 200, 0, 160), width=1)
    for ln in result.lines:
        for b in ln.boxes:
            if b.token.kind == "e":
                col = (0, 90, 255, 70)
            elif b.fallback:
                col = (255, 0, 0, 70)
            else:
                col = (0, 170, 60, 60)
            d.rectangle([b.x0, b.y0, b.x1, b.y1], fill=col, outline=col[:3] + (200,))
    return im
