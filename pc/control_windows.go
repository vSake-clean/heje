//go:build windows

package main

import (
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"time"
)

// Wysyłanie poleceń sterujących do telefonu oraz zamiana ruchów myszy i
// klawiatury na gesty. Wszystko leci przez zwykłe zapytania HTTP GET do
// /control na telefonie, więc nie potrzeba żadnych bibliotek.

var ctrlClient = &http.Client{Timeout: 2 * time.Second}

// mouseX / mouseY wyłuskują współrzędne kursora z parametru lParam komunikatu.
func mouseX(lParam uintptr) int { return int(int16(lParam & 0xffff)) }
func mouseY(lParam uintptr) int { return int(int16((lParam >> 16) & 0xffff)) }

// wheelDelta zwraca obrót kółka (dodatni = do przodu / w górę).
func wheelDelta(wParam uintptr) int { return int(int16((wParam >> 16) & 0xffff)) }

// toPhone przelicza piksel okna na współrzędne telefonu 0..1. ok=false, gdy
// kliknięcie trafiło poza obraz (na czarny pasek obok).
func (v *window) toPhone(mx, my int) (rx, ry float64, ok bool) {
	v.mu.Lock()
	dx, dy, dw, dh := v.imgDX, v.imgDY, v.imgDW, v.imgDH
	v.mu.Unlock()
	if dw <= 0 || dh <= 0 {
		return 0, 0, false
	}
	rx = float64(mx-dx) / float64(dw)
	ry = float64(my-dy) / float64(dh)
	if rx < 0 || rx > 1 || ry < 0 || ry > 1 {
		return 0, 0, false
	}
	return rx, ry, true
}

func (v *window) onPress(mx, my int) {
	rx, ry, ok := v.toPhone(mx, my)
	if !ok {
		return
	}
	v.mu.Lock()
	v.pressX, v.pressY, v.pressTime, v.dragging = rx, ry, time.Now(), true
	v.mu.Unlock()
	pSetCapture.Call(v.hwnd)
}

func (v *window) onMove(mx, my int) {
	// Gest wysyłamy dopiero przy puszczeniu przycisku; ruch nie wymaga akcji.
}

func (v *window) onRelease(mx, my int) {
	v.mu.Lock()
	if !v.dragging {
		v.mu.Unlock()
		return
	}
	v.dragging = false
	x1, y1, t0 := v.pressX, v.pressY, v.pressTime
	v.mu.Unlock()
	pReleaseCapture.Call()

	x2, y2, ok := v.toPhone(mx, my)
	if !ok {
		x2, y2 = x1, y1
	}
	dt := time.Since(t0)
	dist := math.Hypot(x2-x1, y2-y1)

	switch {
	case dist < 0.02 && dt >= 450*time.Millisecond:
		v.sendControl(fmt.Sprintf("a=long&x=%.4f&y=%.4f", x1, y1))
	case dist < 0.02:
		v.sendControl(fmt.Sprintf("a=tap&x=%.4f&y=%.4f", x1, y1))
	default:
		ms := dt.Milliseconds()
		if ms < 50 {
			ms = 50
		} else if ms > 800 {
			ms = 800
		}
		v.sendControl(fmt.Sprintf("a=swipe&x=%.4f&y=%.4f&x2=%.4f&y2=%.4f&t=%d", x1, y1, x2, y2, ms))
	}
}

// onWheel zamienia obrót kółka na przewinięcie (szybki przesuw palca w pionie).
func (v *window) onWheel(mx, my, delta int) {
	rx, _, ok := v.toPhone(mx, my)
	if !ok {
		rx = 0.5
	}
	// Kółko w górę (delta>0) = przewijanie do góry = palec jedzie w dół.
	y1, y2 := 0.35, 0.65
	if delta < 0 {
		y1, y2 = 0.65, 0.35
	}
	v.sendControl(fmt.Sprintf("a=swipe&x=%.4f&y=%.4f&x2=%.4f&y2=%.4f&t=120", rx, y1, rx, y2))
}

// onChar przekazuje wpisany znak do pola tekstowego na telefonie.
func (v *window) onChar(c uint16) {
	switch {
	case c == 8 || c == 27:
		// Backspace i Esc obsługuje WM_KEYDOWN.
		return
	case c == '\r' || c == '\n':
		v.sendControl("a=text&s=" + url.QueryEscape("\n"))
	case c >= 32:
		v.sendControl("a=text&s=" + url.QueryEscape(string(rune(c))))
	}
}

func (v *window) sendKey(k string) {
	v.sendControl("a=key&k=" + k)
}

// sendControl wysyła polecenie do telefonu w tle, żeby nie blokować okna.
func (v *window) sendControl(query string) {
	v.mu.Lock()
	addr := v.ctrlAddr
	v.mu.Unlock()
	if addr == "" {
		return
	}
	go func() {
		resp, err := ctrlClient.Get("http://" + addr + "/control?" + query)
		if err != nil {
			return
		}
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 64))
		resp.Body.Close()
		if string(body) == "NOACCESS" {
			v.mu.Lock()
			first := !v.warned
			v.warned = true
			v.mu.Unlock()
			if first {
				v.setStatus("Włącz sterowanie na telefonie: Ustawienia → Ułatwienia dostępu → " +
					"„Ekran na PC - sterowanie”.")
			}
		}
	}()
}
