/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react'
import Box from '@mui/material/Box'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import StopIcon from '@mui/icons-material/Stop'
import {
  formatIsoDurationDetailed,
  isoDurationFromMs,
  isoDurationToMs,
} from '../../lib/util/duration'
import { findPortfolioLabel } from '../../lib/verification/portfolioLabel'
import { useActiveVerifications } from '../../lib/hooks/useActiveVerifications'
import { isMeaningfulDuration } from '../../lib/verification/metricsTooltip'
import { CaseStatusIcon } from './StatusDisplay'
import KindChip from './KindChip'
import type { SemantifyrLiveApi } from '../../lib/api/lspExtensions'
import type { PortfolioInfo } from '../../lib/api'
import type { VerificationCaseState, VerificationCaseStatus } from '../../lib/verification'
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  api: SemantifyrLiveApi | null
  connected: boolean
  cases: readonly VerificationCaseState[]
  portfolios: readonly PortfolioInfo[]
}

const TERMINAL_STATUSES: ReadonlySet<VerificationCaseStatus> = new Set([
  'passed',
  'failed',
  'inconclusive',
  'errored',
  'not_supported',
])

const SECTION_TITLE_SX = {
  fontSize: FONT_SIZE.xs,
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
  color: 'text.secondary',
  fontWeight: 600,
} as const

function SectionHeader({
  title,
  count,
  trailing,
}: {
  title: string
  count?: number
  trailing?: React.ReactNode
}): React.JSX.Element {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 0.5, bgcolor: 'var(--surface-toolbar-bg)', borderBottom: '1px solid var(--surface-border)' }}>
      <Typography sx={SECTION_TITLE_SX}>{title}</Typography>
      {count !== undefined && (
        <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}>({count})</Typography>
      )}
      {trailing && <Box sx={{ flex: 1, display: 'flex', justifyContent: 'flex-end' }}>{trailing}</Box>}
    </Box>
  )
}

interface SummaryStat {
  label: string
  value: string
}

function deriveSummaryStats(cases: readonly VerificationCaseState[]): SummaryStat[] {
  const counts = {
    passed: 0,
    failed: 0,
    errored: 0,
    inconclusive: 0,
    notSupported: 0,
    other: 0,
  }
  let totalMs = 0
  let timedCount = 0
  for (const cs of cases) {
    switch (cs.status) {
      case 'passed': counts.passed++; break
      case 'failed': counts.failed++; break
      case 'errored': counts.errored++; break
      case 'inconclusive': counts.inconclusive++; break
      case 'not_supported': counts.notSupported++; break
      default: counts.other++
    }
    if (cs.metrics?.totalDuration && isMeaningfulDuration(cs.metrics.totalDuration)) {
      totalMs += isoDurationToMs(cs.metrics.totalDuration)
      timedCount++
    }
  }
  const stats: SummaryStat[] = [
    { label: 'Total', value: String(cases.length) },
    { label: 'Passed', value: String(counts.passed) },
    { label: 'Failed', value: String(counts.failed) },
  ]
  if (counts.errored > 0) stats.push({ label: 'Errored', value: String(counts.errored) })
  if (counts.inconclusive > 0) stats.push({ label: 'Inconclusive', value: String(counts.inconclusive) })
  if (counts.notSupported > 0) stats.push({ label: 'Unsupported', value: String(counts.notSupported) })
  if (timedCount > 0) {
    stats.push({ label: 'Wall-clock', value: formatIsoDurationDetailed(isoDurationFromMs(totalMs)) })
    stats.push({ label: 'Average', value: formatIsoDurationDetailed(isoDurationFromMs(totalMs / timedCount)) })
  }
  return stats
}

/**
 * Three-section overview: active verifications (live JSON-RPC subscription), recent verdicts
 * (current cases with terminal status), and a small summary block. Shares the subscription
 * with {@link ActiveVerificationsMonitor} via {@link useActiveVerifications} so both surfaces
 * show the same authoritative server-side state.
 */
export default function RunningVerificationsTab({ api, connected, cases, portfolios }: Props): React.JSX.Element {
  const { items: active, cancel, cancelAll } = useActiveVerifications(api, connected)
  const terminalCases = cases.filter((cs) => TERMINAL_STATUSES.has(cs.status))
  const stats = deriveSummaryStats(terminalCases)

  const activeTrailing = active.length > 0 ? (
    <Tooltip title="Cancel all active verifications">
      <IconButton
        size="small"
        onClick={() => { void cancelAll() }}
        aria-label="Cancel all active verifications"
        sx={{ color: 'var(--danger)', p: 0.25 }}
      >
        <StopIcon sx={{ fontSize: ICON_SIZE.sm }} />
      </IconButton>
    </Tooltip>
  ) : null

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 auto', minHeight: 0, overflowY: 'auto' }}>
      <SectionHeader title="Active" count={active.length} trailing={activeTrailing} />
      {active.length === 0 ? (
        <Box sx={{ px: 1.5, py: 1 }}>
          <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary', fontStyle: 'italic' }}>
            No running verifications or validations.
          </Typography>
        </Box>
      ) : (
        <Box>
          {active.map((item) => (
            <Box
              key={item.requestId}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.75,
                px: 1.5,
                py: 0.5,
                borderBottom: '1px solid var(--surface-border)',
              }}
            >
              <KindChip kind={item.kind ?? 'Verify'} density="compact" />
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography sx={{ fontSize: FONT_SIZE.sm, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {item.caseLabel ?? `#${item.requestId}`}
                  {/* Only surface elapsed once it actually advanced past sub-second noise.
                      Portfolio is omitted - the user picked it explicitly and seeing "Auto"
                      on every row adds clutter without adding information. */}
                  {item.elapsed && isoDurationToMs(item.elapsed) >= 1000 && (
                    <Box component="span" sx={{ color: 'text.secondary', ml: 0.75, fontSize: FONT_SIZE.xs }}>
                      {formatIsoDurationDetailed(item.elapsed)}
                    </Box>
                  )}
                </Typography>
              </Box>
              <Tooltip title="Cancel">
                <IconButton
                  size="small"
                  onClick={() => { void cancel(item.requestId) }}
                  aria-label={`Cancel ${item.caseLabel ?? item.requestId}`}
                  sx={{ color: 'var(--danger)', p: 0.25 }}
                >
                  <StopIcon sx={{ fontSize: ICON_SIZE.sm }} />
                </IconButton>
              </Tooltip>
            </Box>
          ))}
        </Box>
      )}

      <Divider />

      <SectionHeader title="Recent verdicts" count={terminalCases.length} />
      {terminalCases.length === 0 ? (
        <Box sx={{ px: 1.5, py: 1 }}>
          <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary', fontStyle: 'italic' }}>
            No completed verifications yet.
          </Typography>
        </Box>
      ) : (
        <Box>
          {terminalCases.map((cs) => (
            <Box
              key={cs.caseInfo.id}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.75,
                px: 1.5,
                py: 0.5,
                borderBottom: '1px solid var(--surface-border)',
              }}
            >
              <CaseStatusIcon status={cs.status} />
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography sx={{ fontSize: FONT_SIZE.sm, wordBreak: 'break-word' }}>
                  {cs.caseInfo.label}
                </Typography>
                <Typography sx={{ fontSize: FONT_SIZE.xs, color: 'text.secondary' }}>
                  {findPortfolioLabel(portfolios, cs.portfolioId ?? cs.backendId) ?? 'default'}
                  {cs.metrics?.totalDuration && isMeaningfulDuration(cs.metrics.totalDuration)
                    ? ` - ${formatIsoDurationDetailed(cs.metrics.totalDuration)}`
                    : ''}
                </Typography>
              </Box>
            </Box>
          ))}
        </Box>
      )}

      <Divider />

      <SectionHeader title="Summary" />
      <Box sx={{ display: 'flex', flexDirection: 'column', px: 1.5, py: 0.75, gap: 0.25 }}>
        {stats.map((stat) => (
          <Box key={stat.label} sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>{stat.label}</Typography>
            <Typography sx={{ fontSize: FONT_SIZE.sm }}>{stat.value}</Typography>
          </Box>
        ))}
      </Box>
    </Box>
  )
}
