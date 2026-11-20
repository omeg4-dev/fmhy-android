#!/usr/bin/env bash
# Offline monkey run on the emulator (user decision: monkey never touches real sites).
#
#   monkey.sh [--seeds N] [--events N] [--throttle MS] [--out PATH]   N random seeds (default 3 x 4000 events)
#   monkey.sh --seed S [--events N]                                   replay one seed
#
# Airplane mode is switched on for the run and ALWAYS switched off again (trap on EXIT/INT/TERM).
# After each seed, crashscan runs on that seed's logcat. JSON: [{seed, events, injected, aborted, crashes[]}].
# Exit 0 = no fatal/ANR, 1 = at least one, 2 = usage/device error.
set -euo pipefail

PKG=io.github.omeg4dev.fmhy
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
SEEDS=3 EVENTS=4000 THROTTLE=120 SEED="" OUT="" MPID=""

usage() { sed -n '2,9p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
while [[ $# -gt 0 ]]; do
  case "$1" in
    --seeds) SEEDS="$2"; shift 2 ;;
    --events) EVENTS="$2"; shift 2 ;;
    --throttle) THROTTLE="$2"; shift 2 ;;
    --seed) SEED="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "monkey.sh: unknown argument $1" >&2; usage ;;
  esac
done

if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  mapfile -t emus < <(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1}')
  [[ ${#emus[@]} -eq 1 ]] || { echo "monkey.sh: need exactly one emulator online (or ANDROID_SERIAL); found ${#emus[@]}" >&2; exit 2; }
  export ANDROID_SERIAL="${emus[0]}"
fi
A=(adb -s "$ANDROID_SERIAL")

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${OUT:-$REPO/.cache/hunt/monkey-$STAMP.json}"
WORK="$REPO/.cache/hunt/monkey-$STAMP"
mkdir -p "$WORK" "$(dirname "$OUT")"

cleanup() {
  local rc=$?
  set +e  # never let a failing step skip the airplane-mode reset
  trap - EXIT INT TERM
  [[ -n "${MPID:-}" ]] && kill "$MPID" 2>/dev/null
  # shellcheck disable=SC2016  # expands on the device
  "${A[@]}" shell 'pid=$(pidof com.android.commands.monkey); [ -n "$pid" ] && kill $pid' >/dev/null 2>&1
  "${A[@]}" shell cmd connectivity airplane-mode disable >/dev/null 2>&1
  local now
  now="$("${A[@]}" shell settings get global airplane_mode_on 2>/dev/null | tr -d '\r')"
  echo "airplane mode off again (airplane_mode_on=$now)"
  exit "$rc"
}
trap cleanup EXIT
trap 'echo "interrupted"; exit 130' INT TERM

"${A[@]}" shell cmd connectivity airplane-mode enable || { echo "monkey.sh: could not enable airplane mode" >&2; exit 2; }
sleep 2
[[ "$("${A[@]}" shell settings get global airplane_mode_on | tr -d '\r')" == 1 ]] \
  || { echo "monkey.sh: airplane mode did not turn on" >&2; exit 2; }

if [[ -n "$SEED" ]]; then seeds=("$SEED"); else
  seeds=(); for _ in $(seq "$SEEDS"); do seeds+=("$(( (RANDOM << 15 | RANDOM) % 1000000 ))"); done
fi
echo "monkey: ${#seeds[@]} seed(s) x $EVENTS events, throttle ${THROTTLE} ms, offline; seeds: ${seeds[*]}"

parts=()
for s in "${seeds[@]}"; do
  "${A[@]}" logcat -b crash,main,system -c
  "${A[@]}" shell am force-stop "$PKG"
  log="$WORK/seed-$s.log"
  # in the background + wait, so INT/TERM run the cleanup at once instead of after the whole seed
  "${A[@]}" shell monkey -p "$PKG" --pct-syskeys 0 --pct-appswitch 0 --throttle "$THROTTLE" \
    -s "$s" -v "$EVENTS" >"$log" 2>&1 &
  MPID=$!
  wait "$MPID" || true
  MPID=""
  injected="$(sed -n 's/.*Events injected: \([0-9]*\).*/\1/p' "$log" | tail -1 || true)"
  aborted=false; if rg -q 'Monkey aborted|// CRASH|// NOT RESPONDING' "$log"; then aborted=true; fi
  scan="$WORK/crashscan-$s.json"
  summary="$("$HERE/crashscan.py" --out "$scan" | head -1 || true)"
  echo "seed $s: injected ${injected:-0}/$EVENTS, aborted=$aborted · $summary"
  parts+=("$(python3 - "$s" "$EVENTS" "${injected:-0}" "$aborted" "$scan" <<'EOF'
import json, sys
seed, events, injected, aborted, scan = sys.argv[1:]
print(json.dumps({"seed": int(seed), "events": int(events), "injected": int(injected),
                  "aborted": aborted == "true", "crashes": json.load(open(scan))}))
EOF
)")
done

printf '%s\n' "${parts[@]}" | python3 -c '
import json, sys
out, shown = sys.argv[1:]
rows = [json.loads(l) for l in sys.stdin if l.strip()]
json.dump(rows, open(out, "w"), indent=1, ensure_ascii=False)
hard = sum(1 for r in rows for c in r["crashes"] if c["kind"] in ("fatal", "anr"))
print(f"monkey: {len(rows)} seed(s), {hard} fatal/ANR signature(s) · json: {shown}")
sys.exit(1 if hard else 0)
' "$OUT" "${OUT#"$REPO"/}"
