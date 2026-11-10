/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import CircularProgress from '@mui/material/CircularProgress'
import RefreshIcon from '@mui/icons-material/Refresh'
import CheckCircleOutlinedIcon from '@mui/icons-material/CheckCircleOutlined'
import ErrorOutlinedIcon from '@mui/icons-material/ErrorOutlined'
import HelpOutlineOutlinedIcon from '@mui/icons-material/HelpOutlineOutlined'
import TimerOffOutlinedIcon from '@mui/icons-material/TimerOffOutlined'
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined'
import { witnessValidationLabel } from '@semantifyr/editor-common'
import type { WitnessValidationStatus } from '../../lib/verification'
import { ICON_SIZE } from '../../lib/util/theme';

interface Props {
  status: WitnessValidationStatus | undefined
  message?: string | undefined
  busy: boolean
  disabled?: boolean
  onRevalidate: () => void
}

interface Descriptor {
  label: string
  color: string
  icon: React.ReactElement
  tooltip: string
}

function chipDescriptor(status: WitnessValidationStatus | undefined): Descriptor {
  switch (status) {
    case 'valid':
      return {
        label: witnessValidationLabel(status),
        color: 'var(--success)',
        icon: <CheckCircleOutlinedIcon sx={{ fontSize: ICON_SIZE.sm }} />,
        tooltip: 'The witness is verified to be a real (counter)example.',
      }
    case 'invalid':
      return {
        label: witnessValidationLabel(status),
        color: 'var(--danger)',
        icon: <ErrorOutlinedIcon sx={{ fontSize: ICON_SIZE.sm }} />,
        tooltip: 'The witness is verified to NOT be a real (counter)example.',
      }
    case 'inconclusive':
      return {
        label: witnessValidationLabel(status),
        color: 'var(--warning)',
        icon: <HelpOutlineOutlinedIcon sx={{ fontSize: ICON_SIZE.sm }} />,
        tooltip: 'The portfolio could not validate the witness.',
      }
    case 'timeout':
      return {
        label: witnessValidationLabel(status),
        color: 'var(--warning)',
        icon: <TimerOffOutlinedIcon sx={{ fontSize: ICON_SIZE.sm }} />,
        tooltip: 'The validation ran out of time before the portfolio decided.',
      }
    case 'cancelled':
      return {
        label: witnessValidationLabel(status),
        color: 'text.secondary',
        icon: <BlockOutlinedIcon sx={{ fontSize: ICON_SIZE.sm }} />,
        tooltip: 'The validation was cancelled.',
      }
    case 'errored':
      return {
        label: witnessValidationLabel(status),
        color: 'var(--danger)',
        icon: <ErrorOutlinedIcon sx={{ fontSize: ICON_SIZE.sm }} />,
        tooltip: 'An error occurred while validating the witness.',
      }
    default:
      return {
        label: 'Not validated',
        color: 'text.secondary',
        icon: <HelpOutlineOutlinedIcon sx={{ fontSize: ICON_SIZE.sm }} />,
        tooltip: 'Trigger validation to verify whether this witness is a real (counter)example.',
      }
  }
}

export default function ValidationChip({ status, message, busy, disabled, onRevalidate }: Props): React.JSX.Element {
  const descriptor = chipDescriptor(status)
  return (
    <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25 }}>
      <Tooltip title={message ? `${descriptor.tooltip} ${message}` : descriptor.tooltip}>
        <Chip
          icon={busy ? <CircularProgress size={12} sx={{ color: descriptor.color }} /> : descriptor.icon}
          label={busy ? 'Validating...' : descriptor.label}
          size="small"
          sx={{
            bgcolor: 'transparent',
            color: descriptor.color,
            border: `1px solid ${descriptor.color}`,
            height: 22,
            '& .MuiChip-icon': { color: descriptor.color },
          }}
        />
      </Tooltip>
      <Tooltip title="Validate witness">
        <span>
          <IconButton
            size="small"
            onClick={onRevalidate}
            disabled={(disabled ?? false) || busy}
            aria-label="Validate witness"
            sx={{ color: 'text.secondary' }}
          >
            <RefreshIcon sx={{ fontSize: ICON_SIZE.sm }} />
          </IconButton>
        </span>
      </Tooltip>
    </Box>
  )
}
