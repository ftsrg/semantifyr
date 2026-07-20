/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import Chip from '@mui/material/Chip'
import type { VerificationKind } from '../../lib/api/types'

interface Props {
  kind: VerificationKind
  density?: 'default' | 'compact'
}

const SX_BY_KIND: Record<VerificationKind, { bg: string; fg: string }> = {
  Verify: { bg: 'var(--warning-soft-bg)', fg: 'var(--warning)' },
  Validate: { bg: 'rgba(96,165,250,0.18)', fg: 'var(--accent)' },
}

const LABEL_BY_KIND: Record<VerificationKind, string> = {
  Verify: 'verification',
  Validate: 'validation',
}

export default function KindChip({ kind, density = 'default' }: Props): React.JSX.Element {
  const { bg, fg } = SX_BY_KIND[kind]
  const isCompact = density === 'compact'
  return (
    <Chip
      label={LABEL_BY_KIND[kind]}
      size="small"
      sx={{
        bgcolor: bg,
        color: fg,
        fontWeight: 600,
        fontSize: isCompact ? '0.65rem' : '0.7rem',
        height: isCompact ? 18 : 20,
      }}
    />
  )
}
