#!/usr/bin/env bash
# Kopiuje aplikację webową (z katalogu nadrzędnego) do zasobów Androida.
set -e
cd "$(dirname "$0")"
DST=app/src/main/assets/www
rm -rf "$DST"; mkdir -p "$DST/css" "$DST/js"
cp ../index.html ../icon.svg ../manifest.webmanifest ../sw.js "$DST/"
cp ../css/styles.css "$DST/css/"
cp ../js/data.js ../js/labs.js ../js/app.js "$DST/js/"
echo "Zsynchronizowano web -> $DST"
