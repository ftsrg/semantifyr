#!/usr/bin/env sh

#
# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0
#

TARGET_DIR="$1"
REMOTE_URL="$2"
COMMIT="$3"

if [ -z "$TARGET_DIR" ] || [ -z "$REMOTE_URL" ] || [ -z "$COMMIT" ]; then
    echo "Usage: checkout.sh <target-dir> <remote-url> <commit>"
    exit 1
fi

cd "$TARGET_DIR"

git init
echo "Adding remote"
git remote add origin "$REMOTE_URL" 2> /dev/null || true
echo "Fetch latest"
git fetch
echo "Checkout commit"
git checkout "$COMMIT"
