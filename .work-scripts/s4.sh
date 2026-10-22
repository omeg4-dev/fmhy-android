#!/usr/bin/env bash
source /home/omega/Projects/fmhy-android-p9-search/.work-scripts/lib.sh
launch
sleep 3
tapt 'Search FMHY'; sleep 1.5
ty 'stremo' 1.5
shot 30-fuzzy-typo
tapt '^Clear search'; sleep .8
ty 'anime' 1.5
key KEYCODE_BACK; sleep .8
shot 31-three-categories
tapt 'View all'; sleep 1
shot 32-categories-expanded
tapt '^Clear search'; sleep .8
tapt 'Search FMHY'; sleep 1
for q in github.io archive.org 'tinyurl' 'discord.gg' 'telegram'; do ty "$q" 1.5; if python3 $UI has '^in (link|category|tags):'; then shot 33-extra-line; echo found $q; break; fi; tapt '^Clear search'; sleep .6; tapt 'Search FMHY'; sleep .8; done
