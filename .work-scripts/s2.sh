#!/usr/bin/env bash
source /home/omega/Projects/fmhy-android-p9-search/.work-scripts/lib.sh
launch
shot 01-recent
$A shell input swipe 540 1700 540 700 300; sleep 0.6; shot 02-recent-scrolled
$A shell input swipe 540 700 540 1700 300; sleep 0.4
tapt 'Search FMHY'; sleep 1.5
ty 'in:'
shot 03-in-suggestions
ty 'vi'
shot 04-in-vi
$A shell input tap 300 555; sleep 1
shot 05-pill-from-suggestion
ty 'anime'
shot 06-pill-and-text
ty '%sis:'
shot 07-is-suggestions
$A shell input tap 300 555; sleep 1
shot 08-two-pills
ty 'site:git'
shot 09-site-suggestions
$A shell input tap 300 555; sleep 1
shot 10-three-pills
for i in 1 2 3 4; do key KEYCODE_DEL; done
shot 11-after-backspaces
