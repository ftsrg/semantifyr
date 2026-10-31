#!/bin/sh

#
# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0
#

# Prints what BenchExec 3.35's runexec prints; --fake-reason makes the run end on that limit.

reason=""
while [ "$1" != "--" ]; do
    case "$1" in
        --dir) dir="$2"; shift 2 ;;
        --output) output="$2"; shift 2 ;;
        --fake-reason) reason="$2"; shift 2 ;;
        *) shift ;;
    esac
done
shift

if [ -n "$dir" ]; then
    cd "$dir" || exit 1
fi
printf 'command line\n\n\n--------\n\n\n' > "$output"
"$@" >> "$output" 2>&1
code=$?

echo "starttime=2026-10-31T16:00:00.000000+01:00"
if [ -n "$reason" ]; then
    echo "terminationreason=$reason"
    echo "exitsignal=9"
else
    echo "returnvalue=$code"
fi
echo "walltime=0.500000000s"
echo "cputime=0.250000000s"
echo "cputime-cpu0=0.250000000s"
echo "memory=1048576B"
