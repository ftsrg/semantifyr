#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

nuxmv_version=2.1.0
nuxmv_sha256=c7dfec43749bcb230c857efe81099b95d868b94efd9f81bccebe542a306a7c83
uppaal_version=5.0.0
uppaal_sha256=86d66d8cd25c00f6f45156617f3a7566f1f4fe7aed74dd171bfc9ef27a2c61ce

usage() {
    echo "usage: install-engines.sh [--accept-nuxmv-license] [--accept-uppaal-license] <directory>" >&2
    exit 64
}

accept_nuxmv=false
accept_uppaal=false
tools=
for argument in "$@"; do
    case "$argument" in
        --accept-nuxmv-license) accept_nuxmv=true ;;
        --accept-uppaal-license) accept_uppaal=true ;;
        -*) usage ;;
        *)
            [ -z "$tools" ] || usage
            tools=$argument
            ;;
    esac
done
[ -n "$tools" ] || usage

mkdir -p "$tools/bin" "$tools/downloads"

installed() {
    [ -x "$tools/$1/bin/$2" ] && [ "$(readlink "$tools/bin/$2")" = "../$1/bin/$2" ]
}

downloaded() {
    echo "$2  $tools/downloads/$1" | sha256sum -c --status 2> /dev/null
}

download() {
    if ! curl -fsSL -o "$tools/downloads/$1" "$3"; then
        echo "could not download $1 from $3" >&2
        exit 1
    fi
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
    rm -rf "$tools/$nuxmv"
    mkdir "$tools/$nuxmv"
    tar -xJf "$tools/downloads/$nuxmv_archive" -C "$tools/$nuxmv" --strip-components 1
    ln -sfn "../$nuxmv/bin/nuXmv" "$tools/bin/nuXmv"
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
    rm -rf "$tools/$uppaal"
    unzip -q "$tools/downloads/$uppaal_archive" -d "$tools"
    ln -sfn "../$uppaal/bin/verifyta" "$tools/bin/verifyta"
    echo "installed $uppaal"
fi
