# Panduan rilis versi baru (update)

## Prinsip
- Pembaruan = APK baru yang **ditandatangani kunci pribadi yang sama** dan punya `versionCode` lebih besar.
  `versionCode` naik otomatis di GitHub Actions (nomor run), jadi tidak perlu diatur manual.
- Pengguna memasang APK baru **di atas** versi lama: data (progres, pengaturan) tetap, .
- APK yang dibagikan lewat file/GitHub **tidak memperbarui diri sendiri**. Pengguna harus memasang versi baru secara manual.

## Langkah merilis
1. Pastikan perubahan sudah di cabang `main` dan build hijau (tes logika lolos).
2. Beri tag versi, mis. `v1.1.0` (GitHub -> Releases -> Draft a new release -> Choose a tag -> ketik `v1.1.0` -> Create new tag -> Publish).
   Tag memicu build; `versionName` diambil dari tag.
3. Workflow membangun APK bertanda tangan dan melampirkannya ke halaman Release (`mutqin-v1.1.0.apk`) beserta catatan perubahan otomatis.
4. Bagikan tautan Release ke pengguna. Unduh -> pasang -> selesai.

## Apakah pengguna wajib update?
- Tidak ada pemaksaan otomatis. Versi lama tetap berjalan selama format data cloud kompatibel.
- Aturan kompatibilitas data: field baru selalu OPSIONAL (contoh: `wordIndex` pada label). Jangan mengganti nama/menghapus field lama.
- Jika suatu saat ada perubahan yang TIDAK kompatibel (format data cloud berubah), perlu mekanisme "update wajib"
  (cek `minVersionCode` dari server saat aplikasi dibuka). Belum diimplementasikan.

## Catatan penting
- Kunci tanda tangan (`.jks`) dan password JANGAN hilang: tanpa itu pembaruan di atas versi lama mustahil.
