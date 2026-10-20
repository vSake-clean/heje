# naplet – Ekran telefonu na komputerze

Dwa gotowe pliki w katalogu [`dist/`](dist):

| Plik | Gdzie | Co robi |
|------|-------|---------|
| `EkranTelefonu.apk` | telefon z Androidem (5.0+) | przechwytuje ekran i wysyła go po Wi‑Fi |
| `EkranTelefonu.exe` | komputer z Windows (64‑bit) | sam znajduje telefon w sieci i wyświetla jego ekran w oknie |

## Jak używać

1. Telefon i komputer muszą być w **tej samej sieci Wi‑Fi**.
2. Na telefonie zainstaluj `EkranTelefonu.apk` (zezwól na instalację z nieznanych źródeł), otwórz **Ekran na PC**,
   naciśnij **Start** i zezwól na nagrywanie ekranu.
3. Na komputerze uruchom `EkranTelefonu.exe`. Gdy Windows zapyta o zaporę – kliknij **Zezwól** (sieci prywatne).
4. Po chwili ekran telefonu pojawi się w oknie. Okno można dowolnie powiększać.

Jeśli automatyczne wykrywanie nie zadziała (np. sieć blokuje rozgłaszanie), podaj adres IP widoczny w aplikacji:

```
EkranTelefonu.exe 192.168.1.23
```

Podgląd działa też w przeglądarce: `http://ADRES_TELEFONU:8080`.

Zatrzymanie: przycisk **Zatrzymaj** w aplikacji lub w powiadomieniu.

## Jak to działa

- Telefon (`android/`, Java, bez bibliotek): `MediaProjection` → `ImageReader` → JPEG (max 1280 px, do ~20 kl./s)
  → strumień MJPEG po HTTP na porcie **8080**. Co sekundę rozgłasza pakiet UDP na port **8089**.
- Komputer (`pc/`, Go, czyste Win32 API): nasłuchuje pakietu UDP, łączy się ze strumieniem, dekoduje JPEG
  i rysuje klatki w oknie (z zachowaniem proporcji). Po rozłączeniu ponawia połączenie.

## Budowanie ze źródeł

```
android/build.sh   # wymaga JDK i Android SDK (platforms;android-34, build-tools;35.0.0)
pc/build.sh        # wymaga Go 1.21+ (kompilacja skrośna – działa też z Linuksa)
```
