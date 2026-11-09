#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

missing=0

probe() {
    engine=$1
    expected=$2
    shift 2
    status=0
    "$@" > /dev/null 2>&1 || status=$?
    if [ "$status" -eq "$expected" ]; then
        echo "$engine: available"
    else
        echo "$engine is missing: $* exited $status, expected $expected" >&2
        missing=1
    fi
}

for engine in "$@"; do
    case "$engine" in
        spin)
            probe spin 0 spin -V
            # spin -V answers without gcc, which Spin preprocesses and compiles every search with
            model=$(mktemp -d)
            printf 'active proctype P() { skip }\n' > "$model/model.pml"
            probe 'spin pan' 0 sh -c 'cd "$1" && spin -a model.pml && gcc -o pan pan.c' sh "$model"
            rm -rf "$model"
            ;;
        nuxmv) probe nuxmv 2 nuXmv -h ;;
        uppaal) probe uppaal 0 verifyta -v ;;
        docker)
            if [ "$(docker info --format '{{.OSType}}' 2> /dev/null)" = linux ]; then
                echo "docker: available"
            else
                echo "docker is missing: docker info reports no daemon running Linux containers" >&2
                missing=1
            fi
            ;;
        *)
            echo "unknown engine $engine, expected spin, nuxmv, uppaal or docker" >&2
            exit 64
            ;;
    esac
done

exit "$missing"
