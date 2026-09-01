# subgen

Two independent projects live in this repo:

## `subgen.sh` — YouTube → subtitles

A single bash script: paste a YouTube URL (or several), get back `.srt`/`.vtt`
subtitle files. Pipeline: `yt-dlp` (download audio) → `ffmpeg` (16kHz mono
wav) → `whisper.cpp` (transcribe), with a live progress bar for each stage.

```sh
./subgen.sh                      # interactive prompt, one URL at a time
./subgen.sh URL [URL2 URL3 ...]  # transcribe these URLs then exit
./subgen.sh -f urls.txt          # one URL per line
```

Requires `yt-dlp`, `ffmpeg`, and a local `whisper.cpp` build + model. The
script's `CONFIG` section (top of the file) points at a Rock 5B's
`whisper.cpp` layout by default — edit `WHISPER_DIR`/`WHISPER_BIN`/`MODEL`/
`OUT_DIR`, or set them as environment variables, if yours lives elsewhere.
Output lands in `./subtitles/<video-id>.srt` / `.vtt`.

## `umbra/` — Android VPN + DPI-bypass + firewall app

An Android app in the spirit of [Rethink](https://github.com/celzero/rethink-app):
one always-on [WireGuard](https://www.wireguard.com/) tunnel, optionally
wrapped in [byedpi](https://github.com/hufrea/byedpi)'s UDP desync techniques
for networks that specifically detect and throttle WireGuard, plus an
independent [Shizuku](https://shizuku.rikka.app/)-based per-app hard-block
firewall — all local-only, no analytics or telemetry anywhere in the
codebase.

See [`umbra/README.md`](umbra/README.md) for what it does and why, and
[`umbra/BUILDING.md`](umbra/BUILDING.md) to build it yourself. CI
(`.github/workflows/build-umbra.yml`) builds a debug APK on every push and
publishes it as a GitHub release — see the repo's
[Releases](../../releases) page for the latest installable build.
