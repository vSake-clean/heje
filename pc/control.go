package main

import (
	"bufio"
	"net"
	"strings"
	"sync"
	"time"
)

// controller utrzymuje jedno stałe połączenie sterujące z telefonem (/ctl) i
// wysyła nim polecenia jako kolejne linie tekstu, np. "a=move&x=0.5&y=0.4".
// To znacznie szybsze niż osobne zapytanie HTTP na każdy ruch myszy.
type controller struct {
	queue    chan string
	onAccess func(ok bool) // wywoływane, gdy telefon zgłosi stan sterowania

	mu   sync.Mutex
	addr string
}

func newController(onAccess func(ok bool)) *controller {
	c := &controller{queue: make(chan string, 1024), onAccess: onAccess}
	go c.loop()
	return c
}

func (c *controller) setAddr(addr string) {
	c.mu.Lock()
	c.addr = addr
	c.mu.Unlock()
}

func (c *controller) getAddr() string {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.addr
}

// send kolejkuje polecenie. Nigdy nie blokuje okna: gdy kolejka jest pełna,
// pomija ruchy myszy (ale nie wciśnięcia, puszczenia ani klawisze).
func (c *controller) send(cmd string) {
	if c.getAddr() == "" {
		return
	}
	if strings.HasPrefix(cmd, "a=move&") {
		select {
		case c.queue <- cmd:
		default:
		}
		return
	}
	select {
	case c.queue <- cmd:
	case <-time.After(200 * time.Millisecond):
	}
}

func (c *controller) loop() {
	var conn net.Conn
	var w *bufio.Writer
	var connAddr string
	closeConn := func() {
		if conn != nil {
			conn.Close()
			conn = nil
		}
	}
	for cmd := range c.queue {
		addr := c.getAddr()
		if addr == "" {
			closeConn()
			continue
		}
		if conn != nil && connAddr != addr {
			closeConn()
		}
		if conn == nil {
			cn, err := net.DialTimeout("tcp", addr, 2*time.Second)
			if err != nil {
				continue
			}
			if _, err := cn.Write([]byte("GET /ctl HTTP/1.1\r\nHost: telefon\r\n\r\n")); err != nil {
				cn.Close()
				continue
			}
			conn, connAddr, w = cn, addr, bufio.NewWriter(cn)
			go c.readReplies(cn)
		}
		w.WriteString(cmd)
		w.WriteByte('\n')
		// Dopisz wszystko, co już czeka, i wyślij jednym pakietem.
	drain:
		for {
			select {
			case more := <-c.queue:
				w.WriteString(more)
				w.WriteByte('\n')
			default:
				break drain
			}
		}
		if err := w.Flush(); err != nil {
			closeConn()
		}
	}
}

// readReplies czyta odpowiedzi telefonu: "OK" albo "NOACCESS" (sterowanie
// niewłączone w Ułatwieniach dostępu).
func (c *controller) readReplies(cn net.Conn) {
	r := bufio.NewReader(cn)
	for {
		line, err := r.ReadString('\n')
		if err != nil {
			return
		}
		switch strings.TrimSpace(line) {
		case "OK":
			if c.onAccess != nil {
				c.onAccess(true)
			}
		case "NOACCESS":
			if c.onAccess != nil {
				c.onAccess(false)
			}
		}
	}
}
