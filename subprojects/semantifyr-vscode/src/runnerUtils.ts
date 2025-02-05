/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as os from "node:os";

export const runnerUtils = os.type() === 'Windows_NT' ? 'cmd' : 'sh';
export const commandArg = os.type() === 'Windows_NT' ? '/c' : '-c';
export const executablePostfix = os.type() === 'Windows_NT' ? '.bat' : '';
