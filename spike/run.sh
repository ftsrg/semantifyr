#!/bin/sh
# usage: run.sh <library> <model> <case> <portfolio> <output>
set -u
cli=/home/armin/work/ftsrg/semantifyr/.claude/worktrees/spike-trace-elapsed-time/subprojects/semantifyr-cli/build/install/semantifyr/bin/semantifyr
output=$5
rm -rf "$output"
mkdir -p "$output"
start=$(date +%s.%N)
"$cli" verify -l "$1" --case-name "$3" --portfolio "$4" -t "${TIMEOUT:-600}" -d "$output" --json "$2" > "$output/stdout.json" 2> "$output/stderr.log"
end=$(date +%s.%N)
awk "BEGIN{print $end - $start}" > "$output/wall.txt"
