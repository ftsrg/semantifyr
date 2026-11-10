/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

const GLYPHS = {
  'pause': <path d="M9 6.5v11M15 6.5v11" />,
  'sign-out': (
    <>
      <path d="M10 4.5H6a1 1 0 0 0-1 1v13a1 1 0 0 0 1 1h4" />
      <path d="M14 8l4 4-4 4M18 12H9.5" />
    </>
  ),
  'chevron-right': <path d="M10 7l5 5-5 5" />,
}

export function AdminIcon({ name }: { name: keyof typeof GLYPHS }): React.JSX.Element {
  return (
    <svg className="icon icon-small" viewBox="0 0 24 24" aria-hidden="true">
      {GLYPHS[name]}
    </svg>
  )
}
