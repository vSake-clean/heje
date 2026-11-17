package main

import (
	"bufio"
	"net"
	"strings"
	"testing"
	"time"
)

func TestControllerSendsLinesOverOneConnection(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()

	got := make(chan string, 16)
	accepted := make(chan struct{}, 4)
	go func() {
		for {
			cn, err := ln.Accept()
			if err != nil {
				return
			}
			accepted <- struct{}{}
			go func(cn net.Conn) {
				r := bufio.NewReader(cn)
				for { // nagłówek HTTP
					l, err := r.ReadString('\n')
					if err != nil || l == "\r\n" {
						break
					}
				}
				cn.Write([]byte("HTTP/1.1 200 OK\r\n\r\n"))
				first := true
				for {
					l, err := r.ReadString('\n')
					if err != nil {
						return
					}
					if first {
						cn.Write([]byte("NOACCESS\n"))
						first = false
					}
					got <- strings.TrimSpace(l)
				}
			}(cn)
		}
	}()

	access := make(chan bool, 4)
	c := newController(func(ok bool) { access <- ok })
	c.setAddr(ln.Addr().String())
	for _, cmd := range []string{"a=down&x=0.1&y=0.2", "a=move&x=0.2&y=0.3", "a=up&x=0.3&y=0.4"} {
		c.send(cmd)
	}

	want := []string{"a=down&x=0.1&y=0.2", "a=move&x=0.2&y=0.3", "a=up&x=0.3&y=0.4"}
	for _, w := range want {
		select {
		case g := <-got:
			if g != w {
				t.Fatalf("got %q, want %q", g, w)
			}
		case <-time.After(2 * time.Second):
			t.Fatalf("timeout waiting for %q", w)
		}
	}
	select {
	case ok := <-access:
		if ok {
			t.Fatal("expected NOACCESS to be reported")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("NOACCESS not reported")
	}
	if n := len(accepted); n != 1 {
		t.Fatalf("expected 1 connection, got %d", n)
	}
}
