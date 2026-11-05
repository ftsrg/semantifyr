#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

version=$1

if gh release view "v$version" > /dev/null 2>&1; then
    echo "v$version is already released; bump gradle.properties" >&2
    exit 1
fi
