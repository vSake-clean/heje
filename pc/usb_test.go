package main

import (
	"archive/zip"
	"os"
	"path/filepath"
	"testing"
)

func writeZip(t *testing.T, path string, files map[string]string) {
	f, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	zw := zip.NewWriter(f)
	for name, body := range files {
		w, _ := zw.Create(name)
		w.Write([]byte(body))
	}
	zw.Close()
	f.Close()
}

func TestUnzipExtractsPlatformTools(t *testing.T) {
	dir := t.TempDir()
	z := filepath.Join(dir, "pt.zip")
	writeZip(t, z, map[string]string{"platform-tools/adb.exe": "x", "platform-tools/AdbWinApi.dll": "y"})
	out := filepath.Join(dir, "out")
	if err := unzip(z, out); err != nil {
		t.Fatal(err)
	}
	if b, err := os.ReadFile(filepath.Join(out, "platform-tools", "adb.exe")); err != nil || string(b) != "x" {
		t.Fatalf("adb.exe not extracted: %v", err)
	}
}

func TestUnzipRejectsPathTraversal(t *testing.T) {
	dir := t.TempDir()
	z := filepath.Join(dir, "bad.zip")
	writeZip(t, z, map[string]string{"../evil.txt": "boom"})
	if err := unzip(z, filepath.Join(dir, "out")); err == nil {
		t.Fatal("expected error for ../ path")
	}
	if _, err := os.Stat(filepath.Join(dir, "evil.txt")); err == nil {
		t.Fatal("file written outside target dir")
	}
}
