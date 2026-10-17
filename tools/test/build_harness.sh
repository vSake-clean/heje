#!/bin/sh
# Builds a headless test harness around pce-go, the exact PC Engine core used
# by "Cardputer Game Station". Usage:
#   tools/test/build_harness.sh            -> tools/test/harness
#   printf "60 -\n3 T\n120 -\nshot a.ppm\n" | tools/test/harness build/minecraft.pce
set -e
D=$(cd "$(dirname "$0")" && pwd)
[ -d "$D/gs" ] || git clone --depth 1 https://github.com/geo-tp/Cardputer-Game-Station-Emulators "$D/gs"
P="$D/gs/src/pce/pce-go"
sed 's/^void RunPCE(void)/static void RunPCE_unused(void)/' "$P/pce-go.c" > "$D/pcego_patched.c"
gcc -O2 -w -I"$P" -o "$D/harness" "$D/harness.c" "$D/pcego_patched.c" "$P/pce.c" "$P/gfx.c" "$P/psg.c" "$P/h6280.c"
echo "built $D/harness"
