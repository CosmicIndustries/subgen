/* SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from WireGuard for Android's tunnel/tools/libwg-go/api-android.go
 * (Copyright © 2017-2022 Jason A. Donenfeld <Jason@zx2c4.com>), which
 * exports the same wgTurnOn/wgTurnOff/wgGetSocketV4/V6/wgGetConfig/wgVersion
 * functions bound to com.wireguard.android.backend.GoBackend via jni.c in
 * the same directory. This fork points the JNI glue (see jni.c here) at
 * Umbra's own WireGuardBridge class instead, and adds wgTurnOnViaByedpi,
 * which swaps in a custom conn.Bind (see socks5udpbind.go) that relays
 * WireGuard's UDP transport through byedpi's local SOCKS5 listener instead
 * of a plain UDP socket, so byedpi's own DPI-evasion technique gets a
 * chance to disguise WireGuard's own traffic pattern.
 */

package main

// #cgo LDFLAGS: -llog
// #include <android/log.h>
import "C"

import (
	"fmt"
	"math"
	"net"
	"net/netip"
	"os"
	"os/signal"
	"runtime"
	"runtime/debug"
	"strconv"
	"strings"
	"unsafe"

	"golang.org/x/sys/unix"
	"golang.zx2c4.com/wireguard/conn"
	"golang.zx2c4.com/wireguard/device"
	"golang.zx2c4.com/wireguard/ipc"
	"golang.zx2c4.com/wireguard/tun"
)

type AndroidLogger struct {
	level C.int
	tag   *C.char
}

func cstring(s string) *C.char {
	b, err := unix.BytePtrFromString(s)
	if err != nil {
		b := [1]C.char{}
		return &b[0]
	}
	return (*C.char)(unsafe.Pointer(b))
}

func (l AndroidLogger) Printf(format string, args ...interface{}) {
	C.__android_log_write(l.level, l.tag, cstring(fmt.Sprintf(format, args...)))
}

type TunnelHandle struct {
	device *device.Device
	uapi   net.Listener
}

var tunnelHandles map[int32]TunnelHandle

func init() {
	tunnelHandles = make(map[int32]TunnelHandle)
	signals := make(chan os.Signal)
	signal.Notify(signals, unix.SIGUSR2)
	go func() {
		buf := make([]byte, os.Getpagesize())
		for range signals {
			n := runtime.Stack(buf, true)
			if n == len(buf) {
				n--
			}
			buf[n] = 0
			C.__android_log_write(C.ANDROID_LOG_ERROR, cstring("Umbra/WireGuardBridge/Stacktrace"), (*C.char)(unsafe.Pointer(&buf[0])))
		}
	}()
}

// endpointFromSettings pulls the first `endpoint=<ip>:<port>` line out of a
// UAPI settings blob (see device.IpcSetOperation upstream) — that's the one
// WireGuard peer this app supports byedpi-wrapping for.
func endpointFromSettings(settings string) (netip.AddrPort, error) {
	for _, line := range strings.Split(settings, "\n") {
		key, value, ok := strings.Cut(line, "=")
		if !ok || key != "endpoint" {
			continue
		}
		addr, err := netip.ParseAddrPort(value)
		if err == nil {
			return addr, nil
		}
		// endpoint may be host:port rather than ip:port.
		host, portStr, splitErr := net.SplitHostPort(value)
		if splitErr != nil {
			return netip.AddrPort{}, splitErr
		}
		port, convErr := strconv.Atoi(portStr)
		if convErr != nil {
			return netip.AddrPort{}, convErr
		}
		ips, lookupErr := net.LookupHost(host)
		if lookupErr != nil || len(ips) == 0 {
			return netip.AddrPort{}, fmt.Errorf("resolve endpoint host %q: %w", host, lookupErr)
		}
		ip, parseErr := netip.ParseAddr(ips[0])
		if parseErr != nil {
			return netip.AddrPort{}, parseErr
		}
		return netip.AddrPortFrom(ip, uint16(port)), nil
	}
	return netip.AddrPort{}, fmt.Errorf("no endpoint= line in settings")
}

func turnOn(interfaceName string, tunFd int32, settings string, bind conn.Bind) int32 {
	tag := cstring("Umbra/WireGuardBridge/" + interfaceName)
	logger := &device.Logger{
		Verbosef: AndroidLogger{level: C.ANDROID_LOG_DEBUG, tag: tag}.Printf,
		Errorf:   AndroidLogger{level: C.ANDROID_LOG_ERROR, tag: tag}.Printf,
	}

	tunDevice, name, err := tun.CreateUnmonitoredTUNFromFD(int(tunFd))
	if err != nil {
		unix.Close(int(tunFd))
		logger.Errorf("CreateUnmonitoredTUNFromFD: %v", err)
		return -1
	}

	logger.Verbosef("Attaching to interface %v", name)
	dev := device.NewDevice(tunDevice, bind, logger)

	err = dev.IpcSet(settings)
	if err != nil {
		unix.Close(int(tunFd))
		logger.Errorf("IpcSet: %v", err)
		return -1
	}
	dev.DisableSomeRoamingForBrokenMobileSemantics()

	var uapi net.Listener
	uapiFile, err := ipc.UAPIOpen(name)
	if err != nil {
		logger.Errorf("UAPIOpen: %v", err)
	} else {
		uapi, err = ipc.UAPIListen(name, uapiFile)
		if err != nil {
			uapiFile.Close()
			logger.Errorf("UAPIListen: %v", err)
		} else {
			go func() {
				for {
					c, err := uapi.Accept()
					if err != nil {
						return
					}
					go dev.IpcHandle(c)
				}
			}()
		}
	}

	err = dev.Up()
	if err != nil {
		logger.Errorf("Unable to bring up device: %v", err)
		if uapiFile != nil {
			uapiFile.Close()
		}
		dev.Close()
		return -1
	}
	logger.Verbosef("Device started")

	var i int32
	for i = 0; i < math.MaxInt32; i++ {
		if _, exists := tunnelHandles[i]; !exists {
			break
		}
	}
	if i == math.MaxInt32 {
		logger.Errorf("Unable to find empty handle")
		if uapiFile != nil {
			uapiFile.Close()
		}
		dev.Close()
		return -1
	}
	tunnelHandles[i] = TunnelHandle{device: dev, uapi: uapi}
	return i
}

//export wgTurnOn
func wgTurnOn(interfaceName string, tunFd int32, settings string) int32 {
	return turnOn(interfaceName, tunFd, settings, conn.NewStdNetBind())
}

//export wgTurnOnViaByedpi
// byedpiAddr is "ip:port" for byedpi's local SOCKS5 listener. The peer
// endpoint is pulled out of settings itself (see endpointFromSettings) —
// this only supports a single-peer config, which covers the personal-VPN
// use case this app targets.
func wgTurnOnViaByedpi(interfaceName string, tunFd int32, settings string, byedpiAddr string) int32 {
	tag := cstring("Umbra/WireGuardBridge/" + interfaceName)
	logger := &device.Logger{
		Verbosef: AndroidLogger{level: C.ANDROID_LOG_DEBUG, tag: tag}.Printf,
		Errorf:   AndroidLogger{level: C.ANDROID_LOG_ERROR, tag: tag}.Printf,
	}

	proxyAddr, err := netip.ParseAddrPort(byedpiAddr)
	if err != nil {
		logger.Errorf("bad byedpi address %q: %v", byedpiAddr, err)
		unix.Close(int(tunFd))
		return -1
	}
	peerAddr, err := endpointFromSettings(settings)
	if err != nil {
		logger.Errorf("could not determine peer endpoint from settings: %v", err)
		unix.Close(int(tunFd))
		return -1
	}

	return turnOn(interfaceName, tunFd, settings, NewSocks5UDPBind(proxyAddr, peerAddr))
}

//export wgTurnOff
func wgTurnOff(tunnelHandle int32) {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return
	}
	delete(tunnelHandles, tunnelHandle)
	if handle.uapi != nil {
		handle.uapi.Close()
	}
	handle.device.Close()
}

//export wgGetSocketV4
func wgGetSocketV4(tunnelHandle int32) int32 {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	bind, _ := handle.device.Bind().(conn.PeekLookAtSocketFd)
	if bind == nil {
		return -1
	}
	fd, err := bind.PeekLookAtSocketFd4()
	if err != nil {
		return -1
	}
	return int32(fd)
}

//export wgGetSocketV6
func wgGetSocketV6(tunnelHandle int32) int32 {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return -1
	}
	bind, _ := handle.device.Bind().(conn.PeekLookAtSocketFd)
	if bind == nil {
		return -1
	}
	fd, err := bind.PeekLookAtSocketFd6()
	if err != nil {
		return -1
	}
	return int32(fd)
}

//export wgGetConfig
func wgGetConfig(tunnelHandle int32) *C.char {
	handle, ok := tunnelHandles[tunnelHandle]
	if !ok {
		return nil
	}
	settings, err := handle.device.IpcGet()
	if err != nil {
		return nil
	}
	return C.CString(settings)
}

//export wgVersion
func wgVersion() *C.char {
	info, ok := debug.ReadBuildInfo()
	if !ok {
		return C.CString("unknown")
	}
	for _, dep := range info.Deps {
		if dep.Path == "golang.zx2c4.com/wireguard" {
			parts := strings.Split(dep.Version, "-")
			if len(parts) == 3 && len(parts[2]) == 12 {
				return C.CString(parts[2][:7])
			}
			return C.CString(dep.Version)
		}
	}
	return C.CString("unknown")
}

func main() {}
