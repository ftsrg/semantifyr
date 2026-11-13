/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';

export default function Buttons({ className }: { className?: string }): React.JSX.Element {
  return (
    <div className={className}>
      <button type="button">Word wrap</button>
      <button type="button">Copy</button>
    </div>
  );
}
