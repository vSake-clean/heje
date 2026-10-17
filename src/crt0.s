; Start-up code (replaces cc65's PC Engine crt0 so the VDC is accessed at
; $0000-$0003 directly; some emulators do not mirror it at $0200).

        .setcpu "HuC6280"
        .export         _exit, _vsync
        .export         __STARTUP__ : absolute = 1
        .exportzp       _vbl_count
        .import         initlib, donelib, push0, _main
        .import         __DATA_LOAD__, __DATA_RUN__, __DATA_SIZE__
        .import         __BSS_RUN__, __BSS_SIZE__
        .import         __MAIN_START__, __MAIN_SIZE__, __STACKSIZE__
        .include        "zeropage.inc"

.zeropage
_vbl_count:     .res 1

.segment "STARTUP"
start:  sei
        csh                     ; 7.16 MHz
        cld
        ldx     #$FF
        txs
        lda     #$FF
        tam     #%00000001      ; $0000 = I/O
        lda     #$F8
        tam     #%00000010      ; $2000 = RAM
        lda     #$01
        tam     #%00010000      ; $8000 = bank 1
        lda     #$02
        tam     #%00100000      ; $A000 = bank 2
        lda     #$03
        tam     #%01000000      ; $C000 = bank 3
        stz     $0C01           ; timer off
        lda     #%00000111
        sta     $1402           ; all IRQs masked
        stz     $1403           ; ack timer
        lda     a:$0000         ; ack VDC

        tii     __DATA_LOAD__, __DATA_RUN__, __DATA_SIZE__
        stz     __BSS_RUN__
        tii     __BSS_RUN__, __BSS_RUN__ + 1, __BSS_SIZE__ - 1
        stz     _vbl_count

        lda     #<(__MAIN_START__ + __MAIN_SIZE__ + __STACKSIZE__)
        ldx     #>(__MAIN_START__ + __MAIN_SIZE__ + __STACKSIZE__)
        sta     sp
        stx     sp+1
        jsr     initlib

        lda     #%00000101      ; IRQ1 (VDC) enabled
        sta     $1402
        cli
        jsr     push0
        jsr     push0
        ldy     #4
        jsr     _main
_exit:  jsr     donelib
        jmp     start

; void vsync(void) -- wait for the next vertical blank
_vsync: lda     _vbl_count
@w:     cmp     _vbl_count
        beq     @w
        rts

irq1:   pha
        lda     a:$0000         ; read + acknowledge VDC status
        and     #$20
        beq     @n
        inc     _vbl_count
@n:     pla
        rti

timer:  stz     $1403
        rti

nmi:    rti

        .export initmainargs
initmainargs:
        rts

.segment "VECTORS"
        .word   nmi             ; IRQ2 / BRK
        .word   irq1            ; IRQ1 (VDC)
        .word   timer           ; timer
        .word   nmi             ; NMI
        .word   start           ; reset
