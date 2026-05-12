/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useEffect, useRef, useState } from 'react'
import Box from '@mui/material/Box'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import type {
  VerificationCaseSpecification,
  VerificationTrace,
  WitnessValidationStatus,
} from '../../lib/verification'
import type { LiveEditorHandle } from '../editor/LiveEditor'
import CallTraceView from './CallTraceView'
import WitnessStateView from './WitnessStateView'
import ValidationChip from './ValidationChip'
import { FONT_SIZE } from '../../lib/util/theme';

interface Props {
  caseInfo: VerificationCaseSpecification
  trace: VerificationTrace
  validation: WitnessValidationStatus | undefined
  validating: boolean
  canRevalidate: boolean
  onRevalidate: () => void
  verificationPortfolioLabel?: string | undefined
  /** Used by the Raw view to mount a Monaco editor sharing the live LSP session. */
  editorHandle: LiveEditorHandle | null
  /** Language id for the witness; usually matches the source flavor's languageId. */
  witnessLanguageId: string
}

type ViewMode = 'trace' | 'witness' | 'raw'

const HEADER_BAR_SX = {
  display: 'flex',
  flexDirection: 'column',
  gap: 0.4,
  px: 1.5,
  py: 0.6,
  borderBottom: '1px solid var(--surface-border)',
  bgcolor: 'var(--surface-toolbar-bg)',
} as const

const TOGGLE_GROUP_SX = {
  // Override MUI's default groupedHorizontal rule which sets adjacent right borders
  // transparent and uses negative left-margin to share borders. With our colour scheme that
  // erases the rightmost border on certain themes; keep every button's own 1 px border and
  // drop the negative margin so dividers between buttons render reliably.
  '& .MuiToggleButtonGroup-grouped': {
    color: 'text.secondary',
    border: '1px solid var(--surface-border)',
    textTransform: 'none',
    fontSize: FONT_SIZE.xs,
    px: 1,
    py: 0.1,
    '&:not(:first-of-type)': {
      borderLeft: '1px solid var(--surface-border)',
      marginLeft: 0,
    },
  },
  // Selected toggle: lift via --button-bg-hover (brighter than toolbar bg in dark, darker in
  // light) so it reads as "raised" in either theme, plus the accent foreground for emphasis.
  '& .Mui-selected, & .Mui-selected:hover': {
    color: 'var(--accent) !important',
    bgcolor: 'var(--button-bg-hover) !important',
    border: '1px solid var(--accent) !important',
  },
  '& .MuiToggleButtonGroup-grouped:hover': {
    bgcolor: 'var(--button-bg)',
  },
} as const

function RawWitnessEditor({
  editorHandle,
  witnessUri,
  language,
}: {
  editorHandle: LiveEditorHandle | null
  witnessUri: string
  language: string
}): React.JSX.Element {
  const hostRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!editorHandle || !hostRef.current) return
    const host = hostRef.current
    let cancelled = false
    let dispose: (() => void) | null = null
    void editorHandle
      .attachReadonlyEditor(host, witnessUri, language)
      .then((handle) => {
        if (cancelled) {
          handle.dispose()
          return
        }
        dispose = handle.dispose
      })
      .catch((error) => {
        console.warn('semantifyr-live: failed to attach witness editor', error)
      })
    return () => {
      cancelled = true
      try {
        dispose?.()
      } catch {
        /* ignore */
      }
    }
    // Re-attach when the underlying witness changes (new verification run produces a new URI).
  }, [editorHandle, witnessUri, language])

  return (
    <Box
      ref={hostRef}
      sx={{
        flex: '1 1 auto',
        minHeight: 0,
        minWidth: 0,
        bgcolor: 'var(--page-bg)',
      }}
    />
  )
}

export default function WitnessTab({
  caseInfo,
  trace,
  validation,
  validating,
  canRevalidate,
  onRevalidate,
  verificationPortfolioLabel,
  editorHandle,
  witnessLanguageId,
}: Props): React.JSX.Element {
  const [view, setView] = useState<ViewMode>('trace')
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 0', minHeight: 0 }}>
      <Box sx={HEADER_BAR_SX}>
        {/* Title line: case label, sized to fit alongside compact metadata. Long labels wrap
            via wordBreak; the label leads so it scans as the heading. */}
        <Typography
          sx={{
            fontSize: FONT_SIZE.md,
            fontWeight: 600,
            color: 'text.primary',
            wordBreak: 'break-word',
            lineHeight: 1.25,
          }}
        >
          {caseInfo.label}
        </Typography>
        {/* Metadata + view-toggle row: portfolio label and chip on the left, view-toggle on
            the right. Wraps to a third visual row only when the chip + toggles can't fit. */}
        <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 0.75, rowGap: 0.4 }}>
          {verificationPortfolioLabel && (
            <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}>
              verified using <Box component="span" sx={{ color: 'text.primary' }}>{verificationPortfolioLabel}</Box>
            </Typography>
          )}
          <ValidationChip
            status={validation}
            busy={validating}
            disabled={!canRevalidate}
            onRevalidate={onRevalidate}
          />
          <Box sx={{ flex: 1 }} />
          <ToggleButtonGroup
            size="small"
            value={view}
            exclusive
            onChange={(_, next) => {
              if (next) {
                setView(next)
              }
            }}
            sx={TOGGLE_GROUP_SX}
          >
            <ToggleButton value="trace">Trace</ToggleButton>
            <ToggleButton value="witness">State</ToggleButton>
            <ToggleButton value="raw">Raw</ToggleButton>
          </ToggleButtonGroup>
        </Box>
      </Box>
      <Box sx={{ flex: '1 1 auto', minHeight: 0, minWidth: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        {view === 'trace' && <CallTraceView trace={trace.callTrace} />}
        {view === 'witness' && <WitnessStateView trace={trace.witnessState} />}
        {view === 'raw' && (
          trace.witnessUri ? (
            <RawWitnessEditor
              editorHandle={editorHandle}
              witnessUri={trace.witnessUri}
              language={witnessLanguageId}
            />
          ) : (
            <Box sx={{ flex: '1 1 auto', p: 1.5 }}>
              <Typography sx={{ fontSize: FONT_SIZE.md, color: 'text.secondary', fontStyle: 'italic' }}>
                Witness source unavailable.
              </Typography>
            </Box>
          )
        )}
      </Box>
    </Box>
  )
}
