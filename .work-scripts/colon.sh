source /home/omega/Projects/fmhy-android-p9-search/.work-scripts/lib.sh
launch
tapt 'Search FMHY'
ty 'in:' 1; python3 $UI dump | sed -n 1,3p
ty 'vi' 1; python3 $UI dump | sed -n 1,3p
clearq
ty 'in' 1; ty ':' 1;  python3 $UI dump | sed -n 1,3p
