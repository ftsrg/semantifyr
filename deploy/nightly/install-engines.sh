#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

user=nightly
packages="spin gcc openjdk-25-jdk-headless git curl unzip xz-utils"

usage() {
    echo "usage: $0 [--accept-nuxmv-license] [--accept-uppaal-license]" >&2
    exit 64
}

for argument in "$@"; do
    case "$argument" in
        --accept-nuxmv-license | --accept-uppaal-license) ;;
        *) usage ;;
    esac
done

if [ "$(id -u)" -ne 0 ]; then
    echo "run as root" >&2
    exit 1
fi
home=$(getent passwd "$user" | cut -d: -f6)
if [ -z "$home" ]; then
    echo "create the $user user first" >&2
    exit 1
fi

install_packages() {
    set --
    for package in $packages; do
        if dpkg-query -W -f '${Status}' "$package" 2> /dev/null | grep -q ' installed$'; then
            echo "skipped $package: installed"
        else
            set -- "$@" "$package"
        fi
    done
    if [ $# -gt 0 ]; then
        apt-get update -q
        DEBIAN_FRONTEND=noninteractive apt-get install -y -q --no-install-recommends "$@"
    fi
}
install_packages

# installing as the user keeps archive-supplied owners and modes out of root's hands
runuser -u "$user" -- sh -s -- "$@" "$home/tools" < "$(dirname "$0")/../../.github/scripts/install-engines.sh"
