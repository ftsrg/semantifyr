#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

usage() {
    echo "usage: prune-images.sh [--dry-run] docker.io/<namespace>/<repository>" >&2
    exit 64
}

dry_run=false
if [ "${1:-}" = --dry-run ]; then
    dry_run=true
    shift
fi
[ $# -eq 1 ] || usage
name=${1#docker.io/}
case "$name" in
    */*/*) usage ;;
    ?*/?*) ;;
    *) usage ;;
esac
: "${DOCKERHUB_USERNAME:?}" "${DOCKERHUB_TOKEN:?}"

api=${DOCKERHUB_API:-https://hub.docker.com}/v2
tags=$api/namespaces/${name%/*}/repositories/${name#*/}/tags
cutoff=$(date -u -d '90 days ago' +%Y-%m-%dT%H:%M:%SZ)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

token=$(jq -n '{identifier: env.DOCKERHUB_USERNAME, secret: env.DOCKERHUB_TOKEN}' \
    | curl -fsS -H 'Content-Type: application/json' --data @- "$api/auth/token" \
    | jq -er .access_token)

# the header goes through stdin so the token never shows in a process list
hub() {
    printf 'Authorization: Bearer %s\n' "$token" | curl -fsS -H @- "$@"
}

page=1
while :; do
    hub "$tags?page=$page&page_size=100" > "$work/$page.json"
    next=$(jq -r .next "$work/$page.json")
    [ "$next" != null ] || break
    page=$((page + 1))
done

# the newest dev tags stay at any age, so a server left alone for 90 days can still redeploy and roll back
jq -rs --arg cutoff "$cutoff" --argjson newest 10 '
    [.[].results[] | {name, pushed: (.tag_last_pushed // .last_updated)}]
    | sort_by(.pushed) | reverse
    | (map(select(.name | test("^[0-9]+\\.[0-9]+\\.[0-9]+-dev\\.[0-9a-f]{7,40}$")))
        | .[$newest:] | map(select(.pushed != null and .pushed < $cutoff) | .name)) as $expired
    | .[] | "\(if IN(.name; $expired[]) then "delete" else "keep" end) \(.name) \(.pushed)"' \
    "$work"/*.json > "$work/plan"

while read -r action tag pushed; do
    if [ "$action" = keep ]; then
        echo "keep $tag ($pushed)"
    elif $dry_run; then
        echo "would delete $tag ($pushed)"
    else
        hub -X DELETE "$tags/$tag" > /dev/null
        echo "deleted $tag ($pushed)"
    fi
done < "$work/plan"
