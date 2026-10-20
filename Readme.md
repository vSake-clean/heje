# naplet – Ekran telefonu na komputerze

Dwa gotowe pliki w katalogu [`dist/`](dist):

| Plik | Gdzie | Co robi |
|------|-------|---------|
| `EkranTelefonu.apk` | telefon z Androidem (7.0+) | przechwytuje ekran i wysyła go po Wi‑Fi; opcjonalnie przyjmuje sterowanie z PC |
| `EkranTelefonu.exe` | komputer z Windows (64‑bit) | sam znajduje telefon, wyświetla jego ekran i odsyła kliknięcia/klawiaturę |

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

## Sterowanie telefonem z komputera

Możesz klikać i pisać na telefonie myszką i klawiaturą komputera – tak, jakbyś dotykał ekranu.
Wymaga to jednorazowego włączenia na telefonie (Android nie pozwala sterować urządzeniem z zewnątrz bez zgody):

1. W aplikacji na telefonie naciśnij **Włącz sterowanie z komputera**.
2. Otworzą się **Ustawienia → Ułatwienia dostępu**. Wybierz **„Ekran na PC - sterowanie”** i włącz je.
   (Niektóre telefony pokażą ostrzeżenie o pełnej kontroli – to normalne dla takich aplikacji, potwierdź.)
3. Wróć do aplikacji – przy opcji sterowania pojawi się znaczek ✓.

Od tej pory w oknie na komputerze:

| Na komputerze | Na telefonie |
|---------------|--------------|
| lewy przycisk – klik | dotknięcie |
| przeciągnięcie lewym przyciskiem | przesunięcie palcem (przewijanie, odblokowanie) |
| przytrzymanie w miejscu (~0,5 s) | dłuższe przytrzymanie |
| kółko myszy | przewijanie w górę / w dół |
| prawy przycisk | Wstecz |
| środkowy przycisk / **F1** | ekran główny |
| **F2** | ostatnie aplikacje |
| **Esc** | Wstecz |
| pisanie na klawiaturze | wpisywanie tekstu w zaznaczone pole |

Sterowanie można w każdej chwili wyłączyć, odznaczając usługę w Ustawieniach → Ułatwienia dostępu.
Sam podgląd ekranu działa też bez tego.


## Jak to działa

- Telefon (`android/`, Java, bez bibliotek): `MediaProjection` → `ImageReader` → JPEG (max 1280 px, do ~20 kl./s)
  → strumień MJPEG po HTTP na porcie **8080**. Co sekundę rozgłasza pakiet UDP na port **8089**;
  pod `/info` odpowiada swoją nazwą, a pod `/control` przyjmuje polecenia sterujące, które wykonuje
  przez usługę ułatwień dostępu (`ControlService`).
- Komputer (`pc/`, Go, czyste Win32 API): nasłuchuje pakietu UDP i jednocześnie sprawdza adresy w swojej podsieci
  (połączenia wychodzące – zapora ich nie blokuje, więc działa też na hotspocie telefonu), łączy się ze strumieniem, dekoduje JPEG
  i rysuje klatki w oknie (z zachowaniem proporcji). Po rozłączeniu ponawia połączenie.

## Budowanie ze źródeł

```
android/build.sh   # wymaga JDK i Android SDK (platforms;android-34, build-tools;35.0.0)
pc/build.sh        # wymaga Go 1.21+ (kompilacja skrośna – działa też z Linuksa)
```
