#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

if [ -n "${SONAR_TOKEN:-}" ]; then
    ./gradlew sonar
fi
