#!/usr/bin/env python3
"""Generates src/assets.c / src/assets.h (tiles, sprites, palettes, font, music)
for the PC Engine Minecraft-like game. All art is defined here as ASCII."""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src")

# ---------------------------------------------------------------- palettes
# RGB components 0..7 (PC Engine VCE is 3 bits per channel)
MASTER = [
    (3, 5, 7),  # 0 sky / transparent
    (0, 0, 0),  # 1 K black
    (7, 7, 7),  # 2 W white
    (2, 6, 1),  # 3 G grass
    (1, 4, 1),  # 4 g dark green
    (5, 3, 1),  # 5 D dirt
    (3, 2, 0),  # 6 d dark brown
    (4, 4, 4),  # 7 S stone
    (2, 2, 2),  # 8 s dark gray
    (6, 6, 6),  # 9 L light gray
    (6, 5, 2),  # 10 T tan
    (7, 7, 4),  # 11 Y sand
    (7, 6, 0),  # 12 O gold
    (2, 7, 7),  # 13 C diamond
    (7, 5, 4),  # 14 I iron / peach
    (7, 1, 1),  # 15 R red
]
MKEY = ".KWGgDdSsLTYOCIR"

CHARS = [
    (0, 0, 0),  # 0 transparent
    (0, 0, 0),  # 1 K black
    (7, 7, 7),  # 2 W white
    (7, 5, 3),  # 3 k skin
    (3, 2, 0),  # 4 h hair
    (0, 5, 5),  # 5 c shirt
    (2, 2, 6),  # 6 p pants
    (3, 3, 3),  # 7 o shoes
    (2, 6, 2),  # 8 z zombie skin
    (1, 4, 1),  # 9 Z zombie dark
    (2, 2, 7),  # 10 b eye blue
    (5, 3, 2),  # 11 m mouth
    (7, 1, 1),  # 12 r red
    (4, 1, 5),  # 13 v purple
    (0, 3, 3),  # 14 C dark shirt
    (1, 1, 4),  # 15 P dark pants
]
CKEY = ".KWkhcpozZbmrvCP"

def night(c):
    return tuple(int(v * 0.5 + 0.0) for v in c)

FONTPALS = [
    [(0, 0, 0), (7, 7, 7), (1, 1, 2), (3, 3, 3)] + [(0, 0, 0)] * 12,  # pal 2 white
    [(0, 0, 0), (7, 7, 1), (1, 1, 2), (3, 3, 3)] + [(0, 0, 0)] * 12,  # pal 3 yellow
    [(0, 0, 0), (4, 4, 5), (1, 1, 2), (2, 2, 2)] + [(0, 0, 0)] * 12,  # pal 4 gray
]

def vce(c):
    r, g, b = c
    return (g << 6) | (r << 3) | b

# ---------------------------------------------------------------- block tiles (8x8)
BLOCKS = {
 "AIR": ["........"] * 8,
 "CAVE": ["ssssssss", "sKssssKs", "ssssKsss", "sssssssK",
          "ssKsssss", "sssssKss", "Kssssss.".replace(".", "s"), "sssKssss"],
 "GRASS": ["GGGGGGGG", "GGgGGGgG", "gDGgDGgD", "DDdDDDDD",
           "DDDDDdDD", "DdDDDDDD", "DDDDdDDd", "DDdDDDDD"],
 "DIRT": ["DDDDDdDD", "DdDDDDDD", "DDDDdDDd", "DDdDDDDD",
          "DDDDDDdD", "dDDdDDDD", "DDDDDDDD", "DDDdDDdD"],
 "STONE": ["SSSSSSSS", "SSsSSSSS", "SSSSSLSS", "SsSSSSSs",
           "SSSSsSSS", "SLSSSSSS", "SSSSSSsS", "SSsSSSSS"],
 "COBBLE": ["SSSsLSSs", "SLSsSSSs", "SSSsssss", "ssSSSsSS",
            "SLSSSsLS", "SSSSssSS", "sssSSsss", "LSSsSSSL"],
 "LOG": ["dDdDDdDd", "dDdDdDDd", "dDDdDdDd", "dDdDdDDd",
         "dDdDDdDd", "DdDdDdDd", "dDdDdDDd", "dDDdDdDd"],
 "LEAVES": ["gGgGGgGg", "GgGgGGgG", "gGGg.GgG", "GgGGgGGg",
            "gGgGGgg.", "GGgGgGGg", "gG.GgGgG", "GgGgGGgG"],
 "PLANKS": ["TTTTTTTT", "TTTTTTTT", "TTTDTTTT", "DDDDDDDD",
            "TTTTTTTT", "TTTTTTTD", "TTTTTTTT", "DDDDDDDD"],
 "SAND": ["YYYYYYYY", "YTYYYYYY", "YYYYYTYY", "YYYYYYYY",
          "YYTYYYYY", "YYYYYYTY", "YYYYYYYY", "TYYYTYYY"],
 "COAL": ["SSSSSSSS", "SKKSSSSS", "SKKSSKSS", "SSSSKKSS",
          "SSSSSSSS", "SSKSSSKK", "SKKSSSKS", "SSSSSSSS"],
 "IRON": ["SSSSSSSS", "SIISSSSS", "SIDSSISS", "SSSSIDSS",
          "SSSSSSSS", "SSISSSII", "SIDSSSID", "SSSSSSSS"],
 "GOLD": ["SSSSSSSS", "SOOSSSSS", "SOTSSOSS", "SSSSOOSS",
          "SSSSSSSS", "SSOSSSOO", "SOTSSSOT", "SSSSSSSS"],
 "DIAMOND": ["SSSSSSSS", "SCWSSSSS", "SCCSSCSS", "SSSSCWSS",
             "SSSSSSSS", "SSCSSSCW", "SCCSSSCC", "SSSSSSSS"],
 "BEDROCK": ["sKsSsKKs", "KsSKsSsK", "sKKsLsKs", "SsKsKKsS",
             "KsSsKsSK", "sKsKSsKs", "KssSKsKS", "sKsKsSsK"],
 "TNT": ["RRRRRRRR", "RdRRdRRd", "WWWWWWWW", "WKWKKWKW",
         "WWWWWWWW", "RdRRdRRd", "RRRRRRRR", "RRRRRRRR"],
 "TNTLIT": ["WWWWWWWW", "WLWWLWWL", "WWWWWWWW", "WKWKKWKW",
            "WWWWWWWW", "WLWWLWWL", "WWWWWWWW", "WWWWWWWW"],
 "GLASS": ["LLLLLLLL", "L......L", "L.W....L", "L..W...L",
           "L......L", "L....W.L", "L......L", "LLLLLLLL"],
 "FLOWER": ["........", "...RR...", "..RORR..", "...RR...",
            "...g....", "..gg.g..", "....gg..", "...g...."],
 "TABLE": ["dddddddd", "TTTTTTTT", "TdTdTTdT", "dddddddd",
           "TT.TT.TT", "TDDTTDDT", "TTdTTdTT", "TTTTTTTT"],
}
BLOCK_ORDER = ["AIR", "CAVE", "GRASS", "DIRT", "STONE", "COBBLE", "LOG", "LEAVES",
               "PLANKS", "SAND", "COAL", "IRON", "GOLD", "DIAMOND", "BEDROCK",
               "TNT", "TNTLIT", "GLASS", "FLOWER", "TABLE"]

ITEMTILES = {
 "COALI": ["........", "..KKs...", ".KKsKK..", ".KKKKsK.",
           ".sKKKKK.", "..KKKK..", "........", "........"],
 "IRONI": ["........", "...WWW..", "..IIIII.", ".IIWIIII",
           ".IIIIIID", "..DDDDD.", "........", "........"],
 "GOLDI": ["........", "...WWW..", "..OOOOO.", ".OOWOOOO",
           ".OOOOOOT", "..TTTTT.", "........", "........"],
 "DIAMI": ["........", "..CCCC..", ".CWCCCC.", "CWCCCCCC",
           ".CCCCCC.", "..CCCC..", "...CC...", "........"],
 "APPLE": ["....d...", "...dg...", ".RRRRRR.", "RWRRRRRR",
           "RRRRRRRR", "RRRRRRRR", ".RRRRRR.", "..RR.R.."],
 "WPICK": ["..TTTTT.", ".T...dTT", "....d.T.", "...d..T.",
           "..d.....", ".d......", "d.......", "........"],
 "SPICK": ["..SSSSS.", ".S...dSS", "....d.S.", "...d..S.",
           "..d.....", ".d......", "d.......", "........"],
 "IPICK": ["..LLLLL.", ".L...dLL", "....d.L.", "...d..L.",
           "..d.....", ".d......", "d.......", "........"],
 "DPICK": ["..CCCCC.", ".C...dCC", "....d.C.", "...d..C.",
           "..d.....", ".d......", "d.......", "........"],
 "SWORD": ["......LL", ".....LWL", "....LWL.", "..dLWL..",
           "..dWL...", ".dKd....", "dK.d....", "K......."],
 "CROWN": ["........", "O..O..O.", "OO.O.OO.", "OOOOOOO.",
           "OROCORO.", "OOOOOOO.", "........", "........"],
}
ITEMTILE_ORDER = ["COALI", "IRONI", "GOLDI", "DIAMI", "APPLE", "WPICK", "SPICK",
                  "IPICK", "DPICK", "SWORD", "CROWN"]

# ---------------------------------------------------------------- font 5x7
F = {}
def g(ch, *rows):
    F[ch] = rows
g(' ', *(["     "] * 7))
g('A', " ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #")
g('B', "#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### ")
g('C', " ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### ")
g('D', "#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### ")
g('E', "#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####")
g('F', "#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    ")
g('G', " ### ", "#   #", "#    ", "# ###", "#   #", "#   #", " ####")
g('H', "#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #")
g('I', " ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### ")
g('J', "  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  ")
g('K', "#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #")
g('L', "#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####")
g('M', "#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #")
g('N', "#   #", "#   #", "##  #", "# # #", "#  ##", "#   #", "#   #")
g('O', " ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### ")
g('P', "#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    ")
g('Q', " ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #")
g('R', "#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #")
g('S', " ####", "#    ", "#    ", " ### ", "    #", "    #", "#### ")
g('T', "#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ")
g('U', "#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### ")
g('V', "#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  ")
g('W', "#   #", "#   #", "#   #", "# # #", "# # #", "# # #", " # # ")
g('X', "#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #")
g('Y', "#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  ")
g('Z', "#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####")
g('0', " ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### ")
g('1', "  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### ")
g('2', " ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####")
g('3', "#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### ")
g('4', "   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # ")
g('5', "#####", "#    ", "#### ", "    #", "    #", "#   #", " ### ")
g('6', "  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### ")
g('7', "#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   ")
g('8', " ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### ")
g('9', " ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  ")
g('!', "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "     ", "  #  ")
g('.', "     ", "     ", "     ", "     ", "     ", " ##  ", " ##  ")
g(',', "     ", "     ", "     ", "     ", " ##  ", "  #  ", " #   ")
g(':', "     ", " ##  ", " ##  ", "     ", " ##  ", " ##  ", "     ")
g('-', "     ", "     ", "     ", "#####", "     ", "     ", "     ")
g('+', "     ", "  #  ", "  #  ", "#####", "  #  ", "  #  ", "     ")
g('*', "     ", "     ", "#   #", " # # ", "  #  ", " # # ", "#   #")
g('<', "   # ", "  #  ", " #   ", "#    ", " #   ", "  #  ", "   # ")
g('>', " #   ", "  #  ", "   # ", "    #", "   # ", "  #  ", " #   ")
g('?', " ### ", "#   #", "    #", "   # ", "  #  ", "     ", "  #  ")
g('/', "    #", "    #", "   # ", "  #  ", " #   ", "#    ", "#    ")
g('=', "     ", "     ", "#####", "     ", "#####", "     ", "     ")
g('(', "   # ", "  #  ", " #   ", " #   ", " #   ", "  #  ", "   # ")
g(')', " #   ", "  #  ", "   # ", "   # ", "   # ", "  #  ", " #   ")
g('[', " ### ", " #   ", " #   ", " #   ", " #   ", " #   ", " ### ")
g(']', " ### ", "   # ", "   # ", "   # ", "   # ", "   # ", " ### ")
g("'", "  #  ", "  #  ", " #   ", "     ", "     ", "     ", "     ")
g('"', " # # ", " # # ", "     ", "     ", "     ", "     ", "     ")
g('#', "     ", " # # ", "#####", " # # ", "#####", " # # ", "     ")  # unused
g('^', "  #  ", " ### ", "# # #", "  #  ", "  #  ", "  #  ", "     ")  # arrow up
g('_', "     ", "  #  ", "  #  ", "  #  ", "# # #", " ### ", "  #  ")  # arrow down

def font_tile(ch):
    rows = F.get(ch, F[' '])
    out = []
    for y in range(8):
        line = ""
        for x in range(8):
            if 1 <= x <= 5 and y < 7 and rows[y][x - 1] == '#':
                line += "1"
            else:
                line += "2"
        out.append(line)
    return out

# ---------------------------------------------------------------- sprites (8 wide x 16 tall, left half of 16x16)
def pad16(rows, top=0):
    rows = ["........"] * top + list(rows)
    rows += ["........"] * (16 - len(rows))
    return rows

STEVE_TOP = [
 ".hhhhhh.",
 "hhhhhhhh",
 "hkkhkkkk",
 "kkkkWbkW".replace("W", "W"),
 "kkkkkkkk",
 "kkkmmmk.",
 ".cccccc.",
]
STEVE_IDLE = STEVE_TOP + [
 "kcccccck",
 "kcccccck",
 "k.cccc.k",
 "..pppp..",
 "..pppp..",
 "..pp.pp.",
 "..oo.oo.",
]
STEVE_W1 = STEVE_TOP + [
 "kcccccck",
 ".kccccck",
 "..ccccck",
 "..pppp..",
 ".pp..pp.",
 "pp....pp",
 "oo.....o",
]
STEVE_W2 = STEVE_TOP + [
 "kcccccck",
 "kccccck.",
 "kcccc...",
 "..pppp..",
 "..pppp..",
 "..pppp..",
 "..oooo..",
]
ZTOP = [
 ".ZZZZZZ.",
 "ZzzzzzzZ",
 "zzzzzzzz",
 "zzzzKzzK",
 "zzzzzzzz",
 "zzzZZZz.",
 ".CCCCCC.",
]
ZOMBIE1 = ZTOP + [
 "CCCCzzzz",
 "CCCCCCCC",
 ".CCCC...",
 "..PPPP..",
 "..PPPP..",
 "..PP.PP.",
 "..PP.PP.",
]
ZOMBIE2 = ZTOP + [
 "CCCCzzzz",
 "CCCCCCCC",
 ".CCCC...",
 "..PPPP..",
 ".PP..PP.",
 "PP....PP",
 "PP.....P",
]
CURSOR = ["WWK..KWW", "W......W", "K......K", "........",
          "........", "K......K", "W......W", "WWK..KWW"]
CRACK1 = ["........", "........", "...K....", "...KK...",
          "....K...", "........", "........", "........"]
CRACK2 = ["........", ".K......", "..K.K...", "...KK...",
          "...K.K..", "..K...K.", "........", "........"]
CRACK3 = ["K.....K.", ".K...K..", "..KK.K..", "...KK...",
          "..K.KK..", ".K...KK.", "K.....K.", ".......K"]

def overlay(base, top):
    return ["".join(t if t != '.' else b for b, t in zip(rb, rt)) for rb, rt in zip(base, top)]

HEART_FULL = [".KK.KK..", "KRRKRRK.", "KRWRRRK.", "KRRRRRK.", ".KRRRK..", "..KRK...", "...K....", "........"]
HEART_HALF = [".KK.KK..", "KRRKssK.", "KRWRssK.", "KRRRssK.", ".KRRsK..", "..KRK...", "...K....", "........"]
HEART_EMPTY = [".KK.KK..", "KssKssK.", "KsssssK.", "KsssssK.", ".KsssK..", "..KsK...", "...K....", "........"]

def digit_sprite(ch):
    rows = F[ch]
    grid = [[0] * 8 for _ in range(9)]
    for y in range(7):
        for x in range(5):
            if rows[y][x] == '#':
                grid[y + 1][x + 1] = 2
    out = [[0] * 8 for _ in range(9)]
    for y in range(9):
        for x in range(8):
            if grid[y][x] == 2:
                out[y][x] = 2
            else:
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        yy, xx = y + dy, x + dx
                        if 0 <= yy < 9 and 0 <= xx < 8 and grid[yy][xx] == 2:
                            out[y][x] = 1
    return ["".join(MKEY[v] for v in r) for r in out]

# ---------------------------------------------------------------- encoders
def enc_tile(rows, key):
    """PCE BG tile: 16 words; word y = plane0 | plane1<<8 (rows 0-7), then planes 2/3."""
    px = [[key.index(c) for c in r] for r in rows]
    data = []
    for planes in ((0, 1), (2, 3)):
        for y in range(8):
            lo = hi = 0
            for x in range(8):
                v = px[y][x]
                if v >> planes[0] & 1:
                    lo |= 0x80 >> x
                if v >> planes[1] & 1:
                    hi |= 0x80 >> x
            data += [lo, hi]
    return data

def enc_sprite8(rows, key):
    """Left-half 16x16 sprite, stored as 4 planes x 16 rows of one byte (the
    high byte of each pattern word; the right half is blank)."""
    rows = pad16(rows)
    px = [[key.index(c) for c in r] for r in rows]
    data = []
    for p in range(4):
        for y in range(16):
            b = 0
            for x in range(8):
                if px[y][x] >> p & 1:
                    b |= 0x80 >> x
            data.append(b)
    return data

def carr(name, data, typ="unsigned char"):
    s = "const %s %s[%d] = {\n" % (typ, name, len(data))
    suf = "u" if typ == "unsigned int" else ""
    for i in range(0, len(data), 16):
        s += "  " + ",".join(str(v) + suf for v in data[i:i + 16]) + ",\n"
    return s + "};\n"

def main():
    c = ['#include "assets.h"\n']
    h = ["#ifndef ASSETS_H\n#define ASSETS_H\n"]

    # BG tiles: blocks, item icons, font (contiguous, starting at tile $200)
    bg = []
    for n in BLOCK_ORDER:
        bg += enc_tile(BLOCKS[n], MKEY)
        h.append("#define B_%s %d\n" % (n, BLOCK_ORDER.index(n)))
    h.append("#define NBLOCKS %d\n" % len(BLOCK_ORDER))
    for i, n in enumerate(ITEMTILE_ORDER):
        bg += enc_tile(ITEMTILES[n], MKEY)
        h.append("#define T_%s %d\n" % (n, len(BLOCK_ORDER) + i))
    for code in range(32, 96):
        bg += enc_tile(font_tile(chr(code)), "012")
    h.append("#define T_FONT %d\n" % (len(BLOCK_ORDER) + len(ITEMTILE_ORDER)))
    c.append(carr("bg_tiles", bg))
    h.append("extern const unsigned char bg_tiles[%d];\n" % len(bg))
    h.append("#define BG_TILES_WORDS %d\n" % (len(bg) // 2))

    # sprites
    spr = []
    names = []
    def add(n, rows, key=CKEY, top=0):
        names.append(n)
        spr.extend(enc_sprite8(pad16(rows, top), key))
    add("STEVE0", STEVE_IDLE, top=2)
    add("STEVE1", STEVE_W1, top=2)
    add("STEVE2", STEVE_W2, top=2)
    add("ZOMB1", ZOMBIE1, top=2)
    add("ZOMB2", ZOMBIE2, top=2)
    add("CURSOR", CURSOR, MKEY)
    add("CUR1", overlay(CURSOR, CRACK1), MKEY)
    add("CUR2", overlay(CURSOR, CRACK2), MKEY)
    add("CUR3", overlay(CURSOR, CRACK3), MKEY)
    add("HFULL", HEART_FULL, MKEY)
    add("HHALF", HEART_HALF, MKEY)
    add("HEMPTY", HEART_EMPTY, MKEY)
    for d in "0123456789":
        add("DIG" + d, digit_sprite(d), MKEY)
    # block icons as sprites (for HUD) - every block
    for n in BLOCK_ORDER:
        add("SB_" + n, BLOCKS[n], MKEY)
    for n in ITEMTILE_ORDER:
        add("SI_" + n, ITEMTILES[n], MKEY)
    for i, n in enumerate(names):
        h.append("#define S_%s %d\n" % (n, i))
    h.append("#define NSPRITES %d\n" % len(names))
    c.append(carr("spr_data", spr))
    h.append("extern const unsigned char spr_data[%d];\n" % len(spr))

    # palettes (VCE words)
    pals = []
    pals.append([vce(x) for x in MASTER])            # bg pal 0 day
    pals.append([vce(night(x)) for x in MASTER])     # bg pal 0 night (uploaded on demand)
    for fp in FONTPALS:
        pals.append([vce(x) for x in fp])
    pals.append([vce(x) for x in MASTER])            # sprite pal 0
    pals.append([vce(x) for x in CHARS])             # sprite pal 1
    flat = [v for p in pals for v in p]
    c.append(carr("palettes", flat, "unsigned int"))
    h.append("extern const unsigned int palettes[%d];\n" % len(flat))
    h.append("#define PAL_DAY 0\n#define PAL_NIGHT 16\n#define PAL_FONT 32\n#define PAL_SPR0 80\n#define PAL_SPR1 96\n")
    h.append("#define SKY_DAY %d\n#define SKY_DUSK %d\n#define SKY_NIGHT %d\n#define SKY_DAWN %d\n#define PANEL_COL %d\n" % (
        vce((3, 5, 7)), vce((6, 3, 2)), vce((0, 0, 1)), vce((5, 3, 4)), vce((1, 1, 2))))

    # music: note periods for PSG (clock 3579545 / 32 / f)
    import math
    def period(note):  # MIDI note
        f = 440.0 * 2 ** ((note - 69) / 12.0)
        return int(round(3579545 / 32 / f))
    # calm original tune: (midi note or 0 rest, length in 1/8 beats of 12 frames)
    melody = [
        (64, 2), (67, 2), (71, 4), (0, 2), (69, 2), (67, 4), (0, 4),
        (60, 2), (64, 2), (67, 4), (0, 2), (72, 2), (71, 4), (0, 4),
        (62, 2), (66, 2), (69, 4), (0, 2), (67, 2), (66, 2), (64, 6), (0, 4),
        (60, 2), (62, 2), (64, 4), (67, 4), (64, 8), (0, 12),
    ]
    bass = [
        (40, 16), (0, 0), (36, 16), (38, 16), (36, 16), (0, 8),
    ]
    def enc_song(notes):
        out = []
        for n, l in notes:
            if l == 0:
                continue
            out += [period(n) if n else 0, l * 6]
        return out + [0xFFFF, 0]
    c.append(carr("song_mel", enc_song(melody), "unsigned int"))
    c.append(carr("song_bass", enc_song(bass), "unsigned int"))
    h.append("extern const unsigned int song_mel[];\nextern const unsigned int song_bass[];\n")
    h.append("#endif\n")

    with open(os.path.join(OUT, "assets.c"), "w") as f:
        f.write("/* generated by tools/gen_assets.py - do not edit */\n" + "".join(c))
    with open(os.path.join(OUT, "assets.h"), "w") as f:
        f.write("/* generated by tools/gen_assets.py - do not edit */\n" + "".join(h))
    print("bg tiles: %d bytes, sprites: %d (%d bytes)" % (len(bg), len(names), len(spr)))

if __name__ == "__main__":
    main()
