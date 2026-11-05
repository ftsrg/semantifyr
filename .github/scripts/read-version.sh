#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

commit=$1

version=$(sed -n 's/^version=//p' gradle.properties)
echo "$version" | grep -Eqx '[0-9]+\.[0-9]+\.[0-9]+'
echo "version=$version" >> "$GITHUB_OUTPUT"
echo "snapshot=$version-dev.$(echo "$commit" | cut -c1-7)" >> "$GITHUB_OUTPUT"
