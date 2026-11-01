#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

cd "$(dirname "$0")"

release=$1
files=
for path in "$release"/*; do
    [ -f "$path" ] || { echo "${path##*/} is not a file" >&2; exit 65; }
    file=${path##*/}
    [ "$file" = deploy.sh ] || files="$files $file"
done

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
        [ ! -f "$file" ] || cp -p "$file" previous/
    done
    for file in $changed; do
        # containers read mounted files as their own unprivileged users
        install -m 644 "$release/$file" "$file"
        # compose recreates a service when its definition changes, not when a file it mounts does
        [ "$file" = compose.yaml ] || recreate=--force-recreate
    done
fi

if docker compose up -d --remove-orphans ${recreate:+"$recreate"}; then
    echo "edge deployed, changed:${changed:- nothing}"
    exit 0
fi

echo "edge failed to start" >&2
docker compose logs --tail 50 >&2 || true

if [ -n "$changed" ]; then
    cp -p previous/* .
    if docker compose up -d --remove-orphans --force-recreate; then
        echo "restored the previous edge" >&2
        exit 70
    fi
fi
exit 71
