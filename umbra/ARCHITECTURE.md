# Architecture

## One tunnel, three engines, running together

Umbra owns exactly one `VpnService`/TUN interface (`vpn/UmbraVpnService`).
Every app tagged `AppMode.VPN_WIREGUARD` is routed through the WireGuard
tunnel; every app tagged `AppMode.BLOCKED` is hard-blocked via Shizuku
independent of whether the tunnel is even running; everything else is left
off the TUN and goes direct. Whether WireGuard's *own* connection to your
VPN server is itself wrapped in byedpi's desync techniques is a single
global toggle (`SettingsStore.byedpiWrapEnabled`), not a per-app choice —
so all three pieces the user asked to merge are active at once, in one
session, rather than switched between.

This wasn't the first design. The original version ran WireGuard and DPI
bypass as two **mutually exclusive** modes, each with its own VpnService,
because Android allows only one active VPN interface system-wide and the
official `com.wireguard.android:tunnel` library's `GoBackend` class insists
on driving its *own* auto-managed VpnService — confirmed by extracting the
real `libwg-go.so` from the official WireGuard Android app: its JNI entry
points are hardcoded to
`Java_com_wireguard_android_backend_GoBackend_wgTurnOn` and friends, so
only `GoBackend`'s own Kotlin/Java class could call them. That's a
reasonable design if you actually want two independent per-app-selectable
engines sharing one TUN — but doing that *correctly* means reading raw
packets off the TUN yourself, asking Android which app/UID each connection
belongs to (`ConnectivityManager.getConnectionOwnerUid`), and handing each
packet to the right engine: a hand-rolled userspace router, which is most
of what an app like Rethink actually is. Given the choice between that and
"DPI-bypass wraps WireGuard's own transport instead," wrapping was chosen —
it needs only one TUN, no packet router, and reuses byedpi's existing UDP
fake-packet technique (`-a/--udp-fake`) for exactly the problem "a network
detects and throttles WireGuard specifically" describes.

## The Go bridge (`app/src/main/go/`)

To make WireGuard usable inside Umbra's *own* VpnService instead of
GoBackend's, `app/src/main/go/api.go` + `jni.c` are adapted from
wireguard-android's own `tunnel/tools/libwg-go/{api-android.go,jni.c}`
(read directly from its source, not guessed): same
`wgTurnOn`/`wgTurnOff`/`wgGetSocketV4`/`wgGetSocketV6`/`wgGetConfig`/
`wgVersion` JNI contract, just retargeted at Umbra's own
`tunnel/WireGuardBridge.kt` class instead of `GoBackend`. `jni.c` exists
as a separate C file because cgo's `-buildmode=c-shared` exports Go
`string` parameters as a `{ptr,len}` struct rather than a null-terminated C
string, and JNI's dynamic symbol resolution needs the exact
`Java_<package>_<Class>_<method>` name regardless — cgo automatically
compiles and links any `.c` file sitting next to a cgo-using `.go` file in
the same package directory, which is how this needs no separate build
step of its own.

The one real addition is `wgTurnOnViaByedpi` (api.go) +
`socks5udpbind.go`: a custom `conn.Bind` (the interface wireguard-go uses
for all its network I/O — verified against the real interface in
`golang.zx2c4.com/wireguard/conn`) that relays WireGuard's UDP transport
through byedpi's local SOCKS5 listener via RFC 1928 §7 UDP ASSOCIATE,
instead of a plain UDP socket. This was checked against byedpi's *actual*
UDP-associate handling (`external/byedpi/proxy.c`:
`udp_associate()`/`on_udp_tunnel()`), not just the RFC: byedpi expects one
UDP ASSOCIATE request naming the real destination up front and treats it
as a fixed target, rather than accepting a different destination per
datagram the way some SOCKS5 servers do — which is exactly what a
single-peer WireGuard client needs anyway, so this only supports one peer
(the common personal-VPN-client case).

`com.wireguard.config.Config` (parsing wg-quick text + `toWgUserspaceString()`,
which produces the UAPI text `wgTurnOn`'s `settings` parameter expects) is
still used from the `com.wireguard.android:tunnel` Maven artifact — only
`GoBackend` itself was dropped.

### Which byedpi flags actually apply to a UDP-only relay

byedpi has a large CLI surface (`--split`/`--disorder`/`--oob`/`--tlsrec`/
`--fake`/etc.), but almost all of it is TCP-segment-framing logic with no
meaning here, since byedpi's only job in this app is relaying WireGuard's
UDP transport (see above) — not proxying arbitrary TCP connections. Rather
than guess which flags matter, `external/byedpi/desync.c`'s `desync_udp()`
was read directly: it only ever touches `dp->udp_fake_count`,
`dp->fake_data`, `dp->fake_offset`, and `dp->ttl` off its params struct.
`dpi/ByeDpiConfig.kt` exposes exactly those three that materially change
behavior — `-a/--udp-fake` (decoy count, already had a UI control),
`-t/--ttl` (decoy TTL, byedpi's own default is 8 per `DEFAULT_TTL` in
desync.c — this is the value the upstream README calls out as the one to
actually tune per-network, since too high a TTL means the decoy is
indistinguishable from a real packet and too low means it never reaches
whatever's inspecting traffic), and `-l/--fake-data` (custom decoy
payload, using byedpi's own `ftob()` "leading `:` means literal string,
not a file path" convention from main.c, since a bare path can't resolve
to anything inside this app's sandbox anyway).

### What's actually been verified vs. not

The Go/C side got unusually thorough local verification for something
built in an environment with no Android SDK or device: the *exact
committed* `api.go` + `jni.c` + `socks5udpbind.go` were compiled and
linked locally (Go 1.24.7 was already installed) against a real JDK
`jni.h` and a stub `android/log.h` + a stub `liblog.so`, producing a real
`.so` with all seven `Java_com_cosmicindustries_umbra_tunnel_WireGuardBridge_*`
symbols present and correctly named (`nm -D` confirmed this directly).
The SOCKS5 UDP framing (`encodeSocks5UDP`/`decodeSocks5UDP` round-trip)
and the association handshake (`socks5UDPAssociate` against a fake SOCKS5
server replying exactly as byedpi's real code does) were unit-tested
during development this same way.

What that *doesn't* cover: the real NDK cross-compile (CI does this, not
this local check) and, most importantly, actual end-to-end behavior on a
real device — a real byedpi binary, a real WireGuard peer, real network
conditions. If WireGuard connects with byedpi-wrapping off but not on,
`socks5udpbind.go` is the first place to look; try `-a/--udp-fake 0` or
disabling the wrap toggle entirely to isolate whether the issue is the
relay or something else.

Other known-unverified pieces, carried over from earlier:

- `Shizuku.newProcess(...)` was found to be `private` in the real
  `dev.rikka.shizuku:api:13.1.5` artifact (confirmed by downloading its
  sources jar from Maven Central), so `firewall/ShizukuFirewall.kt` uses
  `Shizuku.bindUserService()` with a custom AIDL service
  (`firewall/IUserService.aidl` + `UserService.kt`) instead.
- The exact `cmd netpolicy`/`cmd appops` grammar in
  `ShizukuFirewall.kt`'s block/unblock commands for a full per-UID network
  block — worth an `adb shell cmd netpolicy --help` on a real device.
- `dev.rikka.shizuku:provider`'s `ShizukuProvider` needed a manual
  `<provider>` declaration in `AndroidManifest.xml` — the library ships
  the class but deliberately doesn't declare it itself (confirmed by
  decompiling the class: `attachInfo()` throws `IllegalStateException` if
  a host app gets `android:multiprocess`/`android:exported` wrong, which
  only makes sense if the host is expected to add the provider itself).
  Missing this was the actual bug behind "Shizuku not detected" the first
  time this was built and tested.
