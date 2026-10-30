/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import ButtonBase from '@mui/material/ButtonBase'
import { FONT_SIZE } from '../../lib/util/theme'

interface Props {
  label: string
  ariaLabel: string
  orientation: 'vertical' | 'horizontal'
  onClick: () => void
}

// Both rails share this thin dimension so the side and bottom collapsed affordances match.
const THIN_PX = 22

export default function ReopenRail({ label, ariaLabel, orientation, onClick }: Props): React.JSX.Element {
  const sx = orientation === 'vertical'
    ? {
        flex: '0 0 auto',
        flexDirection: { xs: 'row' as const, md: 'column' as const },
        borderLeft: { xs: 'none', md: '1px solid var(--surface-border)' },
        borderTop: { xs: '1px solid var(--surface-border)', md: 'none' },
        writingMode: { xs: 'horizontal-tb' as const, md: 'vertical-rl' as const },
        transform: { xs: 'none', md: 'rotate(180deg)' },
        minWidth: { xs: '100%', md: THIN_PX },
        minHeight: { xs: THIN_PX, md: 0 },
        px: { xs: 1, md: 0 },
        py: { xs: 0, md: 1 },
      }
    : {
        borderTop: '1px solid var(--surface-border)',
        minHeight: THIN_PX,
      }
  return (
    <ButtonBase
      onClick={onClick}
      aria-label={ariaLabel}
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'var(--surface-toolbar-bg)',
        color: 'text.secondary',
        fontSize: FONT_SIZE.xs,
        '&:hover': { bgcolor: 'var(--surface-bg)', color: 'text.primary' },
        ...sx,
      }}
    >
      {label}
    </ButtonBase>
  )
}
