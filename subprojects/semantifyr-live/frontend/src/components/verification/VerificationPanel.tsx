/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Box from '@mui/material/Box';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Divider from '@mui/material/Divider';
import CircularProgress from '@mui/material/CircularProgress';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import CloseIcon from '@mui/icons-material/Close';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import MyLocationOutlinedIcon from '@mui/icons-material/MyLocationOutlined';
import Badge from '@mui/material/Badge';
import ArticleOutlinedIcon from '@mui/icons-material/ArticleOutlined';

import type { VerificationCaseState } from '../../lib/verification';
import VerifyButton from './VerifyButton';
import RefreshButton from './RefreshButton';
import ProblemsPill from './ProblemsPill';
import { CaseStatusIcon, SummaryCounts, SummaryStatusIcon } from './StatusDisplay';
import { formatIsoDurationDetailed } from '../../lib/util/duration';
import { buildMetricsTooltip, isMeaningfulDuration } from '../../lib/verification/metricsTooltip';
import { witnessIconDescriptor } from '../../lib/verification/witnessIcon';
import { findPortfolioLabel } from '../../lib/verification/portfolioLabel';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';
import { changeSettings } from '../../lib/stores/settings';
import { useStore } from '../../lib/stores/useStore';
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext';

interface MetricsPillSource {
  verb: 'verified' | 'validated';
  totalDuration: string;
  metrics: VerificationCaseState['metrics'];
  portfolioLabel?: string | undefined;
  backendId?: string | undefined;
}

interface Props {
  panelHeight: number;
  onClose: () => void;
  onShowWitness?: (caseId: string) => void;
}

const pillSx = {
  fontSize: FONT_SIZE.xs,
  fontWeight: 500,
  color: 'text.secondary',
  bgcolor: 'var(--surface-panel-bg)',
  border: '1px solid var(--surface-border)',
  borderRadius: 999,
  px: 0.75,
  py: 0.05,
  whiteSpace: 'nowrap',
} as const;

function MetricsPill({
  source,
}: {
  source: MetricsPillSource;
}): React.JSX.Element {
  const tooltip = buildMetricsTooltip(source.metrics, {
    portfolioLabel: source.portfolioLabel,
    backendId: source.backendId,
  });
  const label = `${source.verb} in ${formatIsoDurationDetailed(source.totalDuration)}`;
  return (
    <Tooltip title={tooltip}>
      <Box component="span" sx={pillSx}>
        {label}
      </Box>
    </Tooltip>
  );
}

export default function VerificationPanel({ panelHeight, onClose, onShowWitness }: Props): React.JSX.Element {
  const live = useLive();
  const verifyState = useCurrentVerification(live);
  const connected = useStore(live.connection, (connection) => {
    return connection.status === 'connected';
  });
  const flavor = useStore(live.workspace, (workspace) => {
    return workspace.flavor;
  });
  const autoValidate = useStore(live.settings, (settings) => {
    return settings.autoValidate;
  });
  const portfolios = useStore(live.catalog, (catalog) => {
    return catalog.portfolios;
  });
  const validateWitnessCommand = flavor?.validateWitnessCommand;

  const { phase, discovery, cases } = verifyState;
  const verifyBusy = phase === 'running';
  const stale = !connected;

  return (
    <Box
      sx={{
        height: `${panelHeight}px`,
        display: 'flex',
        flexDirection: 'column',
        bgcolor: 'var(--surface-panel-bg)',
        borderTop: '1px solid var(--surface-border)',
        opacity: stale ? 0.5 : 1,
        transition: 'opacity 0.2s',
        overflow: 'hidden',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          rowGap: 0.5,
          px: { xs: 1, sm: 1.5 },
          py: 0.25,
          gap: 0.5,
          flex: '0 0 auto',
        }}
      >
        <Typography
          variant="body2"
          sx={{
            fontWeight: 600,
            fontSize: FONT_SIZE.md,
          }}
        >
          Verification Cases
        </Typography>
        <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.5 }} />
        <SummaryCounts cases={cases} />
        <SummaryStatusIcon cases={cases} phase={phase} />
        <ProblemsPill />
        <Box sx={{ flex: 1 }} />

        {validateWitnessCommand && (
          <Tooltip title="Automatically validate the returned witness using a separate portfolio.">
            <FormControlLabel
              control={
                <Switch
                  size="small"
                  checked={autoValidate}
                  onChange={(_, checked) => {
                    changeSettings(live.settings, { autoValidate: checked });
                  }}
                  onClick={(event) => { event.stopPropagation(); }}
                />
              }
              label={
                <Typography sx={{ fontSize: FONT_SIZE.sm, color: 'text.secondary' }}>auto-validate</Typography>
              }
              sx={{ mr: 0.5, ml: 0, '& .MuiFormControlLabel-label': { ml: 0.25 } }}
            />
          </Tooltip>
        )}
        <VerifyButton
          busy={verifyBusy}
          disabled={!connected}
          onVerify={(event) => {
            event.stopPropagation();
            live.verifyAll();
          }}
          onCancel={(event) => {
            event.stopPropagation();
            live.cancelVerification();
          }}
        />
        <RefreshButton
          disabled={!connected || verifyBusy}
          onClick={(event) => {
            event.stopPropagation();
            live.refreshCases();
          }}
        />
        <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.5 }} />
        <Tooltip title="Close panel">
          <IconButton
            size="small"
            onClick={onClose}
            aria-label="Close verification panel"
            sx={{ color: 'text.secondary' }}
          >
            <CloseIcon sx={{ fontSize: ICON_SIZE.md }} />
          </IconButton>
        </Tooltip>
      </Box>

      <Box
        sx={{
          flex: '1 1 auto',
          minHeight: 0,
          overflowY: 'auto',
        }}
      >
          {phase === 'error' && verifyState.message && (
            <Box sx={{ px: 1.5, py: 0.75 }}>
              <Typography variant="body2" sx={{ fontSize: FONT_SIZE.md, color: 'var(--danger)' }}>
                {verifyState.message}
              </Typography>
            </Box>
          )}

          {cases.length > 0 && (
            <List dense disablePadding>
              {cases.map((caseState) => (
                <ListItemButton
                  key={caseState.caseInfo.id}
                  onClick={() => {
                    live.revealRange(caseState.caseInfo.location.range);
                  }}
                  sx={{ py: 0.25, px: 1.5 }}
                >
                  <ListItemIcon sx={{ minWidth: 32 }}>
                    <CaseStatusIcon status={caseState.status} />
                  </ListItemIcon>
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', rowGap: 0.25, columnGap: 1, minWidth: 0 }}>
                        <Typography component="span" sx={{ fontSize: FONT_SIZE.md, color: stale ? 'var(--text-muted)' : 'var(--text)', wordBreak: 'break-word' }}>
                          {caseState.caseInfo.label}
                        </Typography>
                        {caseState.metrics !== undefined && isMeaningfulDuration(caseState.metrics.totalDuration) && (
                          <Box sx={{ display: { xs: 'none', sm: 'inline-flex' } }}>
                            <MetricsPill
                              source={{
                                verb: 'verified',
                                totalDuration: caseState.metrics.totalDuration,
                                metrics: caseState.metrics,
                                portfolioLabel: findPortfolioLabel(
                                  portfolios,
                                  caseState.portfolioId ?? caseState.backendId,
                                ),
                                backendId: caseState.backendId,
                              }}
                            />
                          </Box>
                        )}
                        {caseState.validationMetrics !== undefined && isMeaningfulDuration(caseState.validationMetrics.totalDuration) && (
                          <Box sx={{ display: { xs: 'none', sm: 'inline-flex' } }}>
                            <MetricsPill
                              source={{
                                verb: 'validated',
                                totalDuration: caseState.validationMetrics.totalDuration,
                                metrics: caseState.validationMetrics,
                                portfolioLabel: findPortfolioLabel(
                                  portfolios,
                                  caseState.validationPortfolioIdUsed ?? caseState.validationBackendId,
                                ),
                                backendId: caseState.validationBackendId,
                              }}
                            />
                          </Box>
                        )}
                        {caseState.validating && (
                          <Tooltip title="Validating witness in the background">
                            <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: 'text.secondary', fontSize: FONT_SIZE.xs }}>
                              <CircularProgress size={10} thickness={6} sx={{ color: 'text.secondary' }} />
                              <span>validating...</span>
                            </Box>
                          </Tooltip>
                        )}
                      </Box>
                    }
                    secondary={
                      caseState.message != null && (caseState.status === 'failed' || caseState.status === 'errored' || caseState.status === 'inconclusive' || caseState.status === 'timeout' || caseState.status === 'not_supported' || caseState.status === 'cancelled')
                        ? caseState.message
                        : null
                    }
                    slotProps={{
                      secondary: {
                        sx: {
                          fontSize: FONT_SIZE.sm,
                          color: caseState.status === 'failed' || caseState.status === 'errored' ? 'var(--danger)' : 'var(--text-muted)',
                        },
                      },
                    }}
                  />
                  {caseState.trace !== undefined && onShowWitness && (() => {
                    const descriptor = witnessIconDescriptor(caseState.witnessValidation);
                    return (
                      <Tooltip title={descriptor.tooltip}>
                        <IconButton
                          size="small"
                          onClick={(event) => { event.stopPropagation(); onShowWitness(caseState.caseInfo.id); }}
                          aria-label={descriptor.ariaLabel}
                          sx={{ color: descriptor.iconColor, ml: 0.5 }}
                        >
                          <Badge
                            color={descriptor.badgeColor}
                            variant="dot"
                            invisible={!descriptor.badgeVisible}
                            overlap="circular"
                          >
                            <ArticleOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />
                          </Badge>
                        </IconButton>
                      </Tooltip>
                    );
                  })()}
                  <Tooltip title="Go to definition">
                    <IconButton
                      size="small"
                      onClick={(event) => {
                        event.stopPropagation();
                        live.revealRange(caseState.caseInfo.location.range);
                      }}
                      aria-label={`Go to definition of ${caseState.caseInfo.label}`}
                      sx={{ color: 'text.secondary', ml: 0.5, display: { xs: 'none', sm: 'inline-flex' } }}
                    >
                      <MyLocationOutlinedIcon sx={{ fontSize: ICON_SIZE.md }} />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Verify case">
                    <span>
                      <IconButton
                        size="small"
                        onClick={(event) => {
                          event.stopPropagation();
                          live.verifyCase(caseState.caseInfo.id);
                        }}
                        disabled={!connected || verifyBusy}
                        aria-label={`Verify ${caseState.caseInfo.label}`}
                        sx={{ color: 'var(--accent)' }}
                      >
                        <PlayArrowIcon sx={{ fontSize: ICON_SIZE.lg }} />
                      </IconButton>
                    </span>
                  </Tooltip>
                </ListItemButton>
              ))}
            </List>
          )}

          {cases.length === 0 && connected && discovery === 'discovering' && (
            <Box sx={{ px: 1.5, py: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
              <CircularProgress size={14} sx={{ color: 'text.secondary' }} />
              <Typography variant="body2" sx={{ fontSize: FONT_SIZE.md, color: 'text.secondary' }}>
                Discovering cases...
              </Typography>
            </Box>
          )}

          {cases.length === 0 && connected && discovery === 'discovered' && phase !== 'error' && (
            <Box sx={{ px: 1.5, py: 1 }}>
              <Typography variant="body2" sx={{ fontSize: FONT_SIZE.md, color: 'text.secondary' }}>
                No verification cases
              </Typography>
            </Box>
          )}
      </Box>
    </Box>
  );
}

