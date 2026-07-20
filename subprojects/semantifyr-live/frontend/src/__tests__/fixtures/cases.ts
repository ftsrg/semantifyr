/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerificationCaseSpecification } from '@semantifyr/editor-common';

export function sampleCase(id: string): VerificationCaseSpecification {
  return {
    id,
    label: `Case ${id}`,
    location: {
      uri: 'inmemory:///workspace/snippet.oxsts',
      range: {
        start: { line: 0, character: 0 },
        end: { line: 0, character: 0 },
      },
    },
  };
}
