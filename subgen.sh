#!/usr/bin/env bash
# subgen.sh — paste a YouTube URL (or several), get .srt/.vtt back.
# Pipeline: yt-dlp (download audio) -> ffmpeg (16k mono wav) -> whisper.cpp (transcribe)
# Live progress bars for every stage. No args needed — just run it.
#
# Usage:
#   ./subgen.sh                          interactive prompt, one URL at a time, blank line to quit
#   ./subgen.sh URL [URL2 URL3 ...]      transcribe these URLs then exit
#   ./subgen.sh -f urls.txt              one URL per line
#
# Config below matches this Rock 5B's whisper.cpp layout; edit the four
# variables under CONFIG if yours lives somewhere else.

set -uo pipefail

# ---------- CONFIG ----------
WHISPER_DIR="${WHISPER_DIR:-$HOME/Downloads/findStuff/files/whisper.cpp}"
WHISPER_BIN="${WHISPER_BIN:-$WHISPER_DIR/build/bin/whisper-cli}"
MODEL="${MODEL:-$WHISPER_DIR/models/ggml-small.en.bin}"
OUT_DIR="${OUT_DIR:-$PWD/subtitles}"
THREADS="${THREADS:-8}"
# ---------- /CONFIG ----------

# ---------- colors / glyphs ----------
if [[ -t 1 ]]; then
  BOLD=$(tput bold); DIM=$(tput dim); RESET=$(tput sgr0)
  GREEN=$(tput setaf 2); YELLOW=$(tput setaf 3); RED=$(tput setaf 1)
  CYAN=$(tput setaf 6); BLUE=$(tput setaf 4)
else
  BOLD=""; DIM=""; RESET=""; GREEN=""; YELLOW=""; RED=""; CYAN=""; BLUE=""
fi
CHECK="${GREEN}✔${RESET}"; CROSS="${RED}✘${RESET}"; ARROW="${CYAN}➜${RESET}"

# ---------- progress bar ----------
# draw_bar LABEL PERCENT
draw_bar() {
  local label="$1" pct="$2" width=32
  ((pct < 0)) && pct=0; ((pct > 100)) && pct=100
  local filled=$(( pct * width / 100 ))
  local empty=$(( width - filled ))
  local bar; bar=$(printf '%*s' "$filled" '' | tr ' ' '█')
  local rest; rest=$(printf '%*s' "$empty" '' | tr ' ' '░')
  # NOTE: always stderr. download_audio()'s return value (a file path) comes
  # back via stdout through command substitution — any progress-bar bytes on
  # stdout would corrupt that path. Keep ALL UI on stderr, always.
  printf "\r  %-10s ${CYAN}%s%s${RESET} %3d%%  " "$label" "$bar" "$rest" "$pct" >&2
}

# spinner for steps with no measurable percent (kept for fallback use)
SPIN_CHARS='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
spin_frame() {
  local i=$(( $(date +%s%N) / 100000000 % ${#SPIN_CHARS} ))
  echo -n "${SPIN_CHARS:$i:1}"
}

step_header() {
  local n="$1" total="$2" title="$3"
  echo
  echo "${BOLD}${BLUE}[${n}/${total}]${RESET} ${BOLD}${title}${RESET}"
}

die() { echo "${CROSS} $*" >&2; exit 1; }

# ---------- dependency check ----------
check_deps() {
  local missing=()
  command -v yt-dlp >/dev/null || missing+=("yt-dlp")
  command -v ffmpeg >/dev/null || missing+=("ffmpeg")
  [[ -x "$WHISPER_BIN" ]] || missing+=("whisper-cli (looked in $WHISPER_BIN)")
  [[ -f "$MODEL" ]] || missing+=("whisper model (looked in $MODEL)")
  if ((${#missing[@]})); then
    echo "${CROSS} Missing:" >&2
    printf '    - %s\n' "${missing[@]}" >&2
    echo "  Edit WHISPER_DIR/WHISPER_BIN/MODEL at the top of this script if paths differ." >&2
    exit 1
  fi
}

extract_id() {
  # pull an 11-char YouTube ID out of any common URL shape, else fall back
  # to a timestamp so non-YouTube URLs still get a unique filename.
  local url="$1" id
  id=$(grep -oE '(v=|youtu\.be/|shorts/)[A-Za-z0-9_-]{11}' <<<"$url" | head -1 | grep -oE '[A-Za-z0-9_-]{11}$')
  [[ -n "$id" ]] && echo "$id" || echo "vid_$(date +%s)"
}

# ---------- stage 1: download audio ----------
download_audio() {
  local url="$1" id="$2" tmp="$3"
  local out_tmpl="$tmp/${id}.%(ext)s"
  local logfile="$tmp/ytdlp.log"

  yt-dlp -x --audio-format m4a --audio-quality 0 \
    --sleep-requests 1 --sleep-interval 3 --max-sleep-interval 8 \
    -o "$out_tmpl" "$url" > "$logfile" 2>&1 &
  local pid=$!

  local pct=0
  while kill -0 "$pid" 2>/dev/null; do
    local line
    line=$(grep -oE '[0-9]+\.[0-9]%' "$logfile" 2>/dev/null | tail -1 | tr -d '%')
    [[ -n "$line" ]] && pct="${line%.*}"
    draw_bar "download" "$pct"
    sleep 0.2
  done
  wait "$pid"; local rc=$?
  if ((rc != 0)); then
    draw_bar "download" "$pct"; echo "${CROSS}" >&2
    echo "${DIM}$(tail -5 "$logfile")${RESET}" >&2
    return 1
  fi
  draw_bar "download" 100; echo "${CHECK}" >&2

  find "$tmp" -maxdepth 1 -name "${id}.*" -print -quit
}

# ---------- stage 2: convert to 16k mono wav ----------
convert_wav() {
  local infile="$1" outfile="$2" progress_file="$3"
  local duration
  duration=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$infile" 2>/dev/null)
  duration=${duration%.*}
  ((duration <= 0)) && duration=1

  ffmpeg -y -i "$infile" -ar 16000 -ac 1 -c:a pcm_s16le "$outfile" \
    -progress pipe:1 -nostats -loglevel error > "$progress_file" 2>&1 &
  local pid=$!

  local pct=0
  while kill -0 "$pid" 2>/dev/null; do
    local us
    us=$(grep -oE 'out_time_ms=[0-9]+' "$progress_file" 2>/dev/null | tail -1 | cut -d= -f2)
    if [[ -n "$us" ]]; then
      pct=$(( us / 1000000 * 100 / duration ))
    fi
    draw_bar "convert" "$pct"
    sleep 0.2
  done
  wait "$pid"; local rc=$?
  if ((rc != 0)) || [[ ! -s "$outfile" ]]; then
    draw_bar "convert" "$pct"; echo "${CROSS}" >&2
    return 1
  fi
  draw_bar "convert" 100; echo "${CHECK}" >&2
}

# ---------- stage 3: transcribe ----------
transcribe() {
  local wavfile="$1" outbase="$2"
  local logfile="${wavfile}.whisper.log"

  "$WHISPER_BIN" -m "$MODEL" -t "$THREADS" -f "$wavfile" \
    -osrt -ovtt -of "$outbase" --print-progress \
    > "$logfile" 2>&1 &
  local pid=$!

  local pct=0
  while kill -0 "$pid" 2>/dev/null; do
    local line
    line=$(grep -oE 'progress = *[0-9]+' "$logfile" 2>/dev/null | tail -1 | grep -oE '[0-9]+')
    [[ -n "$line" ]] && pct="$line"
    draw_bar "transcribe" "$pct"
    sleep 0.3
  done
  wait "$pid"; local rc=$?
  if ((rc != 0)) || [[ ! -s "${outbase}.srt" ]]; then
    draw_bar "transcribe" "$pct"; echo "${CROSS}" >&2
    echo "${DIM}$(tail -10 "$logfile")${RESET}" >&2
    return 1
  fi
  draw_bar "transcribe" 100; echo "${CHECK}" >&2
}

# ---------- one full run ----------
process_url() {
  local url="$1" idx="$2" total="$3"
  local id; id=$(extract_id "$url")

  echo "${ARROW} ${BOLD}[$idx/$total]${RESET} ${id}  ${DIM}${url}${RESET}"

  local tmp; tmp=$(mktemp -d)
  # shellcheck disable=SC2064
  trap "rm -rf '$tmp'" RETURN

  step_header 1 3 "downloading audio"
  local audiofile
  audiofile=$(download_audio "$url" "$id" "$tmp") || { echo "  ${RED}download failed for $id${RESET}"; return 1; }
  [[ -s "$audiofile" ]] || { echo "  ${RED}download produced no file for $id${RESET}"; return 1; }

  step_header 2 3 "converting to 16kHz mono wav"
  local wavfile="$tmp/${id}.wav"
  convert_wav "$audiofile" "$wavfile" "$tmp/ffmpeg.progress" || { echo "  ${RED}conversion failed for $id${RESET}"; return 1; }

  step_header 3 3 "transcribing (whisper.cpp)"
  mkdir -p "$OUT_DIR"
  local outbase="$OUT_DIR/${id}"
  transcribe "$wavfile" "$outbase" || { echo "  ${RED}transcription failed for $id${RESET}"; return 1; }

  echo
  echo "  ${CHECK} ${BOLD}${outbase}.srt${RESET}"
  echo "  ${CHECK} ${BOLD}${outbase}.vtt${RESET}"
  local preview
  preview=$(grep -vE '^[0-9]+$|-->|^\s*$' "${outbase}.srt" | head -2)
  [[ -n "$preview" ]] && echo "  ${DIM}${preview}${RESET}"
  echo
}

# ---------- main ----------
main() {
  check_deps
  mkdir -p "$OUT_DIR"

  local urls=()
  if [[ "${1:-}" == "-f" ]]; then
    [[ -f "${2:-}" ]] || die "no such file: ${2:-}"
    while IFS= read -r line; do
      [[ -n "$line" && "$line" != \#* ]] && urls+=("$line")
    done < "$2"
  elif (($# > 0)); then
    urls=("$@")
  else
    echo "${BOLD}subgen${RESET} — paste a video URL (blank line to quit)"
    while true; do
      read -r -p "${ARROW} URL: " u
      [[ -z "$u" ]] && break
      process_url "$u" 1 1
    done
    echo "${DIM}done — output in $OUT_DIR${RESET}"
    exit 0
  fi

  local total=${#urls[@]} i=0 fails=0
  for u in "${urls[@]}"; do
    i=$((i + 1))
    process_url "$u" "$i" "$total" || fails=$((fails + 1))
  done

  echo "${BOLD}${total} total, $((total - fails)) ok, ${fails} failed${RESET} — output in ${OUT_DIR}"
  ((fails > 0)) && exit 1
  exit 0
}

main "$@"
