#ifndef HW_H
#define HW_H

/* VDC registers */
#define VDC_MAWR 0
#define VDC_MARR 1
#define VDC_VWR  2
#define VDC_CR   5
#define VDC_BXR  7
#define VDC_BYR  8
#define VDC_MWR  9
#define VDC_HSR  10
#define VDC_HDR  11
#define VDC_VSR  12
#define VDC_VDR  13
#define VDC_VCR  14
#define VDC_DCR  15
#define VDC_SATB 19

/* joypad bits (as returned by joy_read) */
#define J_I      0x01
#define J_II     0x02
#define J_SEL    0x04
#define J_RUN    0x08
#define J_UP     0x10
#define J_RIGHT  0x20
#define J_DOWN   0x40
#define J_LEFT   0x80

#define VCE_CTRL (*(volatile unsigned char *)0x0400)
#define PSG(r)   (*(volatile unsigned char *)(0x0800 + (r)))

extern unsigned char vreg_sel;
extern unsigned int copy_len;
extern unsigned int vce_idx;

void __fastcall__ vdc_reg(unsigned int val);
void __fastcall__ vram_waddr(unsigned int addr);
void __fastcall__ vram_put(unsigned int w);
unsigned char __fastcall__ vram_get(unsigned int addr);
void __fastcall__ vram_copy(const void *src);
void __fastcall__ vram_copy_spr(const void *src);
void __fastcall__ vram_fill(unsigned int w);
void __fastcall__ vce_color(unsigned int color);
unsigned char joy_read(void);

#define VDC(r, v) do { vreg_sel = (r); vdc_reg(v); } while (0)

#endif
