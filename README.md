# APK Decompiler

Aplikasi Android untuk membongkar APK dan membaca kodenya sebagai Java **langsung di HP**, tanpa PC dan tanpa Android Studio. Mesin decompile-nya memakai [`jadx-core`](https://github.com/skylot/jadx), library yang sama dengan JADX versi desktop. UI-nya dibuat dengan Jetpack Compose.

> **Status:** versi awal / eksperimental. Menjalankan `jadx-core` di Android tidak umum dicoba, jadi bisa ada bug atau crash di APK tertentu. Lihat bagian [Batasan](#batasan) dan [Troubleshooting](#troubleshooting).

---

## Fitur

| Fitur | Keterangan |
|---|---|
| **Pilih file APK** | Buka APK dari penyimpanan HP lewat file picker bawaan Android. |
| **Pilih dari app terpasang** | Daftar semua aplikasi di HP (dengan kolom cari), tinggal tap untuk membongkarnya. |
| **Daftar class** | Menampilkan seluruh class di dalam APK, diurutkan sesuai nama lengkap (`com.contoh.app.MainActivity`). |
| **Cari class** | Filter daftar class secara langsung (tidak peduli huruf besar/kecil), lengkap dengan jumlah hasil. |
| **Decompile ke Java** | Tap sebuah class untuk melihat hasil decompile-nya. Prosesnya per class saat dibuka, jadi hemat memori. |
| **Syntax highlight** | Keyword, string, angka, anotasi, komentar, dan nama tipe diberi warna. Tampilan gelap dengan font monospace. |
| **Nomor baris + scroll horizontal** | Baris panjang tidak dipotong, cukup geser ke samping. |
| **Lihat AndroidManifest.xml** | Manifest sudah di-decode dari format biner jadi XML yang bisa dibaca. |
| **Salin kode** | Salin seluruh isi class (atau manifest) ke clipboard. |

---

## Persyaratan

- Android **8.0 (API 26)** atau lebih baru.
- RAM besar sangat membantu. Aplikasi sudah memakai `largeHeap`, tapi APK besar tetap bisa kehabisan memori.
- Tidak butuh izin internet. Semua proses berjalan lokal di HP.

---

## Cara mendapatkan APK-nya

Tanpa Android Studio, build lewat **GitHub Actions** (gratis):

1. Buat repository baru di GitHub.
2. Upload seluruh isi project ini ke repository tersebut. Kalau folder `.github` tidak ikut ter-upload, buat manual lewat **Add file → Create new file** dengan nama `.github/workflows/build.yml`.
3. Buka tab **Actions** dan tunggu workflow **Build APK** selesai (centang hijau, sekitar 3–6 menit). Kalau tidak jalan sendiri, pilih workflow-nya lalu **Run workflow**.
4. Buka run yang sukses, scroll ke **Artifacts**, lalu download **ApkDecompiler-APK**. Hasilnya zip berisi `app-debug.apk`.

Kalau punya PC dengan JDK 17 dan Gradle, bisa juga build manual:

```bash
gradle assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
```

## Cara install (contoh: Samsung One UI)

1. Pindahkan `app-debug.apk` ke HP lalu buka lewat aplikasi Files.
2. Kalau diblokir, matikan **Auto Blocker**: Settings → Security and privacy → Auto Blocker.
3. Izinkan **Install unknown apps** untuk aplikasi yang dipakai membuka file.
4. Kalau muncul peringatan Play Protect, pilih **Install anyway**. APK ini ditandatangani dengan debug key, jadi peringatan itu wajar.

---

## Cara pakai

### 1. Membuka APK

Di layar awal ada dua cara:

**a. Pilih file APK**
1. Tap **Pilih file APK**.
2. Cari dan pilih file `.apk` di penyimpanan HP.
3. Tunggu proses loading. APK besar bisa memakan waktu lama.

**b. Pilih dari app terpasang**
1. Tap **Pilih dari app terpasang**.
2. Ketik nama aplikasi atau package name di kolom **Cari**.
3. Tap aplikasi yang ingin dibongkar.

> Untuk app yang dipasang sebagai *split APK*, hanya `base.apk` yang dibaca. Kode utama biasanya ada di sana, tapi tidak selalu lengkap.

### 2. Menelusuri class

Setelah loading selesai, muncul daftar class.

- Ketik di kolom **Cari class** untuk menyaring, misalnya `MainActivity`, `login`, atau nama package `com.contoh`. Angka `(hasil/total)` di label menunjukkan jumlah hasil.
- Scroll untuk menelusuri daftar secara manual.
- Hanya class tingkat atas yang tampil. Inner class dan anonymous class sudah ikut di dalam kode class induknya.

### 3. Membaca hasil decompile

1. Tap sebuah class di daftar.
2. Tunggu indikator loading hilang. Class besar butuh waktu lebih lama.
3. Kode Java muncul dengan syntax highlight dan nomor baris.
4. Geser ke samping untuk baris yang panjang.
5. Tap **Salin** untuk menyalin seluruh kode ke clipboard.
6. Tap **← Kembali** (atau tombol back HP) untuk kembali ke daftar class.

Kalau decompile gagal untuk satu class, hasilnya berupa komentar `// Gagal decompile class ini:` beserta pesan error. Class lain tetap bisa dibuka.

### 4. Melihat AndroidManifest.xml

1. Di layar daftar class, tap **Manifest** di bagian atas.
2. Manifest tampil sebagai teks XML biasa: permission, activity, service, receiver, dan komponen lain.
3. Tap **Salin** atau **← Kembali** seperti biasa.

### 5. Menutup APK

Tap **Tutup** di layar daftar class (atau tekan tombol back dari daftar class) untuk kembali ke layar awal. Memori yang dipakai decompiler dilepas, lalu kamu bisa membuka APK lain.

### Tombol back

| Posisi | Efek tombol back |
|---|---|
| Layar kode | Kembali ke daftar class |
| Daftar class | Menutup APK, kembali ke layar awal |
| Layar awal | Keluar dari aplikasi |

---

## Batasan

- **Hanya kode DEX (Java/Kotlin) yang bisa dibaca.** Kode native (`.so`, C/C++) tidak ikut. Game Unity atau Unreal biasanya hampir seluruh logikanya ada di library native, jadi hasilnya hanya kerangka Java-nya.
- **Hasil decompile tidak selalu sempurna.** Kode bisa berisi bagian yang tidak bisa direkonstruksi, ditandai komentar dari jadx.
- **Nama yang di-obfuscate tidak dipulihkan.** App yang memakai R8/ProGuard akan menampilkan nama seperti `a.b.c`.
- **Resource selain manifest** (layout, gambar, string) belum bisa dilihat.
- **Belum ada fitur ekspor/simpan** hasil decompile ke file. Gunakan tombol **Salin** untuk sementara.
- **Memori terbatas.** APK sangat besar bisa gagal dimuat dengan pesan "Memori habis".
- **Hanya APK**, bukan `.xapk`, `.apks`, atau `.aab`.

---

## Troubleshooting

| Masalah | Penyebab / solusi |
|---|---|
| "Memori habis. APK terlalu besar…" | Coba APK yang lebih kecil, tutup aplikasi lain di background, lalu ulangi. |
| Layar error dengan nama exception | Catat pesannya dan laporkan lewat issue di repository ini. Bisa jadi API jadx tidak cocok dengan Android untuk APK tersebut. |
| Aplikasi crash saat memuat APK | Sama seperti di atas. Sertakan nama/ukuran APK dan versi Android. |
| Daftar app terpasang kosong atau kurang | Tunggu sebentar sampai "Memuat…" hilang. Beberapa ROM membatasi visibilitas daftar aplikasi. |
| Hasil decompile berupa `// Gagal decompile class ini` | Class itu bermasalah untuk jadx. Coba class lain. |
| Manifest gagal di-decode | Muncul komentar `Gagal decompile manifest` di layar. Biasanya karena resource APK rusak atau sangat tidak standar. |
| APK tidak bisa dipasang | Matikan Auto Blocker (Samsung) dan izinkan install dari sumber tidak dikenal. |
| Build GitHub Actions merah | Buka step yang gagal dan lihat baris yang diawali `e:` atau blok **What went wrong**, lalu laporkan. |

---

## Struktur project

```
.
├── .github/workflows/build.yml      # build APK otomatis di GitHub
├── build.gradle.kts                 # plugin AGP, Kotlin, Compose
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts             # dependensi (jadx-core, Compose, dll.)
    └── src/main/
        ├── AndroidManifest.xml
        └── java/dev/apkdecompiler/
            ├── MainActivity.kt          # semua layar (Compose)
            ├── DecompilerViewModel.kt   # load APK, decompile per class, state
            └── Highlighter.kt           # syntax highlight Java
```

## Teknologi

- Kotlin 2.0, Jetpack Compose (Material 3)
- [jadx-core](https://github.com/skylot/jadx) 1.5.0 + `jadx-dex-input` (Apache License 2.0)
- minSdk 26, targetSdk 35
- Core library desugaring supaya jadx (yang dibuat untuk JVM desktop) bisa jalan di Android

---

## Penggunaan yang wajar

Aplikasi ini ditujukan untuk belajar struktur dan cara kerja aplikasi Android, membedah aplikasi buatan sendiri, dan analisis keamanan pada aplikasi yang kamu punya izinnya. Hormati lisensi dan hak cipta aplikasi yang dibongkar serta hukum yang berlaku di tempatmu. Jangan dipakai untuk membajak aplikasi atau menembus perlindungan layanan milik pihak lain.

## Lisensi dan kredit

- Decompiler: [JADX](https://github.com/skylot/jadx) oleh skylot, lisensi Apache 2.0.
- Tambahkan lisensi untuk kode aplikasi ini sesuai pilihanmu (misalnya MIT atau Apache 2.0) sebelum dipublikasikan.
