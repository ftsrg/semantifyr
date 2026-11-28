#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

available() {
    case "$1" in
        spin)
            # spin -V answers without gcc, which Spin preprocesses and compiles every search with
            model=$(mktemp -d)
            printf 'active proctype P() { skip }\n' > "$model/model.pml"
            (cd "$model" && spin -a model.pml && gcc -o pan pan.c) > /dev/null 2>&1
            status=$?
            rm -rf "$model"
            return "$status"
            ;;
        nuxmv) nuXmv -h > /dev/null 2>&1; [ $? -eq 2 ] ;;
        uppaal) verifyta -v > /dev/null 2>&1 ;;
        docker) [ "$(docker info --format '{{.OSType}}' 2> /dev/null)" = linux ] ;;
        *) echo "unknown backend $1, expected spin, nuxmv, uppaal or docker" >&2; exit 64 ;;
    esac
}

missing=0
for backend in "$@"; do
    if available "$backend"; then
        echo "$backend: available"
    else
        echo "$backend is missing" >&2
        missing=1
    fi
done
exit "$missing"
