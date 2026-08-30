# Architecture

## Why WireGuard mode and DPI-bypass mode can't run simultaneously

Android allows exactly one active `VpnService`-established TUN interface
system-wide at a time; calling `Builder.establish()` a second time (from
any app, even the same one) tears down whatever tunnel was already up.

The official WireGuard library (`com.wireguard.android:tunnel`) doesn't
hand you a raw file descriptor to plug into your own `VpnService` — its
`GoBackend` class owns and drives its *own* internal `android.net.VpnService`
subclass (`GoBackend.VpnService`), merged into the manifest from the
library's AAR. Confirmed by extracting the real
`com.wireguard.android:tunnel` native library (`libwg-go.so`) from the
official WireGuard Android app: its JNI entry points are hardcoded to
`Java_com_wireguard_android_backend_GoBackend_wgTurnOn` /
`_wgTurnOff` / etc. — meaning only `GoBackend`'s own Kotlin class can call
them; there's no supported way to redirect wireguard-go's tunnel onto a
file descriptor owned by a different `VpnService`.

The alternative — building `wireguard-go` from source with our own JNI
bridge instead of depending on the published library — would let a single
custom `VpnService` own everything, the way apps like Mullvad's do. That's
a real, valid architecture; it was deliberately not taken here because it
requires a Go toolchain + `gomobile bind` step this scaffold's build
(Gradle + CMake/NDK only) doesn't set up, and because it couldn't be
verified in an environment with no Android SDK or device to test against.
It's the natural next step if someone wants true simultaneous
WireGuard+DPI-bypass chaining later.

Given that constraint, Umbra exposes WireGuard and DPI-bypass as two
mutually exclusive **modes** rather than pretending they compose:

- **WireGuard mode**: `GoBackend` + its own auto-managed VpnService.
  Per-app routing goes through wireguard-android's own config extension —
  `IncludedApplications`/`ExcludedApplications` keys in the `[Interface]`
  block (confirmed present in the official app's resources) — rather than
  a Builder we control ourselves. See `tunnel/WireGuardConfigStore.kt`.
- **DPI-bypass mode**: `vpn/UmbraVpnService`, a plain `VpnService` we do
  own, running byedpi as a local SOCKS5 desync proxy and
  hev-socks5-tunnel as the bridge from the TUN fd into it. Per-app routing
  uses `Builder.addAllowedApplication`/`addDisallowedApplication` directly.

Apps tagged `AppMode.BLOCKED` are enforced by the Shizuku firewall path
(`firewall/ShizukuFirewall.kt`) independent of either VPN mode, since it
works at the OS policy level rather than by routing — that's the one piece
that *is* always active regardless of which mode (or no mode) is running.

## Native build

`app/src/main/cpp/CMakeLists.txt` builds two shared libraries from the
vendored submodules in `external/`:

- `libbyedpi.so`: byedpi's own sources (its CLI arg parser, desync engine,
  event loop) compiled as-is, plus one small JNI shim
  (`byedpi_jni.c`) that calls the same entry points byedpi's own `main()`
  calls (`parse_args` → `init` → `run`). Because `run()` blocks, it's
  driven from a pthread; stopping it delivers `SIGTERM` to that thread,
  which byedpi's own `on_cancel()` handler (registered inside `run()`)
  already turns into a clean shutdown of its listening socket — reusing
  upstream's own tested shutdown path instead of reimplementing it.
- `libhev-socks5-tunnel.so`: hev-socks5-tunnel's sources (plus its nested
  `third-part/{yaml,lwip,hev-task-system}` and `src/core` submodules),
  including its *own* upstream-provided Android JNI glue
  (`src/hev-jni.c`) — we don't write new glue for this one, we just point
  its `PKGNAME`/`CLSNAME` compile-time macros at our own Kotlin class
  (`dpi/TProxyService.kt`) instead of its `hev/htproxy/TProxyService`
  default.

Both were verified against the real compiled `.so` files (JNI symbol names
via `strings`, byedpi's exact CLI flag set from its embedded help text)
before the Kotlin/C wrappers were written, rather than guessed from
documentation. See `NOTICE.md` for exact provenance.

## What hasn't been verified

Everything above is grounded in real upstream source/binaries, but none of
it has been compiled or run — there's no Android SDK, emulator, or device
in the environment this was built in. The most likely rough edges on a
first real build:

- `Shizuku.newProcess(...)`'s exact signature/return type in
  `dev.rikka.shizuku:api:13.1.5` (see `firewall/ShizukuFirewall.kt`).
- The exact `cmd netpolicy`/`cmd appops` grammar for a full per-UID network
  block on whatever Android version you're targeting (also
  `ShizukuFirewall.kt`) — worth an `adb shell cmd netpolicy --help` on a
  test device.
- Whether byedpi's `main.c` (which references `daemon()` under `#ifdef
  DAEMON`, an unused code path since Umbra never passes `-D`) links
  cleanly against Android's Bionic libc without adjustment.
- General Gradle/AGP/NDK version-compatibility issues that only show up
  inside a real Android Studio sync.
