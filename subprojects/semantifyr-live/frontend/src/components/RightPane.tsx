/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import type { VerificationCaseSpecification, VerificationTrace, WitnessValidationStatus } from '../lib/verification';
import WitnessTab from './witness/WitnessTab';

export type RightPaneTab = 'generated' | 'witness';

interface Props {
  generatedSource: string | null;
  generatedSourceLastUpdated: number | null;
  onOpenGeneratedInOxsts?: ((source: string) => void) | undefined;
  witnessCase: VerificationCaseSpecification | null;
  witness: VerificationTrace | null;
  witnessValidation: WitnessValidationStatus | undefined;
  verificationPortfolioLabel?: string | undefined;
  validating: boolean;
  canRevalidate: boolean;
  onRevalidate: () => void;
  activeTab: RightPaneTab;
  onTabChange: (tab: RightPaneTab) => void;
}

export default function RightPane({
  generatedSource,
  generatedSourceLastUpdated,
  onOpenGeneratedInOxsts,
  witnessCase,
  witness,
  witnessValidation,
  verificationPortfolioLabel,
  validating,
  canRevalidate,
  onRevalidate,
  activeTab,
  onTabChange,
}: Props): React.JSX.Element {
  const showGenerated = generatedSource !== null;
  const showWitness = witness !== null && witnessCase !== null;
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
      <Tabs
        value={activeTab}
        onChange={(_, value: RightPaneTab) => {
          if (value === 'generated' && !showGenerated) return;
          if (value === 'witness' && !showWitness) return;
          onTabChange(value);
        }}
        sx={{
          minHeight: 32,
          borderBottom: '1px solid var(--surface-border)',
          bgcolor: 'var(--surface-toolbar-bg)',
          '& .MuiTab-root': {
            color: 'var(--text-muted)',
            textTransform: 'none',
            fontSize: '0.82rem',
            minHeight: 32,
            py: 0.5,
          },
          '& .Mui-selected': { color: 'var(--text)' },
          '& .MuiTabs-indicator': { bgcolor: 'var(--accent)' },
        }}
      >
        {showGenerated && <Tab value="generated" label="Generated OXSTS" />}
        {showWitness && <Tab value="witness" label="Witness" />}
      </Tabs>
      <Box sx={{ flex: '1 1 auto', minHeight: 0, minWidth: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {activeTab === 'generated' && showGenerated && (
          <GeneratedSourceContent
            source={generatedSource!}
            updatedAt={generatedSourceLastUpdated}
            onOpenInOxsts={onOpenGeneratedInOxsts}
          />
        )}
        {activeTab === 'witness' && showWitness && (
          <WitnessTab
            caseInfo={witnessCase!}
            trace={witness!}
            validation={witnessValidation}
            validating={validating}
            canRevalidate={canRevalidate}
            onRevalidate={onRevalidate}
            verificationPortfolioLabel={verificationPortfolioLabel}
          />
        )}
      </Box>
    </Box>
  );
}

interface GeneratedSourceContentProps {
  source: string;
  updatedAt: number | null;
  onOpenInOxsts?: ((source: string) => void) | undefined;
}

function GeneratedSourceContent({ source, updatedAt, onOpenInOxsts }: GeneratedSourceContentProps): React.JSX.Element {
  // The viewer's container does the scrolling: the inner <pre> keeps its natural width with
  // `whiteSpace: pre`, while the outer Box clips and offers both axes' scrollbars when the
  // longest line or total height exceed the right pane.
  return (
    <>
      <Box
        sx={{
          px: 1.5,
          py: 0.5,
          borderBottom: '1px solid var(--surface-border)',
          bgcolor: 'var(--surface-toolbar-bg)',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <Typography sx={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
          {updatedAt !== null ? `updated ${new Date(updatedAt).toLocaleTimeString()}` : ''}
        </Typography>
        <Box sx={{ flex: 1 }} />
        {onOpenInOxsts && (
          <Tooltip title="Open this generated OXSTS in a new tab as a Semantifyr-with-Gamma-library session.">
            <Button
              size="small"
              startIcon={<OpenInNewIcon sx={{ fontSize: 16 }} />}
              onClick={() => onOpenInOxsts(source)}
              sx={{ color: 'var(--text)', textTransform: 'none', fontSize: '0.75rem', py: 0, px: 1 }}
            >
              Open in new tab
            </Button>
          </Tooltip>
        )}
      </Box>
      <Box
        sx={{
          flex: '1 1 auto',
          minHeight: 0,
          minWidth: 0,
          overflow: 'auto',
        }}
      >
        <Box
          component="pre"
          sx={{
            m: 0,
            p: 1.5,
            display: 'block',
            fontFamily: "'JetBrains Mono', monospace",
            fontSize: '0.85rem',
            color: 'var(--text)',
            whiteSpace: 'pre',
          }}
        >
          {source}
        </Box>
      </Box>
    </>
  );
}
