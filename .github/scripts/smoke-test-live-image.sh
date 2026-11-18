#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

container=$1
image=$2

docker run -d --name "$container" -p 8080:8080 --cap-drop ALL --security-opt no-new-privileges:true "$image"
started=false
for _ in $(seq 60); do
    if curl -fsS http://localhost:8080/api/health > /dev/null 2>&1; then
        started=true
        break
    fi
    sleep 3
done
if [ "$started" = false ]; then
    echo "the live backend did not answer its health endpoint" >&2
    exit 1
fi
curl -fsS http://localhost:8080/sysml-worker/syside-worker.cjs > /dev/null
curl -fsS http://localhost:8080/sysml-worker/library/Verification.sysml > /dev/null
test "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Auth-Request-User: smoke' http://localhost:8080/api/admin/status)" = 403
