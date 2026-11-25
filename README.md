# Rongsokin – Smart Scrap Management System

## Langkah (semua lewat browser, tanpa Android Studio)
1. **Firebase**: buka console.firebase.google.com → Add project.
   - Add app Android: package `com.irfan.rongsokin`, SHA-1 `03:74:18:72:AB:43:4D:D3:EA:C1:F1:22:A5:43:E7:C1:ED:F2:0C:D0`
   - Download `google-services.json` → letakkan di folder `app/` (sejajar `build.gradle.kts`).
   - Authentication → Sign-in method → aktifkan **Google** dan **Email/Password**.
   - Firestore Database → Create (production mode) → tab Rules, tempel:
     ```
     rules_version = '2';
     service cloud.firestore { match /databases/{d}/documents {
       match /users/{uid}/{document=**} { allow read, write: if request.auth.uid == uid; }
     } }
     ```
   - Unduh `google-services.json` ULANG setelah Google diaktifkan (agar berisi OAuth client).
2. **GitHub**: buat repo baru → upload seluruh isi folder ini (termasuk `.github`) → tab **Actions** → tunggu "Build APK" hijau → unduh artifact **Rongsokin-APK** → install `app-release.apk`.
3. Harga pasar otomatis: isi `PRICE_URL` di MainActivity.kt dengan URL JSON `{"Kardus":2500,"Besi":4000}` (Rp/Kg).
