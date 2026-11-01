#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

cd "$(dirname "$0")"

tag=$1
previous=$(sed -n 's/^IMAGE_TAG=//p' .env)

wait_healthy() {
    container=$(docker compose ps -q live) || return 1
    [ -n "$container" ] || return 1
    [ "$(docker inspect -f '{{.Config.Image}}' "$container")" = "$1" ] || return 1
    for _ in $(seq 60); do
        case "$(docker inspect -f '{{.State.Health.Status}}' "$container")" in
            healthy) return 0 ;;
            unhealthy) return 1 ;;
        esac
        sleep 3
    done
    return 1
}

# errexit does not apply inside a function called as an if condition, so every step checks itself
run() {
    sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" .env || return 1
    docker compose pull --quiet || return 1
    docker compose up -d --remove-orphans || return 1
    wait_healthy "$(docker compose config --images)" || return 1
}

# the other instance shares the image store, so an image any container uses stays as well
prune() {
    repository=$(docker compose config --images) && [ -n "$repository" ] || return 1
    repository=${repository%:*}
    repository=${repository#docker.io/}
    in_use=$(docker ps -a --format '{{.Image}}') || return 1
    for image in $(docker image ls --format '{{.Repository}}:{{.Tag}}' | sed 's|^docker\.io/||'); do
        case "$image" in
            "$repository:$tag" | "$repository:$previous" | *:"<none>") ;;
            "$repository":*)
                printf '%s\n' "$in_use" | sed 's|^docker\.io/||' | grep -Fqx "$image" && continue
                if docker image rm "$image" > /dev/null; then echo "removed $image"; fi
                ;;
        esac
    done
}

if run "$tag"; then
    echo "deployed $tag"
    prune || echo "pruning the old local images failed" >&2
    exit 0
fi

echo "$tag failed to deploy" >&2
docker compose logs --tail 100 live >&2 || true

if [ -n "$previous" ] && [ "$previous" != "$tag" ]; then
    if run "$previous"; then
        echo "rolled back to $previous" >&2
    else
        echo "rollback to $previous failed too" >&2
    fi
fi
exit 1
