#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

cd "$(dirname "$0")"

release=$1
files=$(cd "$release" && find . -type f ! -path ./deploy.sh | sed 's|^\./||' | sort)

# the tunnel drops while cloudflared restarts, so everything that can fail runs before it
docker compose --project-directory . -f "$release/compose.yaml" config --quiet || exit 65
docker compose --project-directory . -f "$release/compose.yaml" pull --quiet || exit 69

changed=
for file in $files; do
    cmp -s "$release/$file" "$file" || changed="$changed $file"
done

recreate=
if [ -n "$changed" ]; then
    rm -rf previous
    mkdir previous
    for file in $files; do
        if [ -f "$file" ]; then
            install -d "previous/$(dirname "$file")"
            cp -p "$file" "previous/$file"
        fi
    done
    for file in $changed; do
        # containers read mounted files as their own unprivileged users
        install -d -m 755 "$(dirname "$file")"
        install -m 644 "$release/$file" "$file"
        # compose recreates a service when its definition changes, not when a file it mounts does
        [ "$file" = compose.yaml ] || recreate=--force-recreate
    done
fi

if docker compose up -d --wait --wait-timeout 180 --remove-orphans ${recreate:+"$recreate"}; then
    echo "${PWD##*/} deployed, changed:${changed:- nothing}"
    exit 0
fi

echo "${PWD##*/} failed to start" >&2
docker compose logs --tail 50 >&2 || true

if [ -n "$changed" ]; then
    cp -pR previous/. .
    if docker compose up -d --wait --wait-timeout 180 --remove-orphans --force-recreate; then
        echo "restored the previous ${PWD##*/}" >&2
        exit 70
    fi
fi
exit 71
