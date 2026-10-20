//go:build !windows

package main

import (
	"fmt"
	"os"
	"time"
)

// Wersja konsolowa (Linux/macOS) – przydatna do testów połączenia.
type consoleViewer struct {
	frames int
	last   time.Time
}

func (c *consoleViewer) setStatus(s string) { fmt.Println(s) }

func (c *consoleViewer) setControlTarget(addr string) {
	if addr != "" {
		fmt.Println("sterowanie dostępne:", addr)
	}
}

func (c *consoleViewer) setFrame(_ []byte, w, h int) {
	c.frames++
	if time.Since(c.last) > time.Second {
		fmt.Printf("klatka %d: %dx%d\n", c.frames, w, h)
		c.last = time.Now()
	}
}

func main() {
	manual := ""
	if len(os.Args) > 1 {
		manual = os.Args[1]
	}
	run(manual, &consoleViewer{})
}
