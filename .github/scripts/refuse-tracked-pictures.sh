#!/bin/sh

# SPDX-FileCopyrightText: 2026 The Semantifyr Authors
#
# SPDX-License-Identifier: EPL-2.0

set -eu

tracked=$(git ls-files -- 'branding/*.png' 'branding/*.ico' 'subprojects/semantifyr-vscode/icons')

if [ -n "$tracked" ]; then
    echo "raster pictures are generated from branding/*.svg and never committed:" >&2
    echo "$tracked" >&2
    exit 1
fi
