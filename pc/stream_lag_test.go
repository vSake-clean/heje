package main

import (
	"bytes"
	"context"
	"fmt"
	"image"
	"image/color"
	"image/jpeg"
	"net"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

type slowViewer struct {
	mu     sync.Mutex
	frames int
	last   color.RGBA
}

func (s *slowViewer) setStatus(string)        {}
func (s *slowViewer) setControlTarget(string) {}
func (s *slowViewer) setFrame(bgra []byte, w, h int) {
	time.Sleep(20 * time.Millisecond) // komputer nie nadąża
	s.mu.Lock()
	s.frames++
	s.last = color.RGBA{bgra[2], bgra[1], bgra[0], bgra[3]}
	s.mu.Unlock()
}

func jpegOf(c color.RGBA) []byte {
	img := image.NewRGBA(image.Rect(0, 0, 32, 32))
	for i := 0; i < len(img.Pix); i += 4 {
		img.Pix[i], img.Pix[i+1], img.Pix[i+2], img.Pix[i+3] = c.R, c.G, c.B, 255
	}
	var b bytes.Buffer
	jpeg.Encode(&b, img, &jpeg.Options{Quality: 95})
	return b.Bytes()
}

// Telefon wysyła 100 klatek naraz; wolny odbiorca ma dostać ostatnią, pomijając stare.
func TestStreamSkipsStaleFrames(t *testing.T) {
	const n = 100
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "multipart/x-mixed-replace; boundary=frame")
		for i := 0; i < n; i++ {
			c := color.RGBA{0, 0, 255, 255} // niebieska
			if i == n-1 {
				c = color.RGBA{255, 0, 0, 255} // ostatnia czerwona
			}
			data := jpegOf(c)
			fmt.Fprintf(w, "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %d\r\n\r\n", len(data))
			w.Write(data)
			w.Write([]byte("\r\n"))
		}
		w.(http.Flusher).Flush()
	}))
	defer srv.Close()

	v := &slowViewer{}
	addr := srv.Listener.Addr().(*net.TCPAddr).String()
	start := time.Now()
	stream(context.Background(), addr, v)
	took := time.Since(start)

	v.mu.Lock()
	defer v.mu.Unlock()
	if v.frames >= n/2 {
		t.Fatalf("pokazano %d z %d klatek – stare klatki nie są pomijane", v.frames, n)
	}
	if v.last.R < 200 || v.last.B > 60 {
		t.Fatalf("ostatnia pokazana klatka to %v, a powinna być czerwona (najnowsza)", v.last)
	}
	if took > time.Second {
		t.Fatalf("odbiór trwał %v – obraz zostaje w tyle", took)
	}
}

// Ekran telefonu stoi w miejscu: przychodzi jedna klatka i dalej cisza.
// Klatka musi się pojawić od razu, a nie dopiero po kolejnej.
func TestStreamShowsFrameWithoutWaitingForNext(t *testing.T) {
	release := make(chan struct{})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "multipart/x-mixed-replace; boundary=frame")
		data := jpegOf(color.RGBA{255, 0, 0, 255})
		fmt.Fprintf(w, "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %d\r\n\r\n", len(data))
		w.Write(data)
		w.Write([]byte("\r\n"))
		w.(http.Flusher).Flush()
		<-release // połączenie otwarte, brak kolejnych klatek
	}))
	defer srv.Close()
	defer close(release)

	got := make(chan struct{}, 1)
	v := &notifyViewer{got: got}
	go stream(context.Background(), srv.Listener.Addr().String(), v)
	select {
	case <-got:
	case <-time.After(time.Second):
		t.Fatal("klatka nie pojawiła się, choć została w całości odebrana")
	}
}

type notifyViewer struct{ got chan struct{} }

func (n *notifyViewer) setStatus(string)        {}
func (n *notifyViewer) setControlTarget(string) {}
func (n *notifyViewer) setFrame([]byte, int, int) {
	select {
	case n.got <- struct{}{}:
	default:
	}
}
