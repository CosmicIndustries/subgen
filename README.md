# Umbra

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](NOTICE.md)
[![Status](https://img.shields.io/badge/status-active_development-orange)](../../pulls)

A privacy-focused Android VPN app in the spirit of [Rethink](https://github.com/celzero/rethink-app),
merging three real, open-source engines into one running session instead of
making you pick one:

- **[WireGuard](https://www.wireguard.com/) wrapped in DPI evasion** — the
  real `wireguard-go` protocol implementation, driven directly rather than
  through the official app's `GoBackend`, with its own transport optionally
  wrapped by **[ByeByeDPI](https://github.com/hufrea/byedpi)**'s `ciadpi`
  desync proxy. That means networks that specifically detect and throttle
  or block WireGuard itself see disguised traffic instead. DPI settings
  range from a few simple sliders (fake-packet count, TTL, custom payload)
  up to pasting byedpi's *entire* CLI flag surface for advanced tuning.
- **A no-root, per-app firewall** — powered by
  [Shizuku](https://github.com/RikkaApps/Shizuku) (the same
  privileged-without-root mechanism [ShizuWall](https://github.com/piyushmaurya23/ShizuWall)
  uses), active independent of whether the tunnel is running. Every
  installed app gets one of three modes — Direct / WireGuard / Blocked —
  plus one-tap bulk presets (**Recommended** / **Advanced** / **Expert**)
  that block known bloatware and telemetry packages, sourced from the
  [Universal Android Debloater](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation)
  project's risk-tiered package list.
- **Config your way** — paste a WireGuard config, scan it as a QR code, or
  import a `.conf`/`.zip` file directly.

All connection logging is **strictly local** (Room database + CSV export
on demand) — there is no analytics or telemetry anywhere in the codebase.

## Status

Umbra is under active development. The current source, build instructions,
and architecture notes live on the project's [open pull request](../../pulls)
while the app is brought up to a first stable structure in this repository.
CI builds a debug APK on every push and publishes it as a GitHub release for
anyone who wants to try the latest build.

## License

Umbra's own code is MIT-licensed. It builds on several third-party projects
(WireGuard, byedpi, Shizuku, and the Universal Android Debloater package
list among them) — see `NOTICE.md` for exact provenance and licenses.
