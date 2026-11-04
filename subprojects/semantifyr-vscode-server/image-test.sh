#!/usr/bin/env sh

#
# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0
#

set -eu

IMAGE="$1"

if [ "$(docker info --format '{{.OSType}}' 2> /dev/null)" != "linux" ]; then
    echo "Docker with Linux containers is not available, skipping the image test"
    exit 0
fi

docker build --tag "$IMAGE" .

CONTAINER=$(docker run --detach --publish 127.0.0.1::3000 --entrypoint openvscode-server "$IMAGE" \
    --host 0.0.0.0 --port 3000 --without-connection-token --telemetry-level off --default-folder /home/jovyan/examples)
trap 'docker rm --force "$CONTAINER" > /dev/null' EXIT

EXTENSIONS=$(docker exec "$CONTAINER" openvscode-server --list-extensions)
for EXTENSION in ftsrg.semantifyr ftsrg.sysml-langium-editor; do
    if ! echo "$EXTENSIONS" | grep -qx "$EXTENSION"; then
        echo "$EXTENSION is not installed, the image has: $EXTENSIONS" >&2
        exit 1
    fi
done

docker exec "$CONTAINER" theta-xsts-cli --version

PORT=$(docker port "$CONTAINER" 3000/tcp | head -n 1 | sed 's/.*://')
for _ in $(seq 60); do
    if curl -fsS "http://127.0.0.1:$PORT/" > /dev/null 2>&1; then
        echo "openvscode-server answers with $(echo "$EXTENSIONS" | tr '\n' ' ')installed"
        exit 0
    fi
    sleep 2
done

docker logs "$CONTAINER" >&2
echo "openvscode-server did not answer on port $PORT" >&2
exit 1
