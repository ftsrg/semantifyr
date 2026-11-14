#!/bin/sh
# waits until other heavy processes use under 40% CPU in a 5 second sample
waited=0
while :; do
    busy=$(top -b -n 2 -d 5 | awk '/^ *PID/{block++} block==2 && $12 ~ /^(java|pan|spin|verifyta|nuXmv|node|gradle|theta)/ {sum+=$9} END{print int(sum)}')
    if [ "$busy" -lt 40 ]; then
        echo "$waited $busy"
        exit 0
    fi
    sleep 10
    waited=$((waited + 15))
done
