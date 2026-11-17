//go:build windows

package main

import (
	"fmt"
	"net/url"
	"time"
)

// Zamiana ruchów myszy i klawiatury na polecenia dla telefonu. Polecenia lecą
// stałym połączeniem (controller), a przeciąganie jest przekazywane na żywo:
// wciśnięcie = palec na ekranie, ruch = przesuwanie, puszczenie = palec w górę.

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
	return rx, ry, rx >= 0 && rx <= 1 && ry >= 0 && ry <= 1
}

func clamp01(f float64) float64 {
	if f < 0 {
		return 0
	}
	if f > 1 {
		return 1
	}
	return f
}

func (v *window) onPress(mx, my int) {
	rx, ry, ok := v.toPhone(mx, my)
	if !ok {
		return
	}
	v.mu.Lock()
	v.dragging = true
	v.lastMove = time.Now()
	v.mu.Unlock()
	pSetCapture.Call(v.hwnd)
	v.sendControl(fmt.Sprintf("a=down&x=%.4f&y=%.4f", rx, ry))
}

func (v *window) onMove(mx, my int) {
	v.mu.Lock()
	if !v.dragging || time.Since(v.lastMove) < 12*time.Millisecond {
		v.mu.Unlock()
		return
	}
	v.lastMove = time.Now()
	v.mu.Unlock()
	rx, ry, _ := v.toPhone(mx, my)
	v.sendControl(fmt.Sprintf("a=move&x=%.4f&y=%.4f", clamp01(rx), clamp01(ry)))
}

func (v *window) onRelease(mx, my int) {
	v.mu.Lock()
	if !v.dragging {
		v.mu.Unlock()
		return
	}
	v.dragging = false
	v.mu.Unlock()
	pReleaseCapture.Call()
	rx, ry, _ := v.toPhone(mx, my)
	v.sendControl(fmt.Sprintf("a=up&x=%.4f&y=%.4f", clamp01(rx), clamp01(ry)))
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

func (v *window) sendControl(cmd string) {
	if v.ctrl != nil {
		v.ctrl.send(cmd)
	}
}

// onControlAccess pokazuje podpowiedź, gdy na telefonie nie włączono sterowania.
func (v *window) onControlAccess(ok bool) {
	v.mu.Lock()
	first := !ok && !v.warned
	if !ok {
		v.warned = true
	}
	v.mu.Unlock()
	if first {
		v.setStatus("Włącz sterowanie na telefonie: Ustawienia → Ułatwienia dostępu → " +
			"„Ekran na PC - sterowanie”.")
	}
}
