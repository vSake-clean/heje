# naplet – Ekran telefonu na komputerze

Dwa gotowe pliki w katalogu [`dist/`](dist):

| Plik | Gdzie | Co robi |
|------|-------|---------|
| `EkranTelefonu.apk` | telefon z Androidem (5.0+) | przechwytuje ekran i wysyła go po Wi‑Fi |
| `EkranTelefonu.exe` | komputer z Windows (64‑bit) | sam znajduje telefon w sieci i wyświetla jego ekran w oknie |

## Jak używać

1. Telefon i komputer muszą być w **tej samej sieci Wi‑Fi** – albo komputer jest podłączony do **hotspotu tego telefonu**
   (Wi‑Fi lub USB). Hotspot działa nawet bez internetu / danych komórkowych.
2. Na telefonie zainstaluj `EkranTelefonu.apk` (zezwól na instalację z nieznanych źródeł), otwórz **Ekran na PC**,
   naciśnij **Start** i zezwól na nagrywanie ekranu.
3. Na komputerze uruchom `EkranTelefonu.exe`. Gdy Windows zapyta o zaporę – kliknij **Zezwól**
   (przy hotspocie zaznacz też sieci publiczne; ale nawet bez tej zgody program znajdzie telefon).
4. Po chwili ekran telefonu pojawi się w oknie. Okno można dowolnie powiększać.

Jeśli automatyczne wykrywanie nie zadziała, podaj adres IP widoczny w aplikacji:

```
EkranTelefonu.exe 192.168.1.23
```

Podgląd działa też w przeglądarce: `http://ADRES_TELEFONU:8080`.

Zatrzymanie: przycisk **Zatrzymaj** w aplikacji lub w powiadomieniu.

## Jak to działa

- Telefon (`android/`, Java, bez bibliotek): `MediaProjection` → `ImageReader` → JPEG (max 1280 px, do ~20 kl./s)
  → strumień MJPEG po HTTP na porcie **8080**. Co sekundę rozgłasza pakiet UDP na port **8089**;
  pod `/info` odpowiada swoją nazwą.
- Komputer (`pc/`, Go, czyste Win32 API): nasłuchuje pakietu UDP i jednocześnie sprawdza adresy w swojej podsieci
  (połączenia wychodzące – zapora ich nie blokuje, więc działa też na hotspocie telefonu), łączy się ze strumieniem, dekoduje JPEG
  i rysuje klatki w oknie (z zachowaniem proporcji). Po rozłączeniu ponawia połączenie.

## Budowanie ze źródeł

```
android/build.sh   # wymaga JDK i Android SDK (platforms;android-34, build-tools;35.0.0)
pc/build.sh        # wymaga Go 1.21+ (kompilacja skrośna – działa też z Linuksa)
```
