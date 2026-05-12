/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import CloseIcon from '@mui/icons-material/Close'
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

/**
 * One content slot rendered by the {@link RightPanel} shell. Each tab is bound to a data
 * source; the parent only adds tabs whose data exists.
 */
export interface RightPanelTab {
  id: string
  label: string
  content: React.ReactNode
}

interface Props {
  tabs: readonly RightPanelTab[]
  activeTabId: string | null
  onActiveTabChange: (id: string) => void
  /**
   * Hides the panel without unmounting. Tabs with mounted state (witness scroll position,
   * future per-tab UI state) survive a close-then-reopen cycle.
   */
  onClose: () => void
}

// Layout only - the colours (idle = text.secondary, selected + indicator = the accent, via
// the theme palette and `<Tabs>`'s default `textColor`/`indicatorColor="primary"`) and the
// `textTransform: none` come from the MUI theme.
const TABS_SX = {
  minHeight: 32,
  flex: 1,
  borderBottom: '1px solid var(--surface-border)',
  '& .MuiTab-root': {
    fontSize: FONT_SIZE.sm,
    minHeight: 32,
    py: 0.5,
  },
} as const

/**
 * Multi-tab right panel shell. Tabs (Witness, Running Verifications, future Compiled OXSTS /
 * Instances) plug into the same shape. The shell renders even when {@code tabs} is empty so
 * the user can open it proactively. The close affordance is the panel header's X.
 */
export default function RightPanel({ tabs, activeTabId, onActiveTabChange, onClose }: Props): React.JSX.Element {
  // Resolve the active tab even if the parent hasn't picked one (or picked an id that
  // doesn't exist anymore because its data went away).
  const hasTabs = tabs.length > 0
  const resolvedActiveId = hasTabs && tabs.some((t) => t.id === activeTabId)
    ? activeTabId
    : (tabs[0]?.id ?? null)
  const active = hasTabs ? (tabs.find((t) => t.id === resolvedActiveId) ?? tabs[0]!) : null

  return (
    <Box
      sx={{
        flex: '1 1 0',
        minWidth: 0,
        minHeight: 0,
        display: 'flex',
        flexDirection: 'column',
        borderLeft: { xs: 'none', md: '1px solid var(--surface-border)' },
        bgcolor: 'var(--page-bg)',
        overflow: 'hidden',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'stretch', bgcolor: 'var(--surface-toolbar-bg)', minHeight: 32 }}>
        {hasTabs ? (
          <Tabs
            value={active!.id}
            onChange={(_, value: string) => onActiveTabChange(value)}
            sx={TABS_SX}
          >
            {tabs.map((tab) => (
              <Tab key={tab.id} value={tab.id} label={tab.label} />
            ))}
          </Tabs>
        ) : (
          <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', px: 1.5, borderBottom: '1px solid var(--surface-border)' }}>
            <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>
              Side panel
            </Typography>
          </Box>
        )}
        <Tooltip title="Close panel">
          <IconButton
            size="small"
            onClick={onClose}
            aria-label="Close right panel"
            sx={{ color: 'text.secondary', alignSelf: 'center', mx: 0.5 }}
          >
            <CloseIcon sx={{ fontSize: ICON_SIZE.md }} />
          </IconButton>
        </Tooltip>
      </Box>
      <Box sx={{ flex: '1 1 auto', minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {active ? active.content : (
          <Box sx={{ p: 2, color: 'text.secondary', fontSize: FONT_SIZE.md }}>
            <Typography sx={{ fontSize: FONT_SIZE.md, color: 'text.secondary', mb: 1 }}>
              No active tabs.
            </Typography>
            <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>
              Tabs appear here when their content is available - for example, after a verify
              run produces a witness, or when verifications are in flight.
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  )
}
