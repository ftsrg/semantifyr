/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react'
import Box from '@mui/material/Box'
import type {
  VerificationCaseSpecification,
  VerificationTrace,
  WitnessValidationStatus,
} from '../lib/verification'
import WitnessTab from './witness/WitnessTab'

interface Props {
  witnessCase: VerificationCaseSpecification
  witness: VerificationTrace
  witnessValidation: WitnessValidationStatus | undefined
  verificationPortfolioLabel?: string | undefined
  validating: boolean
  canRevalidate: boolean
  onRevalidate: () => void
  /** Closes the right pane entirely. */
  onClose: () => void
}

/**
 * Witness-only right pane. The Generated-OXSTS tab was retired with the LSP no longer emitting
 * {@code semantifyr/case/generatedSource}; the pane has a single concern again, so the tab
 * shell is gone and the close button on {@link WitnessTab} dismisses the whole pane.
 */
export default function RightPane({
  witnessCase,
  witness,
  witnessValidation,
  verificationPortfolioLabel,
  validating,
  canRevalidate,
  onRevalidate,
  onClose,
}: Props): React.JSX.Element {
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
      <WitnessTab
        caseInfo={witnessCase}
        trace={witness}
        validation={witnessValidation}
        validating={validating}
        canRevalidate={canRevalidate}
        onRevalidate={onRevalidate}
        onClose={onClose}
        verificationPortfolioLabel={verificationPortfolioLabel}
      />
    </Box>
  )
}
