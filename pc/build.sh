#!/usr/bin/env bash
# Buduje EkranTelefonu.exe (Windows 64-bit) – działa także na Linuksie/macOS (kompilacja skrośna Go).
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p ../dist
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-s -w -H=windowsgui" -o ../dist/EkranTelefonu.exe .
echo "Gotowe: $(cd ../dist && pwd)/EkranTelefonu.exe"
