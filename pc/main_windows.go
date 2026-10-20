//go:build windows

package main

import (
	"os"
	"runtime"
	"sync"
	"syscall"
	"unsafe"
)

// Okno Win32 rysujące obraz z telefonu – bez żadnych zewnętrznych bibliotek.

var (
	user32   = syscall.NewLazyDLL("user32.dll")
	gdi32    = syscall.NewLazyDLL("gdi32.dll")
	kernel32 = syscall.NewLazyDLL("kernel32.dll")

	pRegisterClassExW   = user32.NewProc("RegisterClassExW")
	pCreateWindowExW    = user32.NewProc("CreateWindowExW")
	pDefWindowProcW     = user32.NewProc("DefWindowProcW")
	pGetMessageW        = user32.NewProc("GetMessageW")
	pTranslateMessage   = user32.NewProc("TranslateMessage")
	pDispatchMessageW   = user32.NewProc("DispatchMessageW")
	pPostQuitMessage    = user32.NewProc("PostQuitMessage")
	pPostMessageW       = user32.NewProc("PostMessageW")
	pShowWindow         = user32.NewProc("ShowWindow")
	pBeginPaint         = user32.NewProc("BeginPaint")
	pEndPaint           = user32.NewProc("EndPaint")
	pGetClientRect      = user32.NewProc("GetClientRect")
	pInvalidateRect     = user32.NewProc("InvalidateRect")
	pFillRect           = user32.NewProc("FillRect")
	pDrawTextW          = user32.NewProc("DrawTextW")
	pLoadCursorW        = user32.NewProc("LoadCursorW")
	pLoadIconW          = user32.NewProc("LoadIconW")
	pSetWindowTextW     = user32.NewProc("SetWindowTextW")
	pSetWindowPos       = user32.NewProc("SetWindowPos")
	pAdjustWindowRectEx = user32.NewProc("AdjustWindowRectEx")
	pGetSystemMetrics   = user32.NewProc("GetSystemMetrics")
	pSetProcessDPIAware = user32.NewProc("SetProcessDPIAware")

	pGetStockObject         = gdi32.NewProc("GetStockObject")
	pCreateCompatibleDC     = gdi32.NewProc("CreateCompatibleDC")
	pCreateCompatibleBitmap = gdi32.NewProc("CreateCompatibleBitmap")
	pSelectObject           = gdi32.NewProc("SelectObject")
	pDeleteObject           = gdi32.NewProc("DeleteObject")
	pDeleteDC               = gdi32.NewProc("DeleteDC")
	pBitBlt                 = gdi32.NewProc("BitBlt")
	pStretchDIBits          = gdi32.NewProc("StretchDIBits")
	pSetStretchBltMode      = gdi32.NewProc("SetStretchBltMode")
	pSetBrushOrgEx          = gdi32.NewProc("SetBrushOrgEx")
	pSetTextColor           = gdi32.NewProc("SetTextColor")
	pSetBkMode              = gdi32.NewProc("SetBkMode")
	pCreateFontW            = gdi32.NewProc("CreateFontW")

	pGetModuleHandleW = kernel32.NewProc("GetModuleHandleW")
)

const (
	wmDestroy    = 0x0002
	wmPaint      = 0x000F
	wmEraseBkgnd = 0x0014
	wmApp        = 0x8000
	wmStatus     = wmApp + 1
	wmResize     = wmApp + 2

	wsOverlappedWindow = 0x00CF0000
	cwUseDefault       = 0x80000000
	swShow             = 5
	csHRedraw          = 0x0002
	csVRedraw          = 0x0001
	idcArrow           = 32512
	idiApplication     = 32512
	blackBrush         = 4
	halftone           = 4
	srcCopy            = 0x00CC0020
	transparent        = 1
	dtCenter           = 0x0001
	dtWordBreak        = 0x0010
	dtCalcRect         = 0x0400
	swpNoMove          = 0x0002
	swpNoZOrder        = 0x0004
	smCxScreen         = 0
	smCyScreen         = 1
)

type wndClassEx struct {
	cbSize        uint32
	style         uint32
	lpfnWndProc   uintptr
	cbClsExtra    int32
	cbWndExtra    int32
	hInstance     uintptr
	hIcon         uintptr
	hCursor       uintptr
	hbrBackground uintptr
	lpszMenuName  *uint16
	lpszClassName *uint16
	hIconSm       uintptr
}

type point struct{ x, y int32 }

type msg struct {
	hwnd     uintptr
	message  uint32
	wParam   uintptr
	lParam   uintptr
	time     uint32
	pt       point
	lPrivate uint32
}

type rect struct{ left, top, right, bottom int32 }

type paintStruct struct {
	hdc         uintptr
	fErase      int32
	rcPaint     rect
	fRestore    int32
	fIncUpdate  int32
	rgbReserved [32]byte
}

type bitmapInfoHeader struct {
	biSize          uint32
	biWidth         int32
	biHeight        int32
	biPlanes        uint16
	biBitCount      uint16
	biCompression   uint32
	biSizeImage     uint32
	biXPelsPerMeter int32
	biYPelsPerMeter int32
	biClrUsed       uint32
	biClrImportant  uint32
}

type bitmapInfo struct {
	header bitmapInfoHeader
	colors [1]uint32
}

func utf16(s string) *uint16 {
	p, _ := syscall.UTF16PtrFromString(s)
	return p
}

type window struct {
	hwnd uintptr
	font uintptr

	mu     sync.Mutex
	status string
	pix    []byte
	w, h   int
}

func (v *window) setStatus(s string) {
	v.mu.Lock()
	v.status = s
	v.mu.Unlock()
	pPostMessageW.Call(v.hwnd, wmStatus, 0, 0)
	pInvalidateRect.Call(v.hwnd, 0, 0)
}

func (v *window) setFrame(bgra []byte, w, h int) {
	v.mu.Lock()
	resized := w != v.w || h != v.h
	v.pix, v.w, v.h = bgra, w, h
	v.mu.Unlock()
	if resized {
		pPostMessageW.Call(v.hwnd, wmResize, 0, 0)
	}
	pInvalidateRect.Call(v.hwnd, 0, 0)
}

// fitToFrame dopasowuje rozmiar okna do proporcji ekranu telefonu.
func (v *window) fitToFrame() {
	v.mu.Lock()
	w, h := v.w, v.h
	v.mu.Unlock()
	if w == 0 || h == 0 {
		return
	}
	sw, _, _ := pGetSystemMetrics.Call(smCxScreen)
	sh, _, _ := pGetSystemMetrics.Call(smCyScreen)
	ch := int(sh) * 85 / 100
	cw := ch * w / h
	if maxW := int(sw) * 85 / 100; cw > maxW {
		cw = maxW
		ch = cw * h / w
	}
	r := rect{0, 0, int32(cw), int32(ch)}
	pAdjustWindowRectEx.Call(uintptr(unsafe.Pointer(&r)), wsOverlappedWindow, 0, 0)
	pSetWindowPos.Call(v.hwnd, 0, 0, 0, uintptr(r.right-r.left), uintptr(r.bottom-r.top), swpNoMove|swpNoZOrder)
}

func (v *window) paint() {
	var ps paintStruct
	hdc, _, _ := pBeginPaint.Call(v.hwnd, uintptr(unsafe.Pointer(&ps)))
	defer pEndPaint.Call(v.hwnd, uintptr(unsafe.Pointer(&ps)))

	var rc rect
	pGetClientRect.Call(v.hwnd, uintptr(unsafe.Pointer(&rc)))
	cw, ch := int(rc.right), int(rc.bottom)
	if cw <= 0 || ch <= 0 {
		return
	}

	// Rysujemy do bufora w pamięci, żeby obraz nie migotał.
	mdc, _, _ := pCreateCompatibleDC.Call(hdc)
	bmp, _, _ := pCreateCompatibleBitmap.Call(hdc, uintptr(cw), uintptr(ch))
	old, _, _ := pSelectObject.Call(mdc, bmp)
	brush, _, _ := pGetStockObject.Call(blackBrush)
	pFillRect.Call(mdc, uintptr(unsafe.Pointer(&rc)), brush)

	v.mu.Lock()
	pix, w, h, status := v.pix, v.w, v.h, v.status
	v.mu.Unlock()

	if pix != nil {
		dw, dh := cw, cw*h/w
		if dh > ch {
			dh, dw = ch, ch*w/h
		}
		dx, dy := (cw-dw)/2, (ch-dh)/2
		bi := bitmapInfo{header: bitmapInfoHeader{
			biWidth: int32(w), biHeight: -int32(h), biPlanes: 1, biBitCount: 32,
		}}
		bi.header.biSize = uint32(unsafe.Sizeof(bi.header))
		pSetStretchBltMode.Call(mdc, halftone)
		pSetBrushOrgEx.Call(mdc, 0, 0, 0)
		pStretchDIBits.Call(mdc, uintptr(dx), uintptr(dy), uintptr(dw), uintptr(dh),
			0, 0, uintptr(w), uintptr(h),
			uintptr(unsafe.Pointer(&pix[0])), uintptr(unsafe.Pointer(&bi)), 0, srcCopy)
	} else {
		text := utf16(status)
		pSelectObject.Call(mdc, v.font)
		pSetTextColor.Call(mdc, 0x00FFFFFF)
		pSetBkMode.Call(mdc, transparent)
		tr := rect{20, 0, int32(cw - 20), int32(ch)}
		pDrawTextW.Call(mdc, uintptr(unsafe.Pointer(text)), ^uintptr(0), uintptr(unsafe.Pointer(&tr)), dtCenter|dtWordBreak|dtCalcRect)
		textH := tr.bottom - tr.top
		tr = rect{20, (int32(ch) - textH) / 2, int32(cw - 20), int32(ch)}
		pDrawTextW.Call(mdc, uintptr(unsafe.Pointer(text)), ^uintptr(0), uintptr(unsafe.Pointer(&tr)), dtCenter|dtWordBreak)
	}

	pBitBlt.Call(hdc, 0, 0, uintptr(cw), uintptr(ch), mdc, 0, 0, srcCopy)
	pSelectObject.Call(mdc, old)
	pDeleteObject.Call(bmp)
	pDeleteDC.Call(mdc)
}

func init() {
	// Pętla komunikatów Win32 musi działać zawsze na tym samym wątku systemowym.
	runtime.LockOSThread()
}

func main() {
	pSetProcessDPIAware.Call()

	v := &window{status: "Uruchamianie..."}
	v.font, _, _ = pCreateFontW.Call(28, 0, 0, 0, 400, 0, 0, 0, 1, 0, 0, 5, 0, uintptr(unsafe.Pointer(utf16("Segoe UI"))))

	hInst, _, _ := pGetModuleHandleW.Call(0)
	cursor, _, _ := pLoadCursorW.Call(0, idcArrow)
	icon, _, _ := pLoadIconW.Call(0, idiApplication)
	className := utf16("EkranTelefonuWnd")

	wndProc := syscall.NewCallback(func(hwnd, m, wParam, lParam uintptr) uintptr {
		switch m {
		case wmPaint:
			v.paint()
			return 0
		case wmEraseBkgnd:
			return 1
		case wmStatus:
			v.mu.Lock()
			s := v.status
			v.mu.Unlock()
			pSetWindowTextW.Call(hwnd, uintptr(unsafe.Pointer(utf16("Ekran telefonu – "+s))))
			return 0
		case wmResize:
			v.fitToFrame()
			return 0
		case wmDestroy:
			pPostQuitMessage.Call(0)
			return 0
		}
		r, _, _ := pDefWindowProcW.Call(hwnd, m, wParam, lParam)
		return r
	})

	wc := wndClassEx{
		style:         csHRedraw | csVRedraw,
		lpfnWndProc:   wndProc,
		hInstance:     hInst,
		hIcon:         icon,
		hCursor:       cursor,
		lpszClassName: className,
		hIconSm:       icon,
	}
	wc.cbSize = uint32(unsafe.Sizeof(wc))
	pRegisterClassExW.Call(uintptr(unsafe.Pointer(&wc)))

	v.hwnd, _, _ = pCreateWindowExW.Call(0,
		uintptr(unsafe.Pointer(className)),
		uintptr(unsafe.Pointer(utf16("Ekran telefonu"))),
		wsOverlappedWindow,
		cwUseDefault, cwUseDefault, 540, 960,
		0, 0, hInst, 0)
	if v.hwnd == 0 {
		os.Exit(1)
	}
	pShowWindow.Call(v.hwnd, swShow)

	manual := ""
	if len(os.Args) > 1 {
		manual = os.Args[1]
	}
	go run(manual, v)

	var m msg
	for {
		r, _, _ := pGetMessageW.Call(uintptr(unsafe.Pointer(&m)), 0, 0, 0)
		if int32(r) <= 0 {
			break
		}
		pTranslateMessage.Call(uintptr(unsafe.Pointer(&m)))
		pDispatchMessageW.Call(uintptr(unsafe.Pointer(&m)))
	}
}
