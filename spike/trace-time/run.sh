#!/bin/sh
set -u

here=$(cd "$(dirname "$0")" && pwd)
theta=${THETA_XSTS_CLI:-theta-xsts-cli}
theta_configuration=${THETA_CONFIGURATION:-CEGAR --domain EXPL_PRED_COMBINED --flatten-depth 0 --autoexpl NEWOPERANDS --initprec CTRL --stacktrace}
theta_timeout=${THETA_TIMEOUT:-120}
output=${1:-$here/out}
mkdir -p "$output"

milliseconds() {
    echo $(( $(date +%s%N) / 1000000 ))
}

verdict() {
    query=$1
    found=$2
    if [ "$query" = q5 ]; then
        if [ "$found" = yes ]; then echo violated; else echo holds; fi
    else
        if [ "$found" = yes ]; then echo reachable; else echo unreachable; fi
    fi
}

uppaal_delays() {
    awk '
        /^State:/ { getline location; if (location ~ /Model\.Running/) { if (seen) printf " %s", sum; seen = 1; sum = 0 } }
        /^Delay:/ { sum += $2 }
    ' "$1"
}

theta_delays() {
    awk '
        /post_init last_internal/ { internal++; next }
        /\(d / && internal > 1 && !printed[internal] { gsub(/[()]/, "", $0); printf " %s", $2; printed[internal] = 1 }
    ' "$1"
}

for variant in closed closed-invariant point point-invariant window window-invariant; do
    for index in 1 2 3 4 5 6; do
        query=q$index
        run=$output/uppaal-$variant-$query
        sed -n "${index}p" "$here/uppaal/queries.q" > "$run.q"
        start=$(milliseconds)
        verifyta -t 1 "$here/uppaal/$variant.xml" "$run.q" > "$run.out" 2>&1
        elapsed=$(( $(milliseconds) - start ))
        if grep -q "Formula is satisfied" "$run.out"; then satisfied=yes; else satisfied=no; fi
        if [ "$query" = q5 ]; then
            if [ "$satisfied" = yes ]; then found=no; else found=yes; fi
        else
            found=$satisfied
        fi
        rejected=$(grep -c "error" "$run.out")
        echo "uppaal $variant $query $(verdict "$query" "$found") ${elapsed}ms errors=$rejected delays:$(uppaal_delays "$run.out")"
    done
done

for variant in closed closed-unsaturated point point-unsaturated window window-unsaturated; do
    for index in 1 2 3 4 5 6; do
        query=q$index
        run=$output/theta-$variant-$query
        cat "$here/theta/$variant.xsts" "$here/theta/properties/$query.prop" > "$run.xsts"
        rm -f "$run.cex"
        start=$(milliseconds)
        timeout "$theta_timeout" "$theta" $theta_configuration --model "$run.xsts" --cexfile "$run.cex" > "$run.out" 2>&1
        status=$?
        elapsed=$(( $(milliseconds) - start ))
        if [ "$status" = 124 ]; then
            echo "theta $variant $query timeout ${theta_timeout}s"
            continue
        fi
        if grep -q "SafetyResult Unsafe" "$run.out"; then
            found=yes
        elif grep -q "SafetyResult Safe" "$run.out"; then
            found=no
        else
            echo "theta $variant $query failed status=$status ${elapsed}ms"
            continue
        fi
        delays=""
        if [ -f "$run.cex" ]; then delays=$(theta_delays "$run.cex"); fi
        echo "theta $variant $query $(verdict "$query" "$found") ${elapsed}ms delays:$delays"
    done
done
