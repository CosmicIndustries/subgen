package main

// socks5udpbind.go — a conn.Bind that relays WireGuard's UDP transport
// through byedpi's local SOCKS5 listener (RFC 1928 §7 UDP ASSOCIATE),
// so byedpi's own DPI-evasion technique (--udp-fake: send N decoy
// datagrams before the real one) gets a chance to disguise WireGuard's
// traffic pattern on networks that specifically target it.
//
// Verified against byedpi's actual UDP-associate handling
// (external/byedpi/proxy.c: udp_associate()/on_udp_tunnel()), not just
// the RFC: byedpi expects ONE UDP ASSOCIATE request naming the real
// destination up front and uses it as a fixed target for its outbound
// leg (it does not support per-datagram destinations the way some
// SOCKS5 servers do), which is exactly what a WireGuard client with a
// single peer needs anyway. Both directions use the standard RFC 1928 §7
// framing: 2 reserved bytes, 1 fragment byte (always 0 — byedpi doesn't
// support fragmented UDP), ATYP, address, port, then payload.
//
// Scope: this only supports a single WireGuard peer endpoint (the
// common personal-VPN-client case: one [Peer] block). A multi-peer mesh
// config won't work with byedpi-wrapping enabled.
//
// This module can only actually build with GOOS=android (see api.go's
// cgo import of android/log.h), so it has no runnable `go test` target
// as committed. During development, the framing (encodeSocks5UDP /
// decodeSocks5UDP round-trip) and the handshake (socks5UDPAssociate
// against a fake SOCKS5 server replying exactly as byedpi's real
// udp_associate() does) were both unit-tested by temporarily swapping
// api.go for a cgo-free stub locally — this file itself is unchanged
// from that verified version. The Android cross-compile and the actual
// device-level behavior (a real byedpi binary, a real peer) are not
// verified — see ARCHITECTURE.md.

import (
	"encoding/binary"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"sync"

	"golang.zx2c4.com/wireguard/conn"
)

var (
	errSocks5Handshake = errors.New("socks5: unexpected response from byedpi")
	errNoSession        = errors.New("socks5: not connected")
)

type socks5Endpoint struct {
	addr netip.AddrPort
}

func (e *socks5Endpoint) ClearSrc()           {}
func (e *socks5Endpoint) SrcToString() string { return "" }
func (e *socks5Endpoint) DstToString() string { return e.addr.String() }
func (e *socks5Endpoint) DstToBytes() []byte {
	b := e.addr.Addr().As16()
	return b[:]
}
func (e *socks5Endpoint) DstIP() netip.Addr { return e.addr.Addr() }
func (e *socks5Endpoint) SrcIP() netip.Addr { return netip.Addr{} }

// Socks5UDPBind implements conn.Bind for exactly one fixed peer, reached via
// a local byedpi SOCKS5 listener.
type Socks5UDPBind struct {
	proxyAddr netip.AddrPort
	peerAddr  netip.AddrPort

	mu    sync.Mutex
	ctrl  net.Conn     // TCP control connection to byedpi; the UDP association dies if this closes
	relay *net.UDPConn // our local socket, talking to byedpi's relay address
	dst   netip.AddrPort
}

func NewSocks5UDPBind(proxyAddr, peerAddr netip.AddrPort) *Socks5UDPBind {
	return &Socks5UDPBind{proxyAddr: proxyAddr, peerAddr: peerAddr}
}

func (b *Socks5UDPBind) ParseEndpoint(s string) (conn.Endpoint, error) {
	addr, err := netip.ParseAddrPort(s)
	if err != nil {
		return nil, fmt.Errorf("socks5udpbind: parse endpoint %q: %w", s, err)
	}
	return &socks5Endpoint{addr: addr}, nil
}

func (b *Socks5UDPBind) Open(port uint16) ([]conn.ReceiveFunc, uint16, error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	ctrl, relayAddr, err := socks5UDPAssociate(b.proxyAddr, b.peerAddr)
	if err != nil {
		return nil, 0, fmt.Errorf("socks5udpbind: associate via byedpi: %w", err)
	}

	local, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: 0})
	if err != nil {
		ctrl.Close()
		return nil, 0, err
	}

	b.ctrl = ctrl
	b.relay = local
	b.dst = relayAddr

	return []conn.ReceiveFunc{b.receiveFunc}, 0, nil
}

func (b *Socks5UDPBind) Close() error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.relay != nil {
		b.relay.Close()
		b.relay = nil
	}
	if b.ctrl != nil {
		b.ctrl.Close()
		b.ctrl = nil
	}
	return nil
}

func (b *Socks5UDPBind) SetMark(mark uint32) error { return nil }

func (b *Socks5UDPBind) BatchSize() int { return 1 }

func (b *Socks5UDPBind) Send(bufs [][]byte, ep conn.Endpoint) error {
	b.mu.Lock()
	relay, dst := b.relay, b.dst
	b.mu.Unlock()
	if relay == nil {
		return errNoSession
	}
	if _, ok := ep.(*socks5Endpoint); !ok {
		return conn.ErrWrongEndpointType
	}
	for _, buf := range bufs {
		packet := encodeSocks5UDP(b.peerAddr, buf)
		if _, err := relay.WriteToUDPAddrPort(packet, dst); err != nil {
			return err
		}
	}
	return nil
}

func (b *Socks5UDPBind) receiveFunc(packets [][]byte, sizes []int, eps []conn.Endpoint) (int, error) {
	b.mu.Lock()
	relay := b.relay
	b.mu.Unlock()
	if relay == nil {
		return 0, net.ErrClosed
	}
	buf := make([]byte, 65535)
	n, err := relay.Read(buf)
	if err != nil {
		return 0, err
	}
	payload, err := decodeSocks5UDP(buf[:n])
	if err != nil {
		// Malformed frame from the relay; drop it without erroring the whole loop.
		sizes[0] = 0
		return 1, nil
	}
	copy(packets[0], payload)
	sizes[0] = len(payload)
	eps[0] = &socks5Endpoint{addr: b.peerAddr}
	return 1, nil
}

// socks5UDPAssociate performs the SOCKS5 handshake + UDP ASSOCIATE request
// against byedpi, targeting peerAddr, and returns the open control
// connection (must be kept open for the lifetime of the association) plus
// the relay address to send/receive framed UDP datagrams on.
func socks5UDPAssociate(proxyAddr, peerAddr netip.AddrPort) (net.Conn, netip.AddrPort, error) {
	ctrl, err := net.Dial("tcp", proxyAddr.String())
	if err != nil {
		return nil, netip.AddrPort{}, err
	}

	// Greeting: version 5, 1 method offered, no-auth.
	if _, err := ctrl.Write([]byte{0x05, 0x01, 0x00}); err != nil {
		ctrl.Close()
		return nil, netip.AddrPort{}, err
	}
	resp := make([]byte, 2)
	if _, err := readFull(ctrl, resp); err != nil {
		ctrl.Close()
		return nil, netip.AddrPort{}, err
	}
	if resp[0] != 0x05 || resp[1] != 0x00 {
		ctrl.Close()
		return nil, netip.AddrPort{}, errSocks5Handshake
	}

	// UDP ASSOCIATE request naming the real WireGuard peer as the target
	// (byedpi treats this as fixed, not a per-datagram hint).
	req := socks5AddrBytes(peerAddr)
	full := append([]byte{0x05, 0x03, 0x00}, req...)
	if _, err := ctrl.Write(full); err != nil {
		ctrl.Close()
		return nil, netip.AddrPort{}, err
	}

	header := make([]byte, 4)
	if _, err := readFull(ctrl, header); err != nil {
		ctrl.Close()
		return nil, netip.AddrPort{}, err
	}
	if header[0] != 0x05 || header[1] != 0x00 {
		ctrl.Close()
		return nil, netip.AddrPort{}, fmt.Errorf("%w: rep=%d", errSocks5Handshake, header[1])
	}
	bndAddr, err := readSocks5Addr(ctrl, header[3])
	if err != nil {
		ctrl.Close()
		return nil, netip.AddrPort{}, err
	}
	if !bndAddr.Addr().IsValid() || bndAddr.Addr().IsUnspecified() {
		// Some servers reply with 0.0.0.0 (or ::) meaning "same host you
		// connected to"; fall back to the proxy's own address in that case.
		bndAddr = netip.AddrPortFrom(proxyAddr.Addr(), bndAddr.Port())
	}
	return ctrl, bndAddr, nil
}

func readFull(c net.Conn, buf []byte) (int, error) {
	total := 0
	for total < len(buf) {
		n, err := c.Read(buf[total:])
		if err != nil {
			return total, err
		}
		total += n
	}
	return total, nil
}

// socks5AddrBytes renders ATYP + address + port for a request.
func socks5AddrBytes(addr netip.AddrPort) []byte {
	ip := addr.Addr()
	var out []byte
	if ip.Is4() {
		out = append(out, 0x01)
		b := ip.As4()
		out = append(out, b[:]...)
	} else {
		out = append(out, 0x04)
		b := ip.As16()
		out = append(out, b[:]...)
	}
	portBytes := make([]byte, 2)
	binary.BigEndian.PutUint16(portBytes, addr.Port())
	return append(out, portBytes...)
}

// readSocks5Addr reads ADDR+PORT for the given ATYP from a reply that
// already had its 4-byte header (ver, rep, rsv, atyp) consumed.
func readSocks5Addr(c net.Conn, atyp byte) (netip.AddrPort, error) {
	var ip netip.Addr
	switch atyp {
	case 0x01:
		b := make([]byte, 4)
		if _, err := readFull(c, b); err != nil {
			return netip.AddrPort{}, err
		}
		ip = netip.AddrFrom4([4]byte(b))
	case 0x04:
		b := make([]byte, 16)
		if _, err := readFull(c, b); err != nil {
			return netip.AddrPort{}, err
		}
		ip = netip.AddrFrom16([16]byte(b))
	case 0x03:
		lb := make([]byte, 1)
		if _, err := readFull(c, lb); err != nil {
			return netip.AddrPort{}, err
		}
		b := make([]byte, lb[0])
		if _, err := readFull(c, b); err != nil {
			return netip.AddrPort{}, err
		}
		resolved, err := net.ResolveIPAddr("ip", string(b))
		if err != nil {
			return netip.AddrPort{}, err
		}
		addr, ok := netip.AddrFromSlice(resolved.IP)
		if !ok {
			return netip.AddrPort{}, fmt.Errorf("socks5udpbind: bad resolved address %v", resolved.IP)
		}
		ip = addr
	default:
		return netip.AddrPort{}, fmt.Errorf("socks5udpbind: unsupported ATYP %d", atyp)
	}
	pb := make([]byte, 2)
	if _, err := readFull(c, pb); err != nil {
		return netip.AddrPort{}, err
	}
	return netip.AddrPortFrom(ip, binary.BigEndian.Uint16(pb)), nil
}

// encodeSocks5UDP wraps payload in the RFC 1928 §7 UDP request header.
func encodeSocks5UDP(dst netip.AddrPort, payload []byte) []byte {
	header := append([]byte{0x00, 0x00, 0x00}, socks5AddrBytes(dst)...)
	return append(header, payload...)
}

// decodeSocks5UDP strips the RFC 1928 §7 UDP header and returns the payload.
func decodeSocks5UDP(frame []byte) ([]byte, error) {
	if len(frame) < 4 || frame[2] != 0x00 {
		return nil, fmt.Errorf("socks5udpbind: malformed or fragmented UDP frame")
	}
	atyp := frame[3]
	var addrLen int
	switch atyp {
	case 0x01:
		addrLen = 4
	case 0x04:
		addrLen = 16
	case 0x03:
		if len(frame) < 5 {
			return nil, fmt.Errorf("socks5udpbind: truncated domain frame")
		}
		addrLen = 1 + int(frame[4])
	default:
		return nil, fmt.Errorf("socks5udpbind: unsupported ATYP %d", atyp)
	}
	offset := 4 + addrLen + 2 // header + address + port
	if len(frame) < offset {
		return nil, fmt.Errorf("socks5udpbind: truncated UDP frame")
	}
	return frame[offset:], nil
}
