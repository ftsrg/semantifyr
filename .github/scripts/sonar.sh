#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

required_backends=$1

if [ -n "${SONAR_TOKEN:-}" ]; then
    ./gradlew sonar -Ptests.requiredBackends="$required_backends"
fi
