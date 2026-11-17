package main

import (
	"archive/zip"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync/atomic"
	"time"
)

// adbDownloading jest ustawione, gdy trwa pobieranie ADB (wtedy nie podpowiadamy F5).
var adbDownloading atomic.Bool

// startAdbDownload pobiera ADB w tle, o ile jeszcze go nie ma; status pokazuje postęp.
func startAdbDownload(status func(string)) {
	if findAdb() != "" || !adbDownloading.CompareAndSwap(false, true) {
		return
	}
	status("Pobieranie narzędzia ADB od Google (ok. 7 MB)...")
	go func() {
		defer adbDownloading.Store(false)
		if _, err := downloadAdb(); err != nil {
			status("Nie udało się pobrać ADB: " + err.Error())
			return
		}
		status("ADB gotowe. Podłącz telefon kablem USB (z włączonym debugowaniem USB).")
	}()
}

// Połączenie przez kabel USB (debugowanie USB). Program używa narzędzia ADB
// od Google: "adb forward" przekierowuje port komputera na port aplikacji w
// telefonie, a potem obraz i sterowanie idą przez kabel – szybciej i bez Wi-Fi.

// usbLocalPort to port na komputerze przekierowany przez kabel na port 8080 telefonu.
const usbLocalPort = "28080"

var adbName = func() string {
	if runtime.GOOS == "windows" {
		return "adb.exe"
	}
	return "adb"
}()

// toolsDir to katalog, do którego program pobiera ADB (np. %LOCALAPPDATA%\EkranTelefonu).
func toolsDir() string {
	base, err := os.UserCacheDir()
	if err != nil {
		base = os.TempDir()
	}
	return filepath.Join(base, "EkranTelefonu")
}

// findAdb szuka adb: obok programu, w pobranych narzędziach, w Android SDK i w PATH.
func findAdb() string {
	var dirs []string
	if exe, err := os.Executable(); err == nil {
		d := filepath.Dir(exe)
		dirs = append(dirs, d, filepath.Join(d, "platform-tools"))
	}
	dirs = append(dirs, filepath.Join(toolsDir(), "platform-tools"))
	for _, env := range []string{"ANDROID_HOME", "ANDROID_SDK_ROOT"} {
		if v := os.Getenv(env); v != "" {
			dirs = append(dirs, filepath.Join(v, "platform-tools"))
		}
	}
	if v := os.Getenv("LOCALAPPDATA"); v != "" {
		dirs = append(dirs, filepath.Join(v, "Android", "Sdk", "platform-tools"))
	}
	for _, d := range dirs {
		p := filepath.Join(d, adbName)
		if st, err := os.Stat(p); err == nil && !st.IsDir() {
			return p
		}
	}
	if p, err := exec.LookPath("adb"); err == nil {
		return p
	}
	return ""
}

func adbRun(adb string, args ...string) (string, error) {
	cmd := exec.Command(adb, args...)
	hideWindow(cmd)
	done := make(chan struct{})
	var out []byte
	var err error
	go func() {
		out, err = cmd.CombinedOutput()
		close(done)
	}()
	select {
	case <-done:
		return string(out), err
	case <-time.After(15 * time.Second):
		if cmd.Process != nil {
			cmd.Process.Kill()
		}
		return "", errors.New("adb nie odpowiada")
	}
}

// usbState opisuje, co widać na kablu.
type usbState int

const (
	usbNone         usbState = iota // brak telefonu na kablu
	usbReady                        // telefon gotowy
	usbUnauthorized                 // trzeba potwierdzić debugowanie na telefonie
)

// usbDevice zwraca numer pierwszego telefonu podłączonego kablem.
func usbDevice(adb string) (serial string, st usbState) {
	out, err := adbRun(adb, "devices")
	if err != nil {
		return "", usbNone
	}
	st = usbNone
	for _, line := range strings.Split(out, "\n") {
		f := strings.Fields(line)
		if len(f) < 2 || strings.HasPrefix(line, "List of") {
			continue
		}
		switch f[1] {
		case "device":
			return f[0], usbReady
		case "unauthorized":
			st = usbUnauthorized
		}
	}
	return "", st
}

// usbConnect przekierowuje port i sprawdza, czy po drugiej stronie kabla działa aplikacja.
// Zwraca adres do połączenia i nazwę telefonu.
func usbConnect(adb, serial string) (addr, name string, ok bool) {
	if _, err := adbRun(adb, "-s", serial, "forward", "tcp:"+usbLocalPort, "tcp:"+defaultPort); err != nil {
		return "", "", false
	}
	addr = "127.0.0.1:" + usbLocalPort
	resp, err := probeClient.Get("http://" + addr + "/info")
	if err != nil {
		return "", "", false
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
	_, name, ok = parseHello(string(body))
	if name == "" {
		name = serial
	}
	return addr, "USB: " + name, ok
}

// watchUSB co 2 sekundy sprawdza kabel i zgłasza telefon, gdy aplikacja odpowie.
// hint dostaje podpowiedź dla użytkownika ("" = brak podpowiedzi).
func watchUSB(done <-chan struct{}, report func(addr, name string), showHint func(string)) {
	hint := func(h string) {
		select {
		case <-done: // telefon już znaleziony inną drogą
		default:
			showHint(h)
		}
	}
	for {
		adb := findAdb()
		switch {
		case adb == "" && adbDownloading.Load():
			hint("Pobieranie narzędzia ADB od Google...")
		case adb == "":
			hint("Kabel USB: włącz w telefonie debugowanie USB i naciśnij F5, aby pobrać narzędzie ADB od Google.")
		default:
			serial, st := usbDevice(adb)
			switch st {
			case usbUnauthorized:
				hint("Kabel USB: odblokuj telefon i potwierdź „Zezwolić na debugowanie USB?”.")
			case usbReady:
				if addr, name, ok := usbConnect(adb, serial); ok {
					report(addr, name)
					return
				}
				hint("Kabel USB: telefon podłączony – uruchom w nim aplikację i naciśnij Start.")
			default:
				hint("")
			}
		}
		select {
		case <-done:
			return
		case <-time.After(2 * time.Second):
		}
	}
}

// downloadAdb pobiera oficjalne Android Platform Tools od Google i rozpakowuje
// je do toolsDir(). Zwraca ścieżkę do adb.
func downloadAdb() (string, error) {
	osName := map[string]string{"windows": "windows", "darwin": "darwin"}[runtime.GOOS]
	if osName == "" {
		osName = "linux"
	}
	u := "https://dl.google.com/android/repository/platform-tools-latest-" + osName + ".zip"
	resp, err := (&http.Client{Timeout: 5 * time.Minute}).Get(u)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("serwer odpowiedział: %s", resp.Status)
	}
	dir := toolsDir()
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", err
	}
	tmp, err := os.CreateTemp(dir, "platform-tools-*.zip")
	if err != nil {
		return "", err
	}
	defer os.Remove(tmp.Name())
	if _, err := io.Copy(tmp, resp.Body); err != nil {
		tmp.Close()
		return "", err
	}
	tmp.Close()
	if err := unzip(tmp.Name(), dir); err != nil {
		return "", err
	}
	p := filepath.Join(dir, "platform-tools", adbName)
	if _, err := os.Stat(p); err != nil {
		return "", errors.New("w pobranym archiwum nie ma adb")
	}
	return p, nil
}

// unzip rozpakowuje archiwum do katalogu, odrzucając ścieżki wychodzące poza niego.
func unzip(src, dst string) error {
	r, err := zip.OpenReader(src)
	if err != nil {
		return err
	}
	defer r.Close()
	root, _ := filepath.Abs(dst)
	for _, f := range r.File {
		target := filepath.Join(root, filepath.FromSlash(f.Name))
		if target != root && !strings.HasPrefix(target, root+string(os.PathSeparator)) {
			return fmt.Errorf("niebezpieczna ścieżka w archiwum: %s", f.Name)
		}
		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0o755); err != nil {
				return err
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		out, err := os.OpenFile(target, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, f.Mode()|0o600)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(out, rc)
		rc.Close()
		out.Close()
		if err != nil {
			return err
		}
	}
	return nil
}
