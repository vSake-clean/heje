/*
 * MINECRAFT 2D - PC Engine / TurboGrafx-16 HuCard
 * Native resolution 240x135 = M5Stack Cardputer screen (pixel perfect).
 *
 * The world (128 x 47 blocks) lives directly in the VDC background
 * attribute table (BAT, 128x64 tiles); rows 47-63 hold the menu screens.
 */
#include "hw.h"
#include "assets.h"

void vsync(void);

#pragma static-locals (on)

/* ------------------------------------------------------------ constants */
#define WW        128           /* world width (blocks)            */
#define WH        47            /* world height (blocks)           */
#define UIROW     47            /* first BAT row of the UI screen  */
#define SCR_W     240
#define SCR_H     135
#define TILEBASE  0x200         /* BG tile index of block 0        */
#define SPRBASE   0x200         /* sprite pattern index (VRAM $4000 >> 5) */
#define SATB_ADDR 0x7F00
#define NSAT      16

#define PW        6             /* hitbox */
#define PH        14
#define GRAV      2
#define JUMPV     30
#define MAXFALL   60
#define WALK      12
#define ZWALK     7

#define DAYLEN    21600u
#define T_DUSK    12000u
#define T_NIGHT   13200u
#define T_DAWN    19800u

#define CR_ON     0x00C8        /* BG + sprites + vblank irq */

enum { ST_TITLE, ST_PLAY, ST_MENU, ST_DEAD, ST_WIN };

enum {
    I_DIRT, I_COBBLE, I_LOG, I_PLANKS, I_SAND, I_GLASS, I_TNT, I_FLOWER, I_TABLE,
    I_COAL, I_IRON, I_GOLD, I_DIAMOND, I_APPLE, I_CROWN,
    I_WPICK, I_SPICK, I_IPICK, I_DPICK, I_SWORD, NITEMS
};
#define NSTACK I_WPICK
#define NONE   0xFF

/* ------------------------------------------------------------ tables */
static const unsigned char b_solid[NBLOCKS] =
    { 0,0,1,1,1,1,0,0,1,1,1,1,1,1,1,1,1,1,0,1 };
static const unsigned char b_hard[NBLOCKS] =
    { 0,0,14,14,45,45,30,4,30,14,55,65,65,80,255,1,255,8,1,30 };
static const unsigned char b_tier[NBLOCKS] =
    { 0,0,0,0,1,1,0,0,0,0,1,2,3,3,9,0,9,0,0,0 };
static const unsigned char b_stone[NBLOCKS] =
    { 0,0,0,0,1,1,0,0,0,0,1,1,1,1,0,0,0,0,0,0 };
static const unsigned char b_drop[NBLOCKS] = {
    NONE, NONE, I_DIRT, I_DIRT, I_COBBLE, I_COBBLE, I_LOG, NONE, I_PLANKS, I_SAND,
    I_COAL, I_IRON, I_GOLD, I_DIAMOND, NONE, NONE, NONE, I_GLASS, I_FLOWER, I_TABLE };

static const unsigned char it_block[NITEMS] = {
    B_DIRT, B_COBBLE, B_LOG, B_PLANKS, B_SAND, B_GLASS, B_TNT, B_FLOWER, B_TABLE,
    NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE };
static const unsigned char it_tile[NITEMS] = {
    B_DIRT, B_COBBLE, B_LOG, B_PLANKS, B_SAND, B_GLASS, B_TNT, B_FLOWER, B_TABLE,
    T_COALI, T_IRONI, T_GOLDI, T_DIAMI, T_APPLE, T_CROWN,
    T_WPICK, T_SPICK, T_IPICK, T_DPICK, T_SWORD };
static const unsigned char it_spr[NITEMS] = {
    S_SB_DIRT, S_SB_COBBLE, S_SB_LOG, S_SB_PLANKS, S_SB_SAND, S_SB_GLASS, S_SB_TNT,
    S_SB_FLOWER, S_SB_TABLE, S_SI_COALI, S_SI_IRONI, S_SI_GOLDI, S_SI_DIAMI,
    S_SI_APPLE, S_SI_CROWN, S_SI_WPICK, S_SI_SPICK, S_SI_IPICK, S_SI_DPICK, S_SI_SWORD };
static const char * const it_name[NITEMS] = {
    "ZIEMIA", "BRUK", "DREWNO", "DESKI", "PIASEK", "SZKLO", "TNT", "KWIAT", "STOL",
    "WEGIEL", "ZELAZO", "ZLOTO", "DIAMENT", "JABLKO", "KORONA",
    "KILOF DREW.", "KILOF KAM.", "KILOF ZEL.", "KILOF DIAM.", "MIECZ" };

typedef struct {
    unsigned char out, outn, a, an, b, bn, table;
} recipe_t;
#define NRECIPES 10
static const recipe_t recipes[NRECIPES] = {
    { I_PLANKS, 4, I_LOG, 1, NONE, 0, 0 },
    { I_TABLE, 1, I_PLANKS, 4, NONE, 0, 0 },
    { I_WPICK, 1, I_PLANKS, 5, NONE, 0, 1 },
    { I_SPICK, 1, I_COBBLE, 3, I_PLANKS, 2, 1 },
    { I_IPICK, 1, I_IRON, 3, I_PLANKS, 2, 1 },
    { I_DPICK, 1, I_DIAMOND, 3, I_PLANKS, 2, 1 },
    { I_SWORD, 1, I_IRON, 2, I_PLANKS, 1, 1 },
    { I_GLASS, 2, I_SAND, 2, I_COAL, 1, 1 },
    { I_TNT, 1, I_SAND, 4, I_COAL, 2, 1 },
    { I_CROWN, 1, I_DIAMOND, 5, I_GOLD, 5, 1 },
};

/* logo font for the title screen: width, then 5 rows (bit 4 = leftmost) */
static const unsigned char logo_font[9][6] = {
    { 5, 0x11, 0x1B, 0x15, 0x11, 0x11 },   /* M */
    { 3, 0x1C, 0x08, 0x08, 0x08, 0x1C },   /* I */
    { 4, 0x12, 0x1A, 0x16, 0x12, 0x12 },   /* N */
    { 3, 0x1C, 0x10, 0x18, 0x10, 0x1C },   /* E */
    { 3, 0x0C, 0x10, 0x10, 0x10, 0x0C },   /* C */
    { 4, 0x1C, 0x12, 0x1C, 0x14, 0x12 },   /* R */
    { 4, 0x0C, 0x12, 0x1E, 0x12, 0x12 },   /* A */
    { 3, 0x1C, 0x10, 0x18, 0x10, 0x10 },   /* F */
    { 3, 0x1C, 0x08, 0x08, 0x08, 0x08 },   /* T */
};

/* ------------------------------------------------------------ state */
static unsigned char state;
static unsigned char joy, joyold, joynew;
static unsigned int rs = 0xACE1;
static unsigned int frame;
static unsigned int tod;
static unsigned char phase = 0xFF;
static unsigned char surf[WW];

static unsigned int sat[NSAT * 4];
static unsigned int scroll_x, scroll_y;
static unsigned char shake;

/* player */
static int px, py, pvy;
static signed char kbx;
static unsigned char ground, facing, hp, invuln, anim;
static int peak;
static int spawn_x, spawn_y;
static unsigned char tier, hand;
static unsigned char inv[NITEMS];
static int camx, camy;

/* mining */
static int tx, ty;
static unsigned char tblock;
static int mx = -1, my;
static unsigned char mtime;
static unsigned char msound;

/* zombies */
#define NZ 3
static int zx[NZ], zy[NZ], zvy[NZ];
static unsigned char zon[NZ], zhp[NZ], zground[NZ], zface[NZ], zhurt[NZ];
static signed char zkb[NZ];
static unsigned char zspawn_t;

/* TNT */
#define NTNT 4
static unsigned char tnt_x[NTNT], tnt_y[NTNT], tnt_t[NTNT];

/* generic entity used by the physics routine */
static int ex, ey, evy;
static signed char evx;
static unsigned char eground, eblocked;

/* menu */
static unsigned char tab, msel;
static unsigned char mlist[NSTACK];
static unsigned char mcount;
static const char *mmsg;
static unsigned char won;

/* sound */
static unsigned char sfx_t, sfx_n_t;
static unsigned int sfx_f;
static signed char sfx_df;
static unsigned char sfx_vol;
static unsigned char mel_i, bass_i;
static unsigned char mel_t, bass_t, mel_v, bass_v;

/* scratch */
static unsigned char i, j, k, b, c;
static int x, y, t1, t2;
static unsigned int u;

/* ------------------------------------------------------------ utils */
static unsigned char rnd(void)
{
    rs ^= rs << 7;
    rs ^= rs >> 9;
    rs ^= rs << 8;
    return (unsigned char)rs;
}

static unsigned char get_block(int bx, int by)
{
    if (bx < 0 || bx >= WW || by >= WH) return B_BEDROCK;
    if (by < 0) return B_AIR;
    return vram_get(((unsigned int)by << 7) | bx);
}

static void set_block(int bx, int by, unsigned char t)
{
    if (bx < 0 || bx >= WW || by < 0 || by >= WH) return;
    vram_waddr(((unsigned int)by << 7) | bx);
    vram_put(TILEBASE + t);
}

static unsigned char is_solid(int bx, int by)
{
    return b_solid[get_block(bx, by)];
}

/* any solid block overlapping the pixel box */
static unsigned char box_hit(int bx, int by, unsigned char w, unsigned char h)
{
    int cx0, cx1, cy0, cy1, cy;
    if (bx < 0 || bx + w > WW * 8) return 1;
    cx0 = bx >> 3; cx1 = (bx + w - 1) >> 3;
    cy0 = by >> 3; cy1 = (by + h - 1) >> 3;
    for (cy = cy0; cy <= cy1; ++cy) {
        if (is_solid(cx0, cy)) return 1;
        if (cx1 != cx0 && is_solid(cx1, cy)) return 1;
    }
    return 0;
}

/* ------------------------------------------------------------ sound */
static void psg_wave(unsigned char ch, const unsigned char *w)
{
    PSG(0) = ch;
    PSG(4) = 0x40;
    PSG(4) = 0x00;
    for (i = 0; i < 32; ++i) PSG(6) = w[i];
    PSG(5) = 0xFF;
}

static const unsigned char wave_sq[32] = {
    31,31,31,31,31,31,31,31,31,31,31,31,31,31,31,31,
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 };
static const unsigned char wave_sin[32] = {
    16,19,22,24,27,29,30,31,31,31,30,29,27,24,22,19,
    16,12,9,7,4,2,1,0,0,0,1,2,4,7,9,12 };
static const unsigned char wave_tri[32] = {
    0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30,
    31,29,27,25,23,21,19,17,15,13,11,9,7,5,3,1 };

static void sound_init(void)
{
    PSG(1) = 0xFF;
    psg_wave(0, wave_sq);
    psg_wave(1, wave_sin);
    psg_wave(2, wave_tri);
    psg_wave(5, wave_sq);
}

static void sfx(unsigned int f, signed char df, unsigned char len, unsigned char vol)
{
    sfx_f = f; sfx_df = df; sfx_t = len; sfx_vol = vol;
}

static void noise(unsigned char len)
{
    sfx_n_t = len;
}

static void sound_update(void)
{
    /* sfx tone on channel 0 */
    PSG(0) = 0;
    if (sfx_t) {
        --sfx_t;
        sfx_f += sfx_df;
        PSG(2) = (unsigned char)sfx_f;
        PSG(3) = (unsigned char)(sfx_f >> 8) & 0x0F;
        PSG(4) = 0x80 | (sfx_t > 3 ? sfx_vol : sfx_vol - 8);
    } else {
        PSG(4) = 0;
    }
    /* noise on channel 5 */
    PSG(0) = 5;
    if (sfx_n_t) {
        --sfx_n_t;
        PSG(7) = 0x80 | 0x18 | (sfx_n_t & 3);
        PSG(4) = 0x80 | (sfx_n_t > 15 ? 26 : 10 + sfx_n_t);
    } else {
        PSG(7) = 0;
        PSG(4) = 0;
    }
    /* music: melody ch1, bass ch2 */
    if (state == ST_MENU) return;
    PSG(0) = 1;
    if (mel_t == 0) {
        if (song_mel[mel_i] == 0xFFFF) mel_i = 0;
        u = song_mel[mel_i];
        mel_t = (unsigned char)song_mel[mel_i + 1];
        mel_i += 2;
        if (u) {
            PSG(2) = (unsigned char)u;
            PSG(3) = (unsigned char)(u >> 8);
            mel_v = 22;
        } else {
            mel_v = 0;
        }
    }
    --mel_t;
    if (mel_v && (frame & 3) == 0 && mel_v > 10) --mel_v;
    PSG(4) = mel_v ? 0x80 | mel_v : 0;
    PSG(0) = 2;
    if (bass_t == 0) {
        if (song_bass[bass_i] == 0xFFFF) bass_i = 0;
        u = song_bass[bass_i];
        bass_t = (unsigned char)song_bass[bass_i + 1];
        bass_i += 2;
        if (u) {
            PSG(2) = (unsigned char)u;
            PSG(3) = (unsigned char)(u >> 8);
            bass_v = 20;
        } else {
            bass_v = 0;
        }
    }
    --bass_t;
    if (bass_v && (frame & 7) == 0 && bass_v > 8) --bass_v;
    PSG(4) = bass_v ? 0x80 | bass_v : 0;
}

static void music_mute(void)
{
    PSG(0) = 1; PSG(4) = 0;
    PSG(0) = 2; PSG(4) = 0;
}

/* ------------------------------------------------------------ video */
static void load_palette(unsigned int dst, unsigned char src)
{
    for (i = 0; i < 16; ++i) {
        vce_idx = dst + i;
        vce_color(palettes[src + i]);
    }
}

static void sky(unsigned int col)
{
    vce_idx = 0;
    vce_color(col);
}

static void video_init(void)
{
    /* height first: the emulator sizes its frame buffer from it */
    VDC(VDC_VDR, 0x0086);           /* 135 lines */
    VDC(VDC_CR, 0x0000);
    VDC(VDC_VSR, 0x3B02);
    VDC(VDC_VCR, 0x0030);
    VDC(VDC_HSR, 0x0302);
    VDC(VDC_HDR, 0x051D);           /* 30 tiles = 240 px */
    VDC(VDC_MWR, 0x0070);           /* BAT 128 x 64 */
    VDC(VDC_BXR, 0);
    VDC(VDC_BYR, 0);
    VDC(VDC_DCR, 0x0010);           /* auto SATB DMA every frame */
    VDC(VDC_SATB, SATB_ADDR);
    VCE_CTRL = 0;                   /* 5.37 MHz dot clock */

    /* BG tiles at VRAM $2000 (tile $200) */
    vram_waddr(TILEBASE * 16);
    copy_len = BG_TILES_WORDS;
    vram_copy(bg_tiles);
    /* sprite patterns at VRAM $4000 */
    vram_waddr(SPRBASE * 32);
    copy_len = sizeof(spr_data);
    vram_copy_spr(spr_data);
    /* clear SATB */
    vram_waddr(SATB_ADDR);
    copy_len = 256;
    vram_fill(0);

    load_palette(0, PAL_DAY);
    load_palette(32, PAL_FONT);
    load_palette(48, PAL_FONT + 16);
    load_palette(64, PAL_FONT + 32);
    load_palette(256, PAL_SPR0);
    load_palette(272, PAL_SPR1);
    sky(SKY_DAY);
}

static void spr_set(unsigned char n, int sx, int sy, unsigned char pat, unsigned int attr)
{
    unsigned int *s = &sat[n << 2];
    if (sx < -15 || sx >= SCR_W || sy < -15 || sy >= SCR_H) {
        s[0] = 0;
        return;
    }
    s[0] = sy + 64;
    s[1] = sx + 32;
    s[2] = SPRBASE + (pat << 1);
    s[3] = attr | 0x0080;
}

static void spr_hide_all(void)
{
    for (i = 0; i < NSAT; ++i) sat[i << 2] = 0;
}

static void spr_upload(void)
{
    vram_waddr(SATB_ADDR);
    copy_len = NSAT * 4;
    vram_copy(sat);
}

static void frame_wait(void)
{
    vsync();
    VDC(VDC_BXR, scroll_x);
    VDC(VDC_BYR, scroll_y);
    joyold = joy;
    joy = joy_read();
    joynew = joy & ~joyold;
    sound_update();
    ++frame;
}

/* ------------------------------------------------------------ UI text (BAT rows 47-63) */
static void ui_at(unsigned char col, unsigned char row)
{
    vram_waddr(((unsigned int)(UIROW + row) << 7) | col);
}

static void ui_text(unsigned char col, unsigned char row, const char *s, unsigned char pal)
{
    ui_at(col, row);
    u = ((unsigned int)pal << 12) + TILEBASE + T_FONT - 32;
    while (*s) {
        vram_put(u + (unsigned char)*s);
        ++s;
    }
}

static void ui_num(unsigned char col, unsigned char row, unsigned char n, unsigned char pal)
{
    static char buf[4];
    if (n >= 10) {
        buf[0] = '0' + n / 10;
        buf[1] = '0' + n % 10;
        buf[2] = 0;
    } else {
        buf[0] = '0' + n;
        buf[1] = 0;
    }
    ui_text(col, row, buf, pal);
}

static void ui_tile(unsigned char col, unsigned char row, unsigned char t)
{
    ui_at(col, row);
    vram_put(TILEBASE + t);
}

static void ui_clear(unsigned char t, unsigned char pal)
{
    vram_waddr((unsigned int)UIROW << 7);
    copy_len = (64 - UIROW) * 128;
    vram_fill(((unsigned int)pal << 12) + TILEBASE + t);
}

static void ui_show(unsigned int bg)
{
    scroll_x = 0;
    scroll_y = UIROW * 8;
    spr_hide_all();
    spr_upload();
    sky(bg);
}

/* ------------------------------------------------------------ world generation */
static void gen_world(void)
{
    unsigned char h, depth, desert0, r;
    h = 20;
    desert0 = 8 + (rnd() & 63);
    if (desert0 > 50 && desert0 < 78) desert0 += 30;   /* keep spawn area green */

    /* columns, written with VRAM increment 128 */
    VDC(VDC_CR, CR_ON | 0x1800);
    for (x = 0; x < WW; ++x) {
        if ((x & 3) == 0) {
            r = rnd();
            if (r < 70 && h > 13) --h;
            else if (r > 185 && h < 25) ++h;
        }
        surf[x] = h;
        depth = 3 + (rnd() & 1);
        c = (x >= desert0 && x < desert0 + 16);
        vram_waddr(x);
        for (y = 0; y < WH; ++y) {
            if (y < h) b = B_AIR;
            else if (y == h) b = c ? B_SAND : B_GRASS;
            else if (y < h + depth) b = c ? B_SAND : B_DIRT;
            else if (y == WH - 1 || (y == WH - 2 && (rnd() & 1))) b = B_BEDROCK;
            else {
                r = rnd();
                t1 = y - h;
                if (y >= 37 && r < 5) b = B_DIAMOND;
                else if (y >= 31 && r < 10) b = B_GOLD;
                else if (t1 > 5 && r < 22) b = B_IRON;
                else if (t1 > 1 && r < 38) b = B_COAL;
                else b = B_STONE;
            }
            vram_put(TILEBASE + b);
        }
    }
    VDC(VDC_CR, CR_ON);

    /* caves (random worms) */
    for (k = 0; k < 14; ++k) {
        x = rnd() & 127;
        if (k < 2) y = surf[x] + 1;
        else y = surf[x] + 6 + (rnd() & 15);
        t1 = (rnd() & 1) ? 1 : -1;
        t2 = 0;
        for (j = 0; j < 45; ++j) {
            for (i = 0; i < 4; ++i) {
                c = get_block(x + (i & 1), y + (i >> 1));
                if (c != B_BEDROCK && c != B_AIR && y + (i >> 1) > surf[x]) {
                    set_block(x + (i & 1), y + (i >> 1), B_CAVE);
                }
            }
            r = rnd();
            if (r < 30) t1 = -t1;
            if (r > 200) t2 = 1;
            else if (r > 150) t2 = -1;
            else if (r > 100) t2 = 0;
            x += t1;
            y += t2;
            if (y < 4) y = 4;
            if (y > WH - 4) y = WH - 4;
            if (x < 1 || x > WW - 3) break;
        }
    }

    /* trees and flowers */
    t2 = -10;
    for (x = 3; x < WW - 3; ++x) {
        h = surf[x];
        if (get_block(x, h) != B_GRASS) continue;
        r = rnd();
        if (r < 60 && x - t2 >= 5) {
            t2 = x;
            depth = 3 + (rnd() & 1);
            for (i = 1; i <= depth; ++i) set_block(x, h - i, B_LOG);
            y = h - depth;
            for (t1 = -2; t1 <= 0; ++t1) {
                for (i = 0; i < 5; ++i) {
                    if (t1 == -2 && (i == 0 || i == 4)) continue;
                    if (get_block(x - 2 + i, y + t1) == B_AIR)
                        set_block(x - 2 + i, y + t1, B_LEAVES);
                }
            }
            set_block(x, y - 3, B_LEAVES);
        } else if (r > 225) {
            if (get_block(x, h - 1) == B_AIR) set_block(x, h - 1, B_FLOWER);
        }
    }

    spawn_x = (64 * 8 + 1) << 4;
    spawn_y = ((int)surf[64] * 8 - PH) << 4;
}

/* ------------------------------------------------------------ physics */
static void move_entity(void)
{
    eblocked = 0;
    if (evx) {
        t1 = ex + evx;
        x = t1 >> 4;
        if (box_hit(x, ey >> 4, PW, PH)) {
            eblocked = 1;
            if (evx > 0) ex = ((((x + PW - 1) >> 3) << 3) - PW) << 4;
            else ex = (((x >> 3) + 1) << 3) << 4;
        } else {
            ex = t1;
        }
    }
    evy += GRAV;
    if (evy > MAXFALL) evy = MAXFALL;
    t1 = ey + evy;
    y = evy > 0 ? (t1 + 15) >> 4 : t1 >> 4;   /* any sub-pixel step down touches */
    if (box_hit(ex >> 4, y, PW, PH)) {
        if (evy > 0) {
            ey = ((((y + PH - 1) >> 3) << 3) - PH) << 4;
            eground = 1;
        } else {
            ey = (((y >> 3) + 1) << 3) << 4;
        }
        evy = 0;
    } else {
        ey = t1;
        eground = 0;
    }
}

static unsigned char overlap(int ax, int ay, unsigned char aw, unsigned char ah,
                             int bx, int by, unsigned char bw, unsigned char bh)
{
    return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
}

static void hurt(unsigned char dmg, signed char kb)
{
    if (invuln) return;
    hp = hp > dmg ? hp - dmg : 0;
    invuln = 60;
    kbx = kb;
    pvy = -18;
    sfx(300, 12, 12, 30);
}

/* ------------------------------------------------------------ TNT */
static void ignite(unsigned char bx, unsigned char by, unsigned char fuse)
{
    for (k = 0; k < NTNT; ++k) {
        if (!tnt_t[k]) {
            tnt_x[k] = bx; tnt_y[k] = by; tnt_t[k] = fuse;
            set_block(bx, by, B_TNTLIT);
            sfx(900, -20, 20, 24);
            return;
        }
    }
}

static void explode(unsigned char bx, unsigned char by)
{
    signed char dx, dy;
    unsigned char ad;
    for (dy = -2; dy <= 2; ++dy) {
        for (dx = -2; dx <= 2; ++dx) {
            ad = (dx < 0 ? -dx : dx) + (dy < 0 ? -dy : dy);
            if (ad > 3) continue;
            x = bx + dx; y = by + dy;
            c = get_block(x, y);
            if (c == B_BEDROCK || c == B_AIR || c == B_CAVE) continue;
            if (c == B_TNT) { ignite(x, y, 8); continue; }
            if (c == B_TNTLIT) continue;
            set_block(x, y, (y > surf[x]) ? B_CAVE : B_AIR);
        }
    }
    set_block(bx, by, (by > surf[bx]) ? B_CAVE : B_AIR);
    noise(40);
    shake = 20;
    /* damage */
    x = (px >> 4) + 3 - ((int)bx * 8 + 4);
    y = (py >> 4) + 7 - ((int)by * 8 + 4);
    if (x > -28 && x < 28 && y > -28 && y < 28) {
        invuln = 0;
        hurt(6, x < 0 ? -40 : 40);
    }
    for (k = 0; k < NZ; ++k) {
        if (!zon[k]) continue;
        x = (zx[k] >> 4) - (int)bx * 8;
        y = (zy[k] >> 4) - (int)by * 8;
        if (x > -32 && x < 32 && y > -32 && y < 32) zon[k] = 0;
    }
}

static void tnt_update(void)
{
    unsigned char n;
    for (n = 0; n < NTNT; ++n) {
        if (!tnt_t[n]) continue;
        --tnt_t[n];
        if (tnt_t[n] == 0) {
            explode(tnt_x[n], tnt_y[n]);
        } else if ((tnt_t[n] & 7) == 0) {
            set_block(tnt_x[n], tnt_y[n], (tnt_t[n] & 8) ? B_TNTLIT : B_TNT);
        }
    }
}

/* ------------------------------------------------------------ zombies */
static void zombies_update(void)
{
    unsigned char n;
    int pcx = px >> 4;
    for (n = 0; n < NZ; ++n) {
        if (!zon[n]) continue;
        ex = zx[n]; ey = zy[n]; evy = zvy[n]; eground = zground[n];
        if (zkb[n]) {
            evx = zkb[n];
            zkb[n] = zkb[n] > 0 ? zkb[n] - 4 : zkb[n] + 4;
            if (zkb[n] > -4 && zkb[n] < 4) zkb[n] = 0;
        } else {
            x = (ex >> 4) - pcx;
            if (x > 4) { evx = -ZWALK; zface[n] = 1; }
            else if (x < -4) { evx = ZWALK; zface[n] = 0; }
            else if ((ey >> 4) - (py >> 4) > 6 || (py >> 4) - (ey >> 4) > 6)
                evx = zface[n] ? -ZWALK : ZWALK;   /* keep going, drop down */
            else evx = 0;
        }
        move_entity();
        if (eblocked && eground) evy = -JUMPV;
        zx[n] = ex; zy[n] = ey; zvy[n] = evy; zground[n] = eground;
        if (zhurt[n]) --zhurt[n];

        /* sunlight burns zombies standing in the open */
        if (phase != 2 && (frame & 31) == 0) {
            c = (ex >> 7) & 127;
            if ((ey >> 7) <= surf[c]) {
                if (zhp[n]) --zhp[n];
                zhurt[n] = 8;
                if (!zhp[n]) zon[n] = 0;
            }
        }
        /* despawn when far away */
        x = (ex >> 4) - pcx;
        if (x > 360 || x < -360) zon[n] = 0;

        /* touch damage */
        if (zon[n] && overlap(ex >> 4, ey >> 4, PW, PH, px >> 4, py >> 4, PW, PH)) {
            hurt(2, (ex < px) ? 40 : -40);
        }
    }

    /* spawning at night */
    if (phase == 2) {
        if (++zspawn_t >= 240) {
            zspawn_t = 0;
            for (n = 0; n < NZ; ++n) {
                if (zon[n]) continue;
                x = (px >> 7) + ((rnd() & 1) ? 20 : -20) + (rnd() & 7) - 3;
                if (x < 1 || x > WW - 2) break;
                y = surf[x];
                while (y > 1 && is_solid(x, y - 1)) --y;
                zx[n] = ((x << 3) + 1) << 4;
                zy[n] = ((y << 3) - PH) << 4;
                zvy[n] = 0; zhp[n] = 4; zon[n] = 1; zkb[n] = 0; zhurt[n] = 0;
                break;
            }
        }
    }
}

static unsigned char attack(void)
{
    unsigned char n;
    int ax = facing ? (px >> 4) - 12 : (px >> 4) + PW;
    for (n = 0; n < NZ; ++n) {
        if (!zon[n]) continue;
        if (overlap(ax, (py >> 4), 12, PH, zx[n] >> 4, zy[n] >> 4, PW, PH)) {
            c = inv[I_SWORD] ? 3 : 1;
            zhp[n] = zhp[n] > c ? zhp[n] - c : 0;
            zhurt[n] = 10;
            zkb[n] = facing ? -40 : 40;
            zvy[n] = -16;
            sfx(200, 8, 8, 28);
            if (!zhp[n]) {
                zon[n] = 0;
                sfx(500, -15, 20, 28);
            }
            return 1;
        }
    }
    return 0;
}

/* ------------------------------------------------------------ player */
static void update_tier(void)
{
    tier = inv[I_DPICK] ? 4 : inv[I_IPICK] ? 3 : inv[I_SPICK] ? 2 : inv[I_WPICK] ? 1 : 0;
}

static unsigned char add_item(unsigned char it, unsigned char n)
{
    if (it == NONE) return 0;
    if (inv[it] + n > 99) n = 99 - inv[it];
    inv[it] += n;
    return n;
}

static void compute_target(void)
{
    int cx = ((px >> 4) + 3) >> 3;
    int feet = ((py >> 4) + PH - 1) >> 3;
    int head = feet - 1;
    int front = facing ? cx - 1 : cx + 1;
    unsigned char lr = joy & (J_LEFT | J_RIGHT);

    if (joy & J_UP) {
        if (lr) { tx = front; ty = head; }
        else { tx = cx; ty = head - 1; }
    } else if (joy & J_DOWN) {
        if (lr) { tx = front; ty = feet + 1; }
        else { tx = cx; ty = feet + 1; }
    } else {
        tx = front; ty = feet;
        c = get_block(front, feet);
        if (c == B_AIR || c == B_CAVE) {
            c = get_block(front, head);
            if (c != B_AIR && c != B_CAVE) ty = head;
        }
    }
    tblock = get_block(tx, ty);
}

static unsigned char mine_needed(void)
{
    b = b_hard[tblock];
    if (b_stone[tblock]) {
        u = (unsigned int)b * 3 / (tier + 2);
        b = (unsigned char)u;
    }
    return b ? b : 1;
}

static void break_block(void)
{
    c = b_drop[tblock];
    if (tblock == B_LEAVES) {
        if (rnd() < 50) c = I_APPLE;
    }
    if (c != NONE) add_item(c, 1);
    set_block(tx, ty, (ty > surf[tx]) ? B_CAVE : B_AIR);
    sfx(700, 30, 6, 26);
    noise(4);
}

static void player_mine(void)
{
    if (!(joy & J_II)) { mx = -1; mtime = 0; return; }
    if ((joynew & J_II) && attack()) { mx = -1; return; }
    if (tblock == B_AIR || tblock == B_CAVE || tblock == B_BEDROCK || tblock == B_TNTLIT) {
        mx = -1; mtime = 0; return;
    }
    if (tblock == B_TNT) {
        if (joynew & J_II) ignite(tx, ty, 120);
        return;
    }
    if (b_tier[tblock] > tier) {
        mx = -1; mtime = 0;
        if (joynew & J_II) sfx(120, 0, 6, 22);
        return;
    }
    if (mx != tx || my != ty) { mx = tx; my = ty; mtime = 0; }
    ++mtime;
    if (++msound >= 7) { msound = 0; noise(3); }
    if (mtime >= mine_needed()) {
        break_block();
        mx = -1; mtime = 0;
    }
}

static void player_place(void)
{
    if (!(joynew & J_RUN)) return;
    if (hand == I_APPLE && inv[I_APPLE]) {
        if (hp < 10) {
            --inv[I_APPLE];
            hp = hp + 4 > 10 ? 10 : hp + 4;
            sfx(400, 20, 15, 26);
        }
        return;
    }
    b = it_block[hand];
    if (b == NONE || !inv[hand]) return;
    if (tblock != B_AIR && tblock != B_CAVE) return;
    if (ty < 0 || ty >= WH) return;
    if (b_solid[b]) {
        if (overlap(tx << 3, ty << 3, 8, 8, px >> 4, py >> 4, PW, PH)) return;
        for (k = 0; k < NZ; ++k)
            if (zon[k] && overlap(tx << 3, ty << 3, 8, 8, zx[k] >> 4, zy[k] >> 4, PW, PH)) return;
    } else if (!is_solid(tx, ty + 1)) {
        return;
    }
    set_block(tx, ty, b);
    --inv[hand];
    sfx(250, -10, 6, 26);
}

static void player_update(void)
{
    ex = px; ey = py; evy = pvy; eground = ground;
    evx = 0;
    if (joy & J_LEFT) { evx = -WALK; facing = 1; }
    if (joy & J_RIGHT) { evx = WALK; facing = 0; }
    if (kbx) {
        evx = kbx;
        kbx = kbx > 0 ? kbx - 4 : kbx + 4;
        if (kbx > -4 && kbx < 4) kbx = 0;
    }
    if ((joy & J_I) && eground) {
        evy = -JUMPV;
        sfx(500, -6, 4, 16);
    }
    t2 = eground;
    move_entity();
    if (!eground) {
        if (t2 || ey < peak) peak = ey;
    } else if (!t2) {
        /* landed: fall damage above 3.5 blocks */
        x = (ey - peak) >> 4;
        if (x > 28) {
            invuln = 0;
            hurt((unsigned char)((x - 20) >> 3), 0);
            pvy = 0; evy = 0;
        }
        peak = ey;
    }
    px = ex; py = ey; pvy = evy; ground = eground;
    if (evx && eground) ++anim; else anim = 0;
    if (invuln) --invuln;

    compute_target();
    player_mine();
    player_place();
}

/* ------------------------------------------------------------ day / night */
static void daynight(void)
{
    unsigned char p;
    if (++tod >= DAYLEN) tod = 0;
    p = tod < T_DUSK ? 0 : tod < T_NIGHT ? 1 : tod < T_DAWN ? 2 : 3;
    if (p == phase) return;
    phase = p;
    load_palette(0, p == 2 ? PAL_NIGHT : PAL_DAY);
    sky(p == 0 ? SKY_DAY : p == 1 ? SKY_DUSK : p == 2 ? SKY_NIGHT : SKY_DAWN);
}

static void restore_sky(void)
{
    c = phase;
    if (c == 2) { load_palette(0, PAL_NIGHT); sky(SKY_NIGHT); }
    else { load_palette(0, PAL_DAY); sky(c == 0 ? SKY_DAY : c == 1 ? SKY_DUSK : SKY_DAWN); }
}

/* ------------------------------------------------------------ rendering */
static void camera(void)
{
    camx = (px >> 4) + 3 - SCR_W / 2;
    camy = (py >> 4) + 7 - SCR_H / 2;
    if (camx < 0) camx = 0;
    if (camx > WW * 8 - SCR_W) camx = WW * 8 - SCR_W;
    if (camy < 0) camy = 0;
    if (camy > WH * 8 - SCR_H) camy = WH * 8 - SCR_H;
    scroll_x = camx;
    scroll_y = camy;
    if (shake) {
        --shake;
        scroll_x += (rnd() & 3) - 1;
        scroll_y += (rnd() & 3) - 1;
    }
}

static void draw_sprites(void)
{
    unsigned char n, s;
    /* hearts (0-4) */
    for (n = 0; n < 5; ++n) {
        s = hp >= (n << 1) + 2 ? S_HFULL : hp == (n << 1) + 1 ? S_HHALF : S_HEMPTY;
        spr_set(n, 2 + n * 8, 2, s, 0);
    }
    /* held item + count (5-7) */
    spr_set(5, 214, 2, it_spr[hand], 0);
    c = inv[hand];
    if (c >= 10) {
        spr_set(6, 223, 2, S_DIG0 + c / 10, 0);
        spr_set(7, 229, 2, S_DIG0 + c % 10, 0);
    } else {
        spr_set(6, 223, 2, S_DIG0 + c, 0);
        sat[7 << 2] = 0;
    }
    /* cursor (8) */
    if (mx >= 0 && mtime) {
        s = S_CURSOR + 1 + (unsigned char)((unsigned int)mtime * 3 / mine_needed());
        if (s > S_CUR3) s = S_CUR3;
    } else {
        s = S_CURSOR;
    }
    spr_set(8, (tx << 3) - camx, (ty << 3) - camy, s, 0);
    /* player (9) */
    if (invuln & 2) sat[9 << 2] = 0;
    else spr_set(9, (px >> 4) - 5 - camx, (py >> 4) - 2 - camy,
                 S_STEVE0 + (anim ? 1 + ((anim >> 3) & 1) : 0), 1 | (facing ? 0x0800 : 0));
    /* zombies (10-12) */
    for (n = 0; n < NZ; ++n) {
        if (!zon[n] || (zhurt[n] & 2)) { sat[(10 + n) << 2] = 0; continue; }
        spr_set(10 + n, (zx[n] >> 4) - 5 - camx, (zy[n] >> 4) - 2 - camy,
                S_ZOMB1 + ((frame >> 4) & 1), 1 | (zface[n] ? 0x0800 : 0));
    }
}

/* ------------------------------------------------------------ menu */
static unsigned char near_table(void)
{
    int cx = ((px >> 4) + 3) >> 3;
    int cy = ((py >> 4) + 7) >> 3;
    for (y = cy - 3; y <= cy + 3; ++y)
        for (x = cx - 4; x <= cx + 4; ++x)
            if (get_block(x, y) == B_TABLE) return 1;
    return 0;
}

static unsigned char can_craft(unsigned char r)
{
    const recipe_t *rc = &recipes[r];
    if (rc->out >= NSTACK && inv[rc->out]) return 0;
    if (inv[rc->out] + rc->outn > 99) return 0;
    if (inv[rc->a] < rc->an) return 0;
    if (rc->b != NONE && inv[rc->b] < rc->bn) return 0;
    if (rc->table && !near_table()) return 0;
    return 1;
}

static void menu_draw(void)
{
    unsigned char n, row, col, pal;
    const recipe_t *rc;
    ui_clear(0, 2);
    ui_text(1, 0, "EKWIPUNEK", tab == 0 ? 3 : 4);
    ui_text(12, 0, "TWORZENIE", tab == 1 ? 3 : 4);
    ui_text(23, 0, "POMOC", tab == 2 ? 3 : 4);

    if (tab == 0) {
        mcount = 0;
        for (n = 0; n < NSTACK; ++n)
            if (inv[n]) mlist[mcount++] = n;
        if (msel >= mcount) msel = mcount ? mcount - 1 : 0;
        if (!mcount) ui_text(2, 3, "PUSTO - KOP BLOKI!", 4);
        for (n = 0; n < mcount; ++n) {
            col = n < 8 ? 0 : 15;
            row = 2 + (n & 7);
            pal = (mlist[n] == hand) ? 3 : 2;
            if (n == msel) ui_text(col, row, ">", 3);
            ui_tile(col + 1, row, it_tile[mlist[n]]);
            ui_text(col + 3, row, it_name[mlist[n]], pal);
            ui_num(col + 11, row, inv[mlist[n]], pal);
        }
        ui_text(0, 11, "NARZEDZIA:", 4);
        col = 0;
        for (n = I_WPICK; n < NITEMS; ++n) {
            if (inv[n]) { ui_tile(1 + col, 12, it_tile[n]); col += 2; }
        }
        if (!col) ui_text(1, 12, "BRAK", 4);
        ui_text(0, 14, "W REKU:", 4);
        ui_tile(8, 14, it_tile[hand]);
        ui_text(10, 14, it_name[hand], 3);
        ui_text(0, 16, "K-WEZ 1-ZAKLADKA L-WYJDZ", 4);
    } else if (tab == 1) {
        if (msel >= NRECIPES) msel = NRECIPES - 1;
        for (n = 0; n < NRECIPES; ++n) {
            rc = &recipes[n];
            row = 1 + n;
            pal = can_craft(n) ? 2 : 4;
            if (n == msel) ui_text(0, row, ">", 3);
            ui_tile(1, row, it_tile[rc->out]);
            ui_text(3, row, it_name[rc->out], n == msel ? 3 : pal);
            if (rc->outn > 1) { ui_text(14, row, "*", pal); ui_num(15, row, rc->outn, pal); }
            ui_text(17, row, "=", pal);
            ui_num(19, row, rc->an, pal);
            ui_tile(20, row, it_tile[rc->a]);
            if (rc->b != NONE) {
                ui_num(22, row, rc->bn, pal);
                ui_tile(23, row, it_tile[rc->b]);
            }
            if (rc->table) ui_tile(26, row, B_TABLE);
        }
        ui_tile(0, 12, B_TABLE);
        ui_text(1, 12, "=WYMAGA STOLU OBOK", 4);
        if (mmsg) ui_text(1, 14, mmsg, 3);
        ui_text(0, 16, "K-STWORZ 1-ZAKLADKA L-WYJDZ", 4);
    } else {
        ui_text(1, 2, "CARDPUTER:", 3);
        ui_text(1, 3, "A/D  - RUCH", 2);
        ui_text(1, 4, "K    - SKOK", 2);
        ui_text(1, 5, "L    - KOP / ATAK", 2);
        ui_text(1, 6, "1    - POLOZ / ZJEDZ", 2);
        ui_text(1, 7, "2    - MENU", 2);
        ui_text(1, 8, "E/S  - CEL GORA/DOL", 2);
        ui_text(1, 10, "CEL: STWORZ KORONE", 3);
        ui_text(1, 11, "(5 DIAMENTOW + 5 ZLOTA)", 2);
        ui_text(1, 12, "NOCA UWAZAJ NA ZOMBIE!", 2);
        ui_text(1, 13, "JABLKO Z LISCI = +2 SERCA", 2);
        ui_text(1, 14, "LEPSZY KILOF = TWARDSZE RUDY", 2);
        ui_text(0, 16, "1-ZAKLADKA L-WYJDZ", 4);
    }
}

static void menu_open(void)
{
    state = ST_MENU;
    tab = 0; msel = 0; mmsg = 0;
    music_mute();
    ui_show(PANEL_COL);
    menu_draw();
}

static void camera(void);

static void menu_close(void)
{
    state = ST_PLAY;
    restore_sky();
    camera();
}

static void craft(void)
{
    const recipe_t *rc = &recipes[msel];
    if (!can_craft(msel)) {
        mmsg = (rc->table && !near_table()) ? "POTRZEBNY STOL!" : "BRAK SKLADNIKOW";
        sfx(120, 0, 8, 22);
        return;
    }
    inv[rc->a] -= rc->an;
    if (rc->b != NONE) inv[rc->b] -= rc->bn;
    inv[rc->out] += rc->outn;
    update_tier();
    mmsg = "STWORZONO!";
    sfx(600, 25, 10, 26);
    if (rc->out == I_CROWN && !won) won = 1;
}

static void menu_update(void)
{
    unsigned char redraw = 0;
    if (joynew & (J_II | J_SEL)) { menu_close(); return; }
    if (joynew & J_RUN) { tab = (tab + 1) % 3; msel = 0; mmsg = 0; redraw = 1; }
    if (tab == 0) {
        if ((joynew & J_DOWN) && msel + 1 < mcount) { ++msel; redraw = 1; }
        if ((joynew & J_UP) && msel) { --msel; redraw = 1; }
        if ((joynew & J_RIGHT) && msel + 8 < mcount) { msel += 8; redraw = 1; }
        if ((joynew & J_LEFT) && msel >= 8) { msel -= 8; redraw = 1; }
        if ((joynew & J_I) && mcount) { hand = mlist[msel]; redraw = 1; sfx(800, 0, 4, 22); }
    } else if (tab == 1) {
        if ((joynew & J_DOWN) && msel + 1 < NRECIPES) { ++msel; redraw = 1; mmsg = 0; }
        if ((joynew & J_UP) && msel) { --msel; redraw = 1; mmsg = 0; }
        if (joynew & J_I) { craft(); redraw = 1; }
    }
    if (redraw) menu_draw();
    if (won == 1) {
        won = 2;
        state = ST_WIN;
        ui_clear(0, 2);
        ui_tile(14, 3, T_CROWN);
        ui_text(9, 6, "WYGRALES!", 3);
        ui_text(3, 8, "KORONA KROLA KOPACZY", 2);
        ui_text(5, 9, "JEST TWOJA. BRAWO!", 2);
        ui_text(3, 13, "1 - GRAJ DALEJ", 2);
    }
}

/* ------------------------------------------------------------ title / death */
static void draw_logo_word(unsigned char first, unsigned char len, unsigned char col,
                           unsigned char row, unsigned char tile)
{
    unsigned char l, ry, bit;
    const unsigned char *gl;
    for (l = 0; l < len; ++l) {
        gl = logo_font[first + l];
        for (ry = 0; ry < 5; ++ry)
            for (bit = 0; bit < gl[0]; ++bit)
                if (gl[1 + ry] & (0x10 >> bit))
                    ui_tile(col + bit, row + ry, tile);
        col += gl[0] + 1;
    }
}

static void title_screen(void)
{
    ui_clear(B_AIR, 0);
    draw_logo_word(0, 4, 6, 1, B_GRASS);       /* MINE  */
    draw_logo_word(4, 5, 4, 7, B_COBBLE);      /* CRAFT */
    ui_text(3, 13, "EDYCJA CARDPUTER 240X135", 2);
    for (i = 0; i < 30; ++i) ui_tile(i, 16, B_GRASS);
    ui_show(SKY_DAY);
    for (;;) {
        frame_wait();
        if (frame & 32) {
            ui_text(6, 15, "NACISNIJ 1 = START", 3);
        } else {
            ui_at(6, 15);
            for (i = 0; i < 18; ++i) vram_put(TILEBASE + B_AIR);
        }
        if (joynew & (J_RUN | J_I)) break;
    }
    rs ^= frame | 1;
}

static void respawn(void)
{
    px = spawn_x; py = spawn_y; pvy = 0; kbx = 0;
    hp = 10; invuln = 90; peak = py; ground = 0;
    for (k = 0; k < NZ; ++k) zon[k] = 0;
}

/* ------------------------------------------------------------ main */
void main(void)
{
    video_init();
    sound_init();
    VDC(VDC_CR, CR_ON);

    title_screen();

    ui_clear(0, 2);
    ui_text(5, 8, "GENEROWANIE SWIATA...", 2);
    ui_show(PANEL_COL);
    frame_wait();
    gen_world();

    for (i = 0; i < NITEMS; ++i) inv[i] = 0;
    hand = I_DIRT;
    tier = 0;
    tod = 0;
    phase = 0xFF;
    respawn();
    invuln = 0;
    state = ST_PLAY;
    daynight();

    for (;;) {
        frame_wait();
        switch (state) {
        case ST_PLAY:
            daynight();
            player_update();
            zombies_update();
            tnt_update();
            camera();
            draw_sprites();
            spr_upload();
            if (joynew & J_SEL) menu_open();
            else if (hp == 0) {
                state = ST_DEAD;
                music_mute();
                ui_clear(0, 2);
                ui_text(10, 6, "ZGINALES!", 3);
                ui_text(4, 9, "EKWIPUNEK ZOSTAJE Z TOBA", 2);
                ui_text(6, 12, "1 - ODRODZENIE", 2);
                ui_show(PANEL_COL);
                sfx(400, -10, 40, 30);
            }
            break;
        case ST_MENU:
            menu_update();
            break;
        case ST_DEAD:
            if (joynew & (J_RUN | J_I)) {
                respawn();
                state = ST_PLAY;
                restore_sky();
                camera();
            }
            break;
        case ST_WIN:
            if (joynew & (J_RUN | J_I)) menu_close();
            break;
        }
    }
}
