#!/bin/sh
# CPU ticks used so far by heavy processes outside this spike
total=0
for directory in /proc/[0-9]*; do
    command=$(tr '\0' ' ' < "$directory/cmdline" 2>/dev/null) || continue
    case "$command" in
        *spike-trace-elapsed-time*|*scratchpad/elapsed*) continue ;;
        *java*|*pan*|*spin*|*verifyta*|*nuXmv*|*node*) ;;
        *) continue ;;
    esac
    ticks=$(awk '{print $14 + $15}' "$directory/stat" 2>/dev/null) || continue
    total=$((total + ${ticks:-0}))
done
echo $total
