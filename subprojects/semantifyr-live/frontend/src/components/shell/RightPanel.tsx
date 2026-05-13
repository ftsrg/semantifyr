/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import CloseIcon from '@mui/icons-material/Close'
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

export interface RightPanelTab {
  id: string
  label: string
  content: React.ReactNode
}

interface Props {
  tabs: readonly RightPanelTab[]
  activeTabId: string | null
  onActiveTabChange: (id: string) => void
  onClose: () => void
}

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

export default function RightPanel({ tabs, activeTabId, onActiveTabChange, onClose }: Props): React.JSX.Element {
  const firstTab = tabs[0]
  const hasTabs = firstTab !== undefined
  const resolvedActiveId = hasTabs && tabs.some((t) => t.id === activeTabId)
    ? activeTabId
    : (firstTab?.id ?? null)
  const active = hasTabs ? (tabs.find((t) => t.id === resolvedActiveId) ?? firstTab) : null

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
        {active ? (
          <Tabs
            value={active.id}
            onChange={(_, value: string) => { onActiveTabChange(value); }}
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
