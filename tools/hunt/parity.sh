#!/usr/bin/env bash
# Kotlin vs Python parser parity against .cache/fmhy-edit (no emulator needed).
#
#   parity.sh [--rev REV]      default REV = HEAD of .cache/fmhy-edit
#
# 1. Python reference dump (fmhy_pipeline.parity) of REV into .cache/parity (replaced).
# 2. ./gw :core:data:parityTest --tests '*ParityTest' --rerun; full log in .cache/hunt/parity-<ts>.log.
# Prints `PARITY OK` or `PARITY MISMATCH` + the first 20 differing rows. ParityTest also asserts a
# fixed entry count and a parse-time budget; those asserts are shown as notes, not as parity.
# Exit 0 = parity OK, 1 = mismatches, 2 = setup/build error.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
REV=HEAD

usage() { sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
while [[ $# -gt 0 ]]; do
  case "$1" in
    --rev) REV="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "parity.sh: unknown argument $1" >&2; usage ;;
  esac
done

cd "$REPO"
EDIT=.cache/fmhy-edit RENTRY=.cache/rentry-FMHYB64.html
for p in "$EDIT/.git" "$RENTRY"; do
  [[ -e "$p" ]] || { echo "parity.sh: missing $p (run the pipeline fetch first)" >&2; exit 2; }
done
SHA="$(git -C "$EDIT" rev-parse --short "$REV")" || { echo "parity.sh: bad rev $REV" >&2; exit 2; }

rm -rf .cache/parity
if ! dump="$(uv run --quiet --project pipeline --offline python -m fmhy_pipeline.parity \
      --repo "$EDIT" --rev "$REV" --rentry "$RENTRY" --out .cache/parity 2>&1)"; then
  echo "parity.sh: python reference dump failed:" >&2; echo "$dump" | tail -20 >&2; exit 2
fi
echo "reference: fmhy-edit $SHA $(echo "$dump" | tail -1)"

mkdir -p .cache/hunt
LOG=".cache/hunt/parity-$(date +%Y%m%d-%H%M%S).log"
set +e
./gw :core:data:parityTest --tests '*ParityTest' --rerun >"$LOG" 2>&1
gw_exit=$?
set -e

mm="$(sed -n 's/^.*Mismatches: *\([0-9][0-9]*\).*$/\1/p' "$LOG" | tail -1)"
if [[ -z "$mm" ]]; then
  echo "parity.sh: ParityTest did not report a mismatch count (gradle exit $gw_exit); see $LOG" >&2
  rg -n 'error:|e: |FAILED|AssertionError|Exception' "$LOG" | head -20 >&2 || true
  exit 2
fi
rg -o 'Sections: +\d+|Entries: +\d+|Parse time: +\d+ ms' "$LOG" | tr -s ' ' | paste -sd' ' | sed 's/^/kotlin: /'

if [[ "$mm" == 0 ]]; then
  echo "PARITY OK"
  if [[ $gw_exit -ne 0 ]]; then
    echo "note: ParityTest still failed on a non-parity assert (fixed count/time):"
    xml=core/data/build/test-results/parityTest/TEST-io.github.omeg4dev.fmhy.data.parse.ParityTest.xml
    { rg -o 'message="[^"]{0,200}' "$xml" 2>/dev/null || rg -o 'AssertionError at \S+' "$LOG" || true; } \
      | sed 's/^message="/  /; s/&gt;/>/g; s/&lt;/</g; s/&quot;/"/g' | head -5
  fi
  echo "log: $LOG"
  exit 0
fi

echo "PARITY MISMATCH: $mm rows (first 20; full list in $LOG)"
awk '/First 30 mismatches:/{f=1;next} f&&/^  /{print; n++} f&&n>=20{exit}' "$LOG"
exit 1
