#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -u

PATH=$HOME/tools/bin:$PATH
failed=0

probe() {
    label=$1
    expected=$2
    pattern=$3
    shift 3
    output=$("$@" 2>&1)
    status=$?
    line=$(printf '%s\n' "$output" | grep -m 1 -- "$pattern")
    if [ "$status" -eq "$expected" ] && [ -n "$line" ]; then
        echo "$label: $line"
    else
        echo "$label: missing, $* exited $status: $(printf '%s\n' "$output" | head -n 1)" >&2
        failed=1
    fi
}

probe spin 0 'Spin Version' spin -V
probe nuxmv 2 'This is nuXmv' nuXmv -h
probe uppaal 0 'UPPAAL' verifyta -v
probe gcc 0 'gcc' gcc --version
probe java 0 'version' java -version

# verifyta -v answers without a licence, so only a verification proves the key step
model=$(mktemp -d)
trap 'rm -rf "$model"' EXIT
printf 'process P() { state s; init s; }\nsystem P;\n' > "$model/model.xta"
printf 'A[] true\n' > "$model/query.q"
probe 'uppaal licence' 0 'Formula is satisfied' verifyta -q -s "$model/model.xta" "$model/query.q"

exit "$failed"
