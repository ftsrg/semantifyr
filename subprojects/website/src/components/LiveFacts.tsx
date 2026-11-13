/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';

interface Facts {
  backends: string[];
  verificationTimeoutSeconds: number;
}

// live's configuration, written by the generatedReference_live Gradle task
export function LiveBackends({ facts }: { facts: Facts }): React.JSX.Element {
  const { backends } = facts;
  const last = backends.at(-1);
  if (last === undefined) {
    throw new Error('live names no backend');
  }
  return <>{backends.length === 1 ? last : `${backends.slice(0, -1).join(', ')} and ${last}`}</>;
}

export function LiveLimit({ facts }: { facts: Facts }): React.JSX.Element {
  return <>{facts.verificationTimeoutSeconds} seconds</>;
}
