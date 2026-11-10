# Mutqin — Android native (Kotlin + Jetpack Compose)

Port native dari web app **Mutqin** (murajaah Al-Qur'an per halaman dengan flashcard,
Leitner 7 kotak, dan active recall metode Dr. Ali Rajabi).

## Cara membangun APK lewat GitHub Actions
1. Buat repo GitHub, unggah seluruh isi folder ini (termasuk `.github/workflows/` dan `app/mutqin-debug.keystore`).
2. **Sekali saja:** tab **Actions** -> **Generate Assets** -> *Run workflow*. Ia mengunduh teks 604 halaman dan audio
   3,5 detik tiap halaman, memvalidasinya (604 halaman, 6.236 ayat, 604 klip), lalu menyimpannya ke repo.
   Jika gagal saat *push*, buka Settings -> Actions -> General -> Workflow permissions -> **Read and write**.
3. Tab **Actions** -> **Build Android APK** -> *Run workflow* (commit dari langkah 2 tidak memicunya otomatis).
4. Unduh `mutqin-debug-apk` dari bagian **Artifacts**.

APK sudah memuat seluruh teks dan audio, jadi aplikasi berjalan sepenuhnya offline (tanpa izin internet).
Build akan GAGAL dengan pesan jelas bila aset belum lengkap.

### Memperbarui aplikasi tanpa kehilangan data
Semua build ditandatangani keystore tetap (`app/mutqin-debug.keystore`), sehingga APK baru bisa dipasang **di atas** versi lama
tanpa uninstall. Khusus peralihan pertama dari APK lama (keystore berubah-ubah): ekspor cadangan JSON dulu dari aplikasi lama
(ikon sinkronisasi), pasang APK baru, lalu impor cadangan itu.

### Halaman bergambar + kotak kata
Halaman Qur'an ditampilkan sebagai gambar mushaf cetak. Jalankan sekali **Generate Page Images** (setelah Generate Assets): ia mengunduh
gambar per halaman (isi pola URL sumber di input workflow), mendeteksi posisi tiap kata dan nomor ayat, lalu menyimpan
`assets/pages_img/` dan `assets/page_boxes/`. Kotak tidak terlihat sampai disentuh. Tanpa aset ini aplikasi memakai tampilan teks lama.
Sumber yang disarankan: quran-ws/quran-svg (SVG + poligon ayat; isi `svg_base` di workflow). Periksa hasil dengan `python tools/generate_page_images.py --pages N --debug-dir DIR`. Perhatikan lisensi sumber gambar.

### Cadangan JSON (tanpa login)
- Semua data lokal. Menu sinkronisasi (ikon kanan atas): ekspor/impor JSON. Setelah impor, kartu langsung mengikuti antrean di file.
- Cadangan otomatis ke satu file `Download/Mutqin/mutqin-cadangan-TANGGAL-JAM.json` (waktu file itu pertama dibuat, namanya tidak
  berubah saat diperbarui) setiap data berubah dan saat aplikasi ditinggalkan. File baru dibuat setelah impor, reset data, atau
  "mulai dari awal" di dialog pertama; file lama tidak dihapus. File yang diimpor boleh di folder mana pun dan tidak diubah.
  Android tidak bisa menjalankan kode sebelum uninstall, jadi file dijaga selalu terbaru. Setelah pasang ulang, impor file itu.

### Pengingat dan tema
- Pengingat memakai alarm tepat waktu (jika diizinkan) ditambah pemeriksa cadangan WorkManager (~15 menit) yang menyusulkan
  notifikasi terlewat; tiap jam hanya ditampilkan sekali. Alarm dijadwalkan ulang saat reboot, pembaruan aplikasi, dan perubahan jam/zona waktu.
- Tema: ikon palet di kanan atas -> Mushaf (krem terang), Zamrud, atau Gelap.

### Dua APK: debug dan rilis
Build menghasilkan dua artifact: `mutqin-debug-apk` (untuk uji/diagnosis) dan `mutqin-release-apk`
(dioptimalkan R8 dan profil baseline, jauh lebih mulus saat scroll). Pakai yang rilis untuk sehari-hari.
Keduanya memakai ID aplikasi dan keystore tetap yang sama, sehingga saling menimpa tanpa uninstall.

### APK rilis bertanda tangan (kunci pribadi)
1. Buat keystore pribadi (mis. di GitHub Codespaces):
   `keytool -genkeypair -v -keystore mutqin-release.jks -alias mutqin -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12`
2. Simpan 4 GitHub Secrets: `MUTQIN_KEYSTORE_BASE64` (`base64 -w0 mutqin-release.jks`), `MUTQIN_KEYSTORE_PASSWORD`,
   `MUTQIN_KEY_ALIAS` (`mutqin`), `MUTQIN_KEY_PASSWORD` (sama dengan password keystore).
3. Jalankan **Build Android APK**. Halaman Summary menampilkan SHA-1/SHA-256 kunci.
4. Pasang `mutqin-release-apk` (uninstall APK bertanda tangan lama dulu; simpan cadangan JSON dulu).
**Cadangkan file `.jks` dan password di dua tempat aman** — tanpa keduanya aplikasi tidak bisa diperbarui di atas versi lama.

## Build lokal
Butuh JDK 17 + Android SDK 35 + Gradle 8.9: `gradle :app:assembleDebug`
(atau buka folder ini di Android Studio).

## Struktur
- `app/src/main/java/id/mutqin/app/MainActivity.kt` — Activity, scaffold, navigasi, dialog
- `.../ui/` — layar Flashcard, Daftar 604 halaman, Statistik (grafik Canvas), dialog target/pengingat/cadangan
- `.../Srs.kt` — Leitner, penilaian, antrean "bayar utang dulu", statistik (logika murni, teruji)
- `.../Codec.kt` — JSON kompatibel dengan file cadangan versi web
- `app/src/main/assets/` — daftar surat, trigger & ayat pembuka 604 halaman; `pages/` teks 604 halaman dan `audio/` klip 3,5 detik (dibuat `tools/generate_assets.py`)
- `app/src/test/` — unit test (antrean, perubahan rentang, SRS, pemetaan ayat->halaman); dijalankan CI tiap push
- Font Arab: Scheherazade New & Amiri (SIL OFL), disertakan di `res/font`
