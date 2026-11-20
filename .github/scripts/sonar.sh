#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

if [ -z "${SONAR_TOKEN:-}" ]; then
    exit 0
fi

# a failed quality gate stops a pull request's merge; on main the scan stops no delivery
if [ "${GITHUB_EVENT_NAME:-}" = pull_request ]; then
    ./gradlew sonar -Dsonar.qualitygate.wait=true
else
    ./gradlew sonar
fi
