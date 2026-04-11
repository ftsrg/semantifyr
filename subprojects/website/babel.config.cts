/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { TransformOptions } from '@babel/core';

module.exports = {
  presets: [require.resolve('@docusaurus/core/lib/babel/preset')],
} satisfies TransformOptions;
