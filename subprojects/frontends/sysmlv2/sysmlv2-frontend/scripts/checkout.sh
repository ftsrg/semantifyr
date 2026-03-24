#!/usr/bin/env sh

#
# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0
#

set -e

TARGET_DIR="$1"
REMOTE_URL="$2"
COMMIT="$3"

if [ -z "$TARGET_DIR" ] || [ -z "$REMOTE_URL" ] || [ -z "$COMMIT" ]; then
    echo "Usage: checkout.sh <target-dir> <remote-url> <commit>"
    exit 1
fi

if [ ! -d "$TARGET_DIR/.git" ]; then
    git clone "$REMOTE_URL" "$TARGET_DIR"
fi

cd "$TARGET_DIR"

git fetch origin "$COMMIT"
git checkout "$COMMIT"
