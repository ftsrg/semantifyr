/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';

// a text taken from a source shows its backticked words as code, as the editors' hover does
export function withCode(text: string): React.ReactNode[] {
  return text.split('`').map((part, index) => {
    return index % 2 === 1 ? <code key={index}>{part}</code> : part;
  });
}
