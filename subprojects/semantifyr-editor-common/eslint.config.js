/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { baseConfig } from '../../eslint.config.base.js';

export default baseConfig({
  tsconfigRootDir: path.dirname(fileURLToPath(import.meta.url)),
});
