#!/usr/bin/env bash
source /home/omega/Projects/fmhy-android-p9-search/.work-scripts/lib.sh
launch
tapt 'Search FMHY'; sleep 1.5
ty 'is:starred%sin:video%s'
for i in 1 2 3; do key KEYCODE_DEL; done
shot 20-backspace-pill-removed
key KEYCODE_BACK; sleep .5
tapt '^Filters'; sleep 1.2
shot 21-filters-sheet
tapt 'Hidden \(base64\)'; sleep .8
shot 22-sheet-b64-toggled
key KEYCODE_BACK; sleep .8
shot 23-pills-from-sheet
tapt '^Clear search'; sleep .8
tapt 'Search FMHY'; sleep 1.2
ty 'stremo' 1.5
shot 24-fuzzy-typo
tapt 'Fuzzy match'; sleep 1.2
shot 25-exact-typo
tapt 'Exact match'; sleep .5
tapt '^Clear search'; sleep .8
tapt 'Search FMHY'; sleep 1.2
ty 'rentry' 1.5
shot 26-url-match
key KEYCODE_BACK; sleep .5
tapt 'View all'; sleep 1
shot 27-categories-expanded
