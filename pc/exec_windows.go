//go:build windows

package main

import (
	"os/exec"
	"syscall"
)

// hideWindow sprawia, że adb uruchamia się bez migającego okna konsoli.
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
}
