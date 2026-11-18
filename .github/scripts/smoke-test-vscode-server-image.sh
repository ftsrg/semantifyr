#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

image=$1

container=$(docker run --detach --publish 127.0.0.1::3000 --entrypoint openvscode-server "$image" \
    --host 0.0.0.0 --port 3000 --without-connection-token --telemetry-level off --default-folder /home/jovyan/examples)
trap 'docker rm --force "$container" > /dev/null' EXIT

extensions=$(docker exec "$container" openvscode-server --list-extensions)
for extension in ftsrg.semantifyr ftsrg.sysml-langium-editor; do
    if ! echo "$extensions" | grep -qx "$extension"; then
        echo "$extension is not installed, the image has: $extensions" >&2
        exit 1
    fi
done

docker exec "$container" theta-xsts-cli --version

port=$(docker port "$container" 3000/tcp | head -n 1 | sed 's/.*://')
for _ in $(seq 60); do
    if curl -fsS "http://127.0.0.1:$port/" > /dev/null 2>&1; then
        exit 0
    fi
    sleep 2
done

docker logs "$container" >&2
echo "openvscode-server did not answer on port $port" >&2
exit 1
