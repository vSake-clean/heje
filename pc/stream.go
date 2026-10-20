package main

import (
	"context"
	"errors"
	"fmt"
	"image"
	"image/draw"
	"image/jpeg"
	"io"
	"mime"
	"mime/multipart"
	"net"
	"net/http"
	"strings"
	"time"
)

const (
	discoveryPort = 8089
	defaultPort   = "8080"
	magic         = "EKRANTELEFONU"
)

// viewer to okno (lub konsola), które pokazuje status i kolejne klatki.
type viewer interface {
	setStatus(s string)
	// setFrame dostaje piksele w formacie BGRA (32 bity, wiersze od góry).
	setFrame(bgra []byte, w, h int)
}

// discover czeka na pakiet rozgłoszeniowy wysyłany przez aplikację na telefonie.
// Zwraca adres "ip:port" serwera strumienia oraz nazwę telefonu.
func discover() (addr, name string, err error) {
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{Port: discoveryPort})
	if err != nil {
		return "", "", fmt.Errorf("nie mogę nasłuchiwać na porcie UDP %d: %w", discoveryPort, err)
	}
	defer conn.Close()
	buf := make([]byte, 1500)
	for {
		n, from, err := conn.ReadFromUDP(buf)
		if err != nil {
			return "", "", err
		}
		fields := strings.SplitN(string(buf[:n]), " ", 3)
		if len(fields) < 2 || fields[0] != magic {
			continue
		}
		if len(fields) == 3 {
			name = fields[2]
		}
		return net.JoinHostPort(from.IP.String(), fields[1]), name, nil
	}
}

var client = &http.Client{
	Transport: &http.Transport{
		DialContext:           (&net.Dialer{Timeout: 5 * time.Second, KeepAlive: 10 * time.Second}).DialContext,
		ResponseHeaderTimeout: 5 * time.Second,
		DisableCompression:    true,
	},
}

// stream odbiera strumień MJPEG z telefonu i przekazuje klatki do v, aż do błędu.
func stream(ctx context.Context, addr string, v viewer) error {
	req, err := http.NewRequestWithContext(ctx, "GET", "http://"+addr+"/stream", nil)
	if err != nil {
		return err
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("telefon odpowiedział: %s", resp.Status)
	}
	_, params, err := mime.ParseMediaType(resp.Header.Get("Content-Type"))
	if err != nil || params["boundary"] == "" {
		return errors.New("nieprawidłowa odpowiedź telefonu")
	}
	mr := multipart.NewReader(resp.Body, params["boundary"])
	var rgba *image.RGBA
	for {
		part, err := mr.NextPart()
		if err != nil {
			if errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
				return errors.New("telefon zakończył udostępnianie")
			}
			return err
		}
		img, err := jpeg.Decode(part)
		part.Close()
		if err != nil {
			continue
		}
		b := img.Bounds()
		if rgba == nil || rgba.Bounds().Dx() != b.Dx() || rgba.Bounds().Dy() != b.Dy() {
			rgba = image.NewRGBA(image.Rect(0, 0, b.Dx(), b.Dy()))
		}
		draw.Draw(rgba, rgba.Bounds(), img, b.Min, draw.Src)
		out := make([]byte, len(rgba.Pix))
		for i := 0; i+3 < len(out); i += 4 {
			out[i], out[i+1], out[i+2], out[i+3] = rgba.Pix[i+2], rgba.Pix[i+1], rgba.Pix[i], 255
		}
		v.setFrame(out, b.Dx(), b.Dy())
	}
}

// run to główna pętla: znajdź telefon (lub użyj podanego adresu), oglądaj, po rozłączeniu ponów.
func run(manual string, v viewer) {
	for {
		addr, name := manual, ""
		if addr == "" {
			v.setStatus("Szukam telefonu w sieci... Uruchom aplikację na telefonie i naciśnij Start.")
			var err error
			addr, name, err = discover()
			if err != nil {
				v.setStatus("Błąd: " + err.Error())
				time.Sleep(3 * time.Second)
				continue
			}
		} else if _, _, err := net.SplitHostPort(addr); err != nil {
			addr = net.JoinHostPort(addr, defaultPort)
		}
		if name == "" {
			name = addr
		}
		v.setStatus("Łączenie z " + name + " (" + addr + ")...")
		err := stream(context.Background(), addr, &titled{viewer: v, name: name})
		v.setStatus("Rozłączono (" + err.Error() + "). Ponawiam...")
		time.Sleep(2 * time.Second)
	}
}

// titled ustawia status "Połączono" przy pierwszej klatce.
type titled struct {
	viewer
	name string
	seen bool
}

func (t *titled) setFrame(bgra []byte, w, h int) {
	if !t.seen {
		t.seen = true
		t.viewer.setStatus("Połączono: " + t.name)
	}
	t.viewer.setFrame(bgra, w, h)
}
