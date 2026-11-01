#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

user=nightly
packages="spin gcc openjdk-25-jdk-headless git curl unzip xz-utils"
nuxmv_version=2.1.0
nuxmv_sha256=c7dfec43749bcb230c857efe81099b95d868b94efd9f81bccebe542a306a7c83
uppaal_version=5.0.0
uppaal_sha256=86d66d8cd25c00f6f45156617f3a7566f1f4fe7aed74dd171bfc9ef27a2c61ce

usage() {
    echo "usage: $0 [--accept-nuxmv-license] [--accept-uppaal-license]" >&2
    exit 64
}

accept_nuxmv=false
accept_uppaal=false
for argument in "$@"; do
    case "$argument" in
        --accept-nuxmv-license) accept_nuxmv=true ;;
        --accept-uppaal-license) accept_uppaal=true ;;
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
tools=$home/tools

# extracting as the user keeps archive-supplied owners and modes out of root's hands
as_user() {
    runuser -u "$user" -- "$@"
}

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

as_user mkdir -p "$tools/bin" "$tools/downloads"

installed() {
    [ -x "$tools/$1/bin/$2" ] && [ "$(readlink "$tools/bin/$2")" = "../$1/bin/$2" ]
}

downloaded() {
    echo "$2  $tools/downloads/$1" | sha256sum -c --status 2> /dev/null
}

download() {
    as_user curl -fsSL -o "$tools/downloads/$1" "$3"
    if ! downloaded "$1" "$2"; then
        echo "$1 does not match its pinned sha256" >&2
        exit 1
    fi
}

nuxmv=nuXmv-$nuxmv_version
nuxmv_archive=$nuxmv-linux64.tar.xz
if installed "$nuxmv" nuXmv; then
    echo "skipped nuXmv: $nuxmv installed"
elif ! downloaded "$nuxmv_archive" "$nuxmv_sha256" && [ "$accept_nuxmv" != true ]; then
    echo "skipped nuXmv: accept https://nuxmv.fbk.eu/license.html with --accept-nuxmv-license"
else
    if ! downloaded "$nuxmv_archive" "$nuxmv_sha256"; then
        download "$nuxmv_archive" "$nuxmv_sha256" "https://nuxmv.fbk.eu/theme/download.php?file=$nuxmv_archive"
    fi
    as_user rm -rf "$tools/$nuxmv"
    as_user mkdir "$tools/$nuxmv"
    as_user tar -xJf "$tools/downloads/$nuxmv_archive" -C "$tools/$nuxmv" --strip-components 1
    as_user ln -sfn "../$nuxmv/bin/nuXmv" "$tools/bin/nuXmv"
    echo "installed $nuxmv"
fi

uppaal=uppaal-$uppaal_version-linux64
uppaal_archive=$uppaal.zip
if installed "$uppaal" verifyta; then
    echo "skipped Uppaal: $uppaal installed"
elif ! downloaded "$uppaal_archive" "$uppaal_sha256" && [ "$accept_uppaal" != true ]; then
    echo "skipped Uppaal: accept the licence at https://uppaal.org/downloads with --accept-uppaal-license"
else
    if ! downloaded "$uppaal_archive" "$uppaal_sha256"; then
        download "$uppaal_archive" "$uppaal_sha256" "https://download.uppaal.org/uppaal-${uppaal_version%.*}/uppaal-$uppaal_version/$uppaal_archive"
    fi
    as_user rm -rf "$tools/$uppaal"
    as_user unzip -q "$tools/downloads/$uppaal_archive" -d "$tools"
    as_user ln -sfn "../$uppaal/bin/verifyta" "$tools/bin/verifyta"
    echo "installed $uppaal"
fi
