#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

image=$1

container=$(docker run --detach --publish 127.0.0.1::8888 --env JUPYTER_TOKEN=smoke "$image")
trap 'docker rm --force "$container" > /dev/null' EXIT

extensions=$(docker exec "$container" openvscode-server --list-extensions)
for extension in ftsrg.semantifyr ftsrg.sysml-langium-editor; do
    if ! echo "$extensions" | grep -qx "$extension"; then
        echo "$extension is not installed, the image has: $extensions" >&2
        exit 1
    fi
done

docker exec "$container" theta-xsts-cli --version

port=$(docker port "$container" 8888/tcp | head -n 1 | sed 's/.*://')
for _ in $(seq 60); do
    if curl -fsS --max-time 30 -H 'Authorization: token smoke' "http://127.0.0.1:$port/semantifyr/" > /dev/null 2>&1; then
        exit 0
    fi
    sleep 2
done

docker logs "$container" >&2
echo "openvscode-server did not answer behind the Jupyter proxy on port $port" >&2
exit 1
