#!/usr/bin/env bash
# Old-reader replay: run an OLD release's own DatasetStore against the NEW bundled snapshot.
#
#   tools/compat/old-reader-replay.sh [TAG...]      default: v1.1.5 v1.2.1
#
# For each tag: scratch git worktree at the tag (next to the repo, unique name), the new
# app/src/main/assets/dataset copied in, tools/compat/OldAppOnNewSnapshotTest.kt added, then the old tests that
# open the bundled snapshot directly with the OLD DatasetStore run there (plus the compat test).
# Prints `OLD-READER OK <tag>`, `OLD-READER FAIL <tag>` + failing lines, or `OLD-READER SKIP <tag> (...)` when the
# old tag cannot build offline. Exit 0 = all OK or skipped, 1 = a failure. Never touches this repo's working tree.
set -uo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
TAGS=("$@"); [ ${#TAGS[@]} -eq 0 ] && TAGS=(v1.1.5 v1.2.1)
rc=0
for tag in "${TAGS[@]}"; do
  WT="$(dirname "$REPO")/fmhy-compat-$tag-$$"
  git -C "$REPO" rev-parse -q --verify "refs/tags/$tag" >/dev/null || { echo "OLD-READER SKIP $tag (no such tag)"; continue; }
  git -C "$REPO" worktree add --detach "$WT" "$tag" >/dev/null 2>&1 || { echo "OLD-READER SKIP $tag (worktree failed)"; continue; }
  cp "$REPO/gw" "$REPO/local.properties" "$WT/"
  ln -s "$(readlink -f "$REPO/.gradle-home")" "$WT/.gradle-home"
  mkdir -p "$WT/app/src/main/assets/dataset" "$WT/.cache" "$WT/core/data/src/test/kotlin/io/github/omeg4dev/fmhy/data/compat"
  cp "$REPO"/app/src/main/assets/dataset/* "$WT/app/src/main/assets/dataset/"
  [ -e "$REPO/.cache/fmhy-edit" ] && ln -s "$(readlink -f "$REPO/.cache/fmhy-edit")" "$WT/.cache/fmhy-edit"
  cp "$REPO/tools/compat/OldAppOnNewSnapshotTest.kt" "$WT/core/data/src/test/kotlin/io/github/omeg4dev/fmhy/data/compat/"
  log="$WT/compat.log"
  (cd "$WT" && ./gw :core:data:test --offline \
      --tests '*OldAppOnNewSnapshotTest' \
      --tests '*DatasetStoreTest.testBundledSnapshotEveryItemFollowsItsOwnHeading' \
      --tests '*CrossLinksTest' --tests '*SafeGuardTest' >"$log" 2>&1)
  code=$?
  if [ $code -eq 0 ]; then
    echo "OLD-READER OK $tag"
  elif rg -q "Could not resolve|offline mode|No cached version" "$log"; then
    echo "OLD-READER SKIP $tag (dependencies not available offline)"
  else
    echo "OLD-READER FAIL $tag"; rg -n "FAILED|AssertionError|Exception" "$log" | head -15
    rg --no-filename -A3 "<failure" "$WT"/core/data/build/test-results/test/*.xml 2>/dev/null | head -20
    rc=1
  fi
  git -C "$REPO" worktree remove --force "$WT" >/dev/null 2>&1
done
exit $rc
