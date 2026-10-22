#!/usr/bin/env bash
source /home/omega/Projects/fmhy-android-p9-search/.work-scripts/lib.sh
$A logcat -b crash -c
launch
tapt "What's new on FMHY"; sleep 2
shot 40-whats-new-from-search
echo "--- crash log:"; $A logcat -d -b crash | head -5
