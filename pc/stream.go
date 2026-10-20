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
	"sync"
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

// discover szuka telefonu dwiema metodami naraz, aż któraś zadziała:
//   - nasłuch pakietów rozgłoszeniowych UDP wysyłanych przez aplikację,
//   - aktywne sprawdzanie adresów w podsieci komputera (działa też, gdy zapora
//     Windows blokuje ruch przychodzący, np. w sieci z hotspotu telefonu).
//
// Zwraca adres "ip:port" serwera strumienia oraz nazwę telefonu.
func discover() (addr, name string) {
	found := make(chan [2]string, 1)
	done := make(chan struct{})
	defer close(done)
	report := func(a, n string) {
		select {
		case found <- [2]string{a, n}:
		default:
		}
	}

	if conn, err := net.ListenUDP("udp4", &net.UDPAddr{Port: discoveryPort}); err == nil {
		defer conn.Close()
		go listenBeacons(conn, report)
	}
	go func() {
		for {
			if a, n, ok := scan(); ok {
				report(a, n)
				return
			}
			select {
			case <-done:
				return
			case <-time.After(3 * time.Second):
			}
		}
	}()

	r := <-found
	return r[0], r[1]
}

func listenBeacons(conn *net.UDPConn, report func(addr, name string)) {
	buf := make([]byte, 1500)
	for {
		n, from, err := conn.ReadFromUDP(buf)
		if err != nil {
			return
		}
		if port, name, ok := parseHello(string(buf[:n])); ok {
			report(net.JoinHostPort(from.IP.String(), port), name)
			return
		}
	}
}

// parseHello rozpoznaje komunikat "EKRANTELEFONU <port> <nazwa telefonu>".
func parseHello(s string) (port, name string, ok bool) {
	fields := strings.SplitN(strings.TrimSpace(s), " ", 3)
	if len(fields) < 2 || fields[0] != magic {
		return "", "", false
	}
	if len(fields) == 3 {
		name = fields[2]
	}
	return fields[1], name, true
}

var probeClient = &http.Client{
	Timeout: 1500 * time.Millisecond,
	Transport: &http.Transport{
		DialContext:       (&net.Dialer{Timeout: 800 * time.Millisecond}).DialContext,
		DisableKeepAlives: true,
	},
}

// scan sprawdza port 8080 na wszystkich adresach podsieci (maks. /24) każdej
// karty sieciowej komputera. Przy hotspocie telefon jest bramą tej podsieci.
func scan() (addr, name string, ok bool) {
	var candidates []net.IP
	seen := map[string]bool{}
	addrs, _ := net.InterfaceAddrs()
	for _, a := range addrs {
		ipn, isNet := a.(*net.IPNet)
		if !isNet {
			continue
		}
		ip := ipn.IP.To4()
		if ip == nil || ip.IsLoopback() || ip.IsLinkLocalUnicast() {
			continue
		}
		ones, _ := ipn.Mask.Size()
		if ones < 24 {
			ones = 24
		}
		if ones > 30 {
			continue
		}
		mask := net.CIDRMask(ones, 32)
		base := ip.Mask(mask)
		hosts := 1<<(32-ones) - 2
		for i := 1; i <= hosts; i++ {
			c := make(net.IP, 4)
			copy(c, base)
			v := uint32(c[0])<<24 | uint32(c[1])<<16 | uint32(c[2])<<8 | uint32(c[3])
			v += uint32(i)
			c[0], c[1], c[2], c[3] = byte(v>>24), byte(v>>16), byte(v>>8), byte(v)
			if c.Equal(ip) || seen[c.String()] {
				continue
			}
			seen[c.String()] = true
			candidates = append(candidates, c)
		}
	}

	hostports := make([]string, len(candidates))
	for i, c := range candidates {
		hostports[i] = net.JoinHostPort(c.String(), defaultPort)
	}
	return probe(hostports)
}

// probe odpytuje równolegle podane adresy "ip:port" i zwraca pierwszy telefon, który odpowie.
func probe(hostports []string) (addr, name string, ok bool) {
	results := make(chan [2]string, len(hostports))
	sem := make(chan struct{}, 128)
	var wg sync.WaitGroup
	for _, hp := range hostports {
		wg.Add(1)
		go func(hostport string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			resp, err := probeClient.Get("http://" + hostport + "/info")
			if err != nil {
				return
			}
			defer resp.Body.Close()
			body, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
			if port, name, ok := parseHello(string(body)); ok {
				h, _, _ := net.SplitHostPort(hostport)
				results <- [2]string{net.JoinHostPort(h, port), name}
			}
		}(hp)
	}
	go func() {
		wg.Wait()
		close(results)
	}()
	r, ok := <-results
	return r[0], r[1], ok
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
			addr, name = discover()
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
