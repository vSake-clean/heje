package main

import (
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestParseHello(t *testing.T) {
	port, name, ok := parseHello("EKRANTELEFONU 8080 samsung SM-A515F")
	if !ok || port != "8080" || name != "samsung SM-A515F" {
		t.Fatalf("got %q %q %v", port, name, ok)
	}
	if _, _, ok := parseHello("<html>"); ok {
		t.Fatal("accepted garbage")
	}
}

func TestProbeFindsPhone(t *testing.T) {
	phone := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/info" {
			fmt.Fprint(w, "EKRANTELEFONU 8080 Test Phone")
		}
	}))
	defer phone.Close()
	other := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprint(w, "<html>router</html>")
	}))
	defer other.Close()

	host, _, _ := net.SplitHostPort(phone.Listener.Addr().String())
	addr, name, ok := probe([]string{"127.0.0.1:1", other.Listener.Addr().String(), phone.Listener.Addr().String()})
	if !ok || addr != net.JoinHostPort(host, "8080") || name != "Test Phone" {
		t.Fatalf("got %q %q %v", addr, name, ok)
	}
	if _, _, ok := probe([]string{"127.0.0.1:1", other.Listener.Addr().String()}); ok {
		t.Fatal("found phone where there is none")
	}
}
