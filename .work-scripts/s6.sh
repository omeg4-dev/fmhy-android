#!/usr/bin/env bash
source /home/omega/Projects/fmhy-android-p9-search/.work-scripts/lib.sh
ORIG=$($A shell cmd uimode night | sed 's/.*: //')
echo "orig night mode: $ORIG"
run() { # $1 = prefix
launch
shot $1-01-recent
tapt 'Search FMHY'; sleep 1.5
ty 'is:'
shot $1-02-is-suggestions
$A shell input tap 300 555; sleep .8
ty 'in:vi'; $A shell input tap 300 555; sleep .8
ty 'site:github.com%s' 1; ty '-foo%s' 1; ty '-bar%s' 1; ty 'anime' 1
shot $1-03-pills-wrapped
key KEYCODE_BACK; sleep .6
tapt '^Clear search'; sleep .6
tapt 'Search FMHY'; sleep 1
ty 'anime' 1.5; key KEYCODE_BACK; sleep .8
shot $1-04-three-categories
tapt '^Clear search'; sleep .6; tapt 'Search FMHY'; sleep 1
ty 'discord.gg' 1.8; key KEYCODE_BACK; sleep .8
shot $1-05-highlight-and-in-link
}
$A shell cmd uimode night no; sleep 2
run light
$A shell cmd uimode night yes; sleep 2
launch; tapt 'Search FMHY'; sleep 1.5
ty 'is:starred%sin:vi' 1; $A shell input tap 300 555; sleep .8; ty 'site:github.com%s' 1; ty '-foo%s' 1; ty '-bar%s' 1; ty 'anime' 1
shot dark-03-pills-wrapped
$A shell cmd uimode night $ORIG
