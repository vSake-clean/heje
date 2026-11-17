package main

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"fmt"
	"image"
	"image/color"
	"image/draw"
	"image/jpeg"
	"io"
	"mime"
	"net"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
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
	// setControlTarget podaje adres telefonu, do którego okno wysyła sterowanie
	// (kliknięcia, przewijanie, klawiatura). Pusty string wyłącza sterowanie.
	setControlTarget(addr string)
}

// discover szuka telefonu dwiema metodami naraz, aż któraś zadziała:
//   - nasłuch pakietów rozgłoszeniowych UDP wysyłanych przez aplikację,
//   - aktywne sprawdzanie adresów w podsieci komputera (działa też, gdy zapora
//     Windows blokuje ruch przychodzący, np. w sieci z hotspotu telefonu).
//
// Zwraca adres "ip:port" serwera strumienia oraz nazwę telefonu.
//   - kabel USB z debugowaniem (adb forward), gdy na komputerze jest ADB.
//
// usbOnly ogranicza szukanie do kabla. hint dostaje podpowiedzi dotyczące USB.
func discover(usbOnly bool, hint func(string)) (addr, name string) {
	found := make(chan [2]string, 1)
	done := make(chan struct{})
	defer close(done)
	report := func(a, n string) {
		select {
		case found <- [2]string{a, n}:
		default:
		}
	}

	go watchUSB(done, report, hint)
	if usbOnly {
		r := <-found
		return r[0], r[1]
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
	br := bufio.NewReaderSize(resp.Body, 256*1024)

	// Czytanie i dekodowanie idą osobno: czytnik odbiera klatki tak szybko, jak
	// przychodzą, i zostawia tylko najnowszą. Gdy komputer nie nadąża z
	// dekodowaniem, stare klatki są pomijane, więc obraz nie zostaje w tyle.
	latest := make(chan []byte, 1)
	done := make(chan struct{})
	go func() {
		defer close(done)
		var bufs [3][]byte
		n := 0
		for data := range latest {
			img, err := jpeg.Decode(bytes.NewReader(data))
			if err != nil {
				continue
			}
			b := img.Bounds()
			buf := &bufs[n%len(bufs)]
			n++
			if need := b.Dx() * b.Dy() * 4; cap(*buf) < need {
				*buf = make([]byte, need)
			} else {
				*buf = (*buf)[:need]
			}
			toBGRA(img, *buf)
			v.setFrame(*buf, b.Dx(), b.Dy())
		}
	}()
	defer func() {
		close(latest)
		<-done
	}()

	for {
		data, err := readFrame(br)
		if err != nil {
			if errors.Is(err, io.EOF) || errors.Is(err, io.ErrUnexpectedEOF) {
				return errors.New("telefon zakończył udostępnianie")
			}
			return err
		}
		select {
		case latest <- data:
		default:
			select { // dekoder zajęty – podmień czekającą klatkę na nowszą
			case <-latest:
			default:
			}
			latest <- data
		}
	}
}

// readFrame czyta jedną klatkę strumienia MJPEG: linię granicy "--frame",
// nagłówki i dokładnie Content-Length bajtów JPEG. Klatka jest gotowa od razu
// po odebraniu – nie czekamy na początek następnej (jak robi to multipart.Reader),
// więc ostatnia zmiana na ekranie telefonu pojawia się bez opóźnienia.
func readFrame(br *bufio.Reader) ([]byte, error) {
	// Granica (pomijamy ewentualne puste linie po poprzedniej klatce).
	for {
		line, err := br.ReadString('\n')
		if err != nil {
			return nil, err
		}
		if strings.HasPrefix(line, "--") {
			break
		}
	}
	length := -1
	for {
		line, err := br.ReadString('\n')
		if err != nil {
			return nil, err
		}
		line = strings.TrimRight(line, "\r\n")
		if line == "" {
			break
		}
		if k, v, ok := strings.Cut(line, ":"); ok && strings.EqualFold(strings.TrimSpace(k), "Content-Length") {
			n, err := strconv.Atoi(strings.TrimSpace(v))
			if err != nil || n < 0 || n > 32<<20 {
				return nil, errors.New("nieprawidłowa długość klatki")
			}
			length = n
		}
	}
	if length < 0 {
		return nil, errors.New("klatka bez długości")
	}
	data := make([]byte, length)
	if _, err := io.ReadFull(br, data); err != nil {
		return nil, err
	}
	return data, nil
}

// toBGRA zamienia zdekodowany obraz na piksele BGRA dla Windows (jednym przebiegiem).
func toBGRA(img image.Image, out []byte) {
	b := img.Bounds()
	w := b.Dx()
	if yc, ok := img.(*image.YCbCr); ok {
		i := 0
		for y := b.Min.Y; y < b.Max.Y; y++ {
			for x := b.Min.X; x < b.Min.X+w; x++ {
				yi := yc.YOffset(x, y)
				ci := yc.COffset(x, y)
				r, g, bl := color.YCbCrToRGB(yc.Y[yi], yc.Cb[ci], yc.Cr[ci])
				out[i], out[i+1], out[i+2], out[i+3] = bl, g, r, 255
				i += 4
			}
		}
		return
	}
	rgba := image.NewRGBA(image.Rect(0, 0, w, b.Dy()))
	draw.Draw(rgba, rgba.Bounds(), img, b.Min, draw.Src)
	for i := 0; i+3 < len(out); i += 4 {
		out[i], out[i+1], out[i+2], out[i+3] = rgba.Pix[i+2], rgba.Pix[i+1], rgba.Pix[i], 255
	}
}

// run to główna pętla: znajdź telefon (lub użyj podanego adresu), oglądaj, po rozłączeniu ponów.
// W trybie automatycznym w trakcie oglądania pilnuje kabla: gdy telefon pojawi się
// na USB, przełącza się na kabel, a gdy kabel zniknie – wraca na Wi-Fi.
func run(manual string, v viewer) {
	usbOnly := strings.EqualFold(manual, "usb")
	auto := manual == "" // tylko wtedy przełączamy się sami
	var next [2]string   // adres i nazwa, na które przełączamy się od razu
	for {
		addr, name := next[0], next[1]
		next = [2]string{}
		if addr == "" {
			addr = manual
		}
		if addr == "" || usbOnly {
			base := "Szukam telefonu (Wi-Fi, hotspot lub kabel USB)... " +
				"Uruchom aplikację na telefonie i naciśnij Start."
			if usbOnly {
				base = "Szukam telefonu podłączonego kablem USB... " +
					"Uruchom aplikację na telefonie i naciśnij Start."
			}
			v.setStatus(base)
			var lastHint string
			addr, name = discover(usbOnly, func(h string) {
				if h == lastHint {
					return
				}
				lastHint = h
				if h == "" {
					v.setStatus(base)
				} else {
					v.setStatus(base + "\n\n" + h)
				}
			})
		} else if _, _, err := net.SplitHostPort(addr); err != nil {
			addr = net.JoinHostPort(addr, defaultPort)
		}
		if name == "" {
			name = addr
		}

		ctx, cancel := context.WithCancel(context.Background())
		onUSB := strings.HasPrefix(name, "USB:")
		switched := make(chan [2]string, 1)
		var cableGone atomic.Bool
		if auto && !onUSB {
			// Oglądamy przez Wi-Fi – sprawdzaj, czy nie podłączono kabla.
			go watchUSB(ctx.Done(), func(a, n string) {
				switched <- [2]string{a, n}
				cancel()
			}, func(string) {})
		}
		if auto && onUSB {
			// Oglądamy przez kabel – gdy go odłączysz, wracamy na Wi-Fi.
			go watchUSBGone(ctx.Done(), func() {
				cableGone.Store(true)
				cancel()
			})
		}

		v.setStatus("Łączenie z " + name + " (" + addr + ")...")
		v.setControlTarget(addr)
		err := stream(ctx, addr, &titled{viewer: v, name: name})
		cancel()
		v.setControlTarget("")

		select {
		case sw := <-switched:
			v.setStatus("Wykryto kabel USB – przełączam na kabel...")
			next = sw
			continue
		default:
		}
		if cableGone.Load() {
			v.setStatus("Kabel USB odłączony – wracam na Wi-Fi...")
			continue
		}
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
