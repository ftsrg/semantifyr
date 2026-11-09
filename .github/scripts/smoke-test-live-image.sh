#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

container=$1
image=$2

docker run -d --name "$container" -p 8080:8080 --cap-drop ALL --security-opt no-new-privileges:true "$image"
for _ in $(seq 60); do
    curl -fsS http://localhost:8080/api/health > /dev/null && break
    sleep 3
done
curl -fsS http://localhost:8080/api/health
curl -fsS http://localhost:8080/ > /dev/null
curl -fsS http://localhost:8080/sysml-worker/syside-worker.cjs > /dev/null
curl -fsS http://localhost:8080/sysml-worker/library/Verification.sysml > /dev/null
test "$(curl -s -o /dev/null -w '%{http_code}' -H 'X-Auth-Request-User: smoke' http://localhost:8080/api/admin/status)" = 403
docker exec "$container" theta-xsts-cli --version
