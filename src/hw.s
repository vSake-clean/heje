; Low-level PC Engine hardware helpers (HuC6280 / HuC6270 VDC / HuC6260 VCE)
; VDC ports are at $0000-$0003; accesses use the a: prefix so the assembler
; never turns them into zero-page ones (zero page lives at $2000 on this CPU).

        .setcpu "HuC6280"
        .include "zeropage.inc"

VDC_SEL  = $0000
VDC_LO   = $0002
VDC_HI   = $0003
VCE_ALO  = $0402
VCE_AHI  = $0403
VCE_DLO  = $0404
VCE_DHI  = $0405
JOYPORT  = $1000

        .export _vreg_sel, _copy_len, _vce_idx
        .export _vdc_reg, _vram_waddr, _vram_put, _vram_get, _vram_copy
        .export _vram_copy_spr, _vram_fill, _vce_color, _joy_read

.bss
_vreg_sel:  .res 1
_copy_len:  .res 2
_vce_idx:   .res 2

.code

; void __fastcall__ vdc_reg(unsigned val)   -- register number in vreg_sel
_vdc_reg:
        ldy     _vreg_sel
        sty     a:VDC_SEL
        sta     a:VDC_LO
        stx     a:VDC_HI
        rts

; void __fastcall__ vram_waddr(unsigned addr)
_vram_waddr:
        st0     #$00
        sta     a:VDC_LO
        stx     a:VDC_HI
        st0     #$02
        rts

; void __fastcall__ vram_put(unsigned w)   (VWR must be selected)
_vram_put:
        sta     a:VDC_LO
        stx     a:VDC_HI
        rts

; unsigned char __fastcall__ vram_get(unsigned addr)  -- returns low byte
_vram_get:
        st0     #$01
        sta     a:VDC_LO
        stx     a:VDC_HI
        st0     #$02
        lda     a:VDC_LO
        ldx     a:VDC_HI
        ldx     #0
        rts

; void __fastcall__ vram_copy(const void *src)  -- copy_len words
_vram_copy:
        sta     ptr1
        stx     ptr1+1
        ldy     #0
@loop:  lda     _copy_len
        ora     _copy_len+1
        beq     @done
        lda     (ptr1),y
        sta     a:VDC_LO
        iny
        lda     (ptr1),y
        sta     a:VDC_HI
        iny
        bne     @nc
        inc     ptr1+1
@nc:    lda     _copy_len
        bne     @dl
        dec     _copy_len+1
@dl:    dec     _copy_len
        bra     @loop
@done:  rts

; void __fastcall__ vram_copy_spr(const void *src)
; expands copy_len bytes into words with the byte in the high half
; (left 8 pixels of a 16 pixel sprite row)
_vram_copy_spr:
        sta     ptr1
        stx     ptr1+1
        ldy     #0
@loop:  lda     _copy_len
        ora     _copy_len+1
        beq     @done
        st1     #$00
        lda     (ptr1),y
        sta     a:VDC_HI
        iny
        bne     @nc
        inc     ptr1+1
@nc:    lda     _copy_len
        bne     @dl
        dec     _copy_len+1
@dl:    dec     _copy_len
        bra     @loop
@done:  rts

; void __fastcall__ vram_fill(unsigned w)  -- copy_len times
_vram_fill:
        sta     tmp1
        stx     tmp2
@loop:  lda     _copy_len
        ora     _copy_len+1
        beq     @done
        lda     tmp1
        sta     a:VDC_LO
        lda     tmp2
        sta     a:VDC_HI
        lda     _copy_len
        bne     @dl
        dec     _copy_len+1
@dl:    dec     _copy_len
        bra     @loop
@done:  rts

; void __fastcall__ vce_color(unsigned color)  -- palette entry vce_idx
_vce_color:
        ldy     _vce_idx
        sty     VCE_ALO
        ldy     _vce_idx+1
        sty     VCE_AHI
        sta     VCE_DLO
        stx     VCE_DHI
        rts

; unsigned char joy_read(void)  -- pad 1, bits: I II SEL RUN U R D L
_joy_read:
        lda     #$01
        sta     JOYPORT
        lda     #$03
        sta     JOYPORT
        lda     #$01
        sta     JOYPORT
        pha
        pla
        nop
        nop
        lda     JOYPORT
        asl     a
        asl     a
        asl     a
        asl     a
        sta     tmp1
        lda     #$00
        sta     JOYPORT
        pha
        pla
        nop
        nop
        lda     JOYPORT
        and     #$0F
        ora     tmp1
        eor     #$FF
        ldx     #0
        rts
