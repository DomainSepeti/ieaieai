# Kasa Android

Offline kişisel şifre/not kasası için başlangıç Android projesi.

## Özellikler
- PIN ile kasa kilidi
- Android Keystore üzerinde AES-256-GCM anahtarı
- Şifreli yerel veri
- Şifre göster/gizle ve kopyala
- Arama
- Not alanı
- Biyometrik açma

## Açma
Android Studio -> Open -> bu klasörü seç.
Gradle sync tamamlandıktan sonra Run ile cihaz/emülatörde çalıştır.

Not: Bu sürüm temel çalışan MVP'dir. Üretim sürümünde PIN deneme sınırlaması, güvenli yedekleme/geri yükleme, kayıt düzenleme, otomatik kilitleme ve daha gelişmiş ana parola mimarisi eklenmelidir.
