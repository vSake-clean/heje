# Minecraft 2D for PC Engine (HuCard) - native 240x135 (M5Stack Cardputer)
CL65   ?= cl65
PY     ?= python3
ROM    := build/minecraft.pce
SRC    := src/crt0.s src/main.c src/assets.c src/hw.s

all: $(ROM)

src/assets.c src/assets.h: tools/gen_assets.py
	$(PY) tools/gen_assets.py

build/linear.bin: $(SRC) src/assets.h src/hw.h src/pce.cfg
	@mkdir -p build
	$(CL65) -t pce -C src/pce.cfg -Oirs --cpu huc6280 -g -Ln build/minecraft.lbl -m build/minecraft.map -o $@ $(SRC)

# ld65 emits the 32K image linearly ($8000-$FFFF); a HuCard boots from bank 0,
# so the last 8K bank (with the reset vector) must come first.
$(ROM): build/linear.bin
	dd if=$< of=$@ bs=8192 skip=3 status=none
	dd if=$< bs=8192 count=3 status=none >> $@
	@ls -l $@

clean:
	rm -rf build/*.bin build/*.map src/*.o

.PHONY: all clean
