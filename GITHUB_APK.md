# Android Studio olmadan APK oluşturma

## 1. GitHub repository oluştur
GitHub'da boş bir repository oluştur ve bu projenin dosyalarını yükle.

Önemli: `.github/workflows/build-apk.yml` dosyası da yüklenmiş olmalı.

## 2. Actions sekmesine gir
Repository -> Actions -> **Build Kasa APK** workflow'unu seç.

İlk çalıştırmada **Run workflow** butonuna basabilirsin.

Ayrıca `main` veya `master` branch'ine push yaptığında workflow otomatik çalışır.

## 3. APK'yı indir
Workflow tamamlanınca:
Actions -> ilgili çalıştırma -> Artifacts

Burada **Kasa-debug-apk** adlı artifact'i indir.

ZIP'i açınca:
`app-debug.apk`

dosyasını Android telefona gönderip kurabilirsin.

## Not
Bu workflow debug APK üretir. Play Store dağıtımı için ayrıca imzalı release APK/AAB ve güvenli signing-key yapılandırması gerekir.
