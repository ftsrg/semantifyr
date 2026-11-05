#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

commit=$1
directory=$2
shift 2

run=$(gh run list --workflow cd.yml --commit "$commit" --status success --limit 1 --json databaseId --jq '.[0].databaseId')
if [ -z "$run" ]; then
    echo "$commit has no successful Continuous Delivery run" >&2
    exit 1
fi
# one artifact per call, since gh puts each artifact of a multi-artifact download in a directory of its own
for artifact in "$@"; do
    gh run download "$run" --name "$artifact" --dir "$directory"
done
