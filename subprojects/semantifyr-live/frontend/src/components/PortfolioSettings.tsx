/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import type { PortfolioInfo } from '../lib/portfolios';

interface Props {
  portfolios: readonly PortfolioInfo[];
  /** Verify-side portfolio. */
  verifyPortfolioId: string;
  onVerifyPortfolioChange: (portfolioId: string) => void;
  /** Validate-side portfolio. Pass null to hide the Validate section (flavor without validate). */
  validationPortfolioId?: string | null;
  onValidationPortfolioChange?: (portfolioId: string) => void;
}

/**
 * Combined portfolio picker for both verify and validate sides. Lives in the StatusBar trailing
 * slot. The button label only shows the verify portfolio name; the validate-side selection is
 * surfaced as a small caption inside the popover and in the tooltip.
 */
export default function PortfolioSettings({
  portfolios,
  verifyPortfolioId,
  onVerifyPortfolioChange,
  validationPortfolioId,
  onValidationPortfolioChange,
}: Props): React.JSX.Element {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  // The popover only lists installed/usable portfolios; unavailable ones are surfaced on the
  // admin page's "Available portfolios" panel rather than as greyed-out menu rows here.
  const availablePortfolios = portfolios.filter((p) => p.available);
  const verify = portfolios.find((p) => p.id === verifyPortfolioId);
  const validate = validationPortfolioId
    ? portfolios.find((p) => p.id === validationPortfolioId)
    : null;

  if (availablePortfolios.length === 0) {
    return <></>;
  }

  const tooltip = validate
    ? `Verify with ${verify?.displayName ?? verifyPortfolioId}, validate with ${validate.displayName}.`
    : `Verify with ${verify?.displayName ?? verifyPortfolioId}.`;

  return (
    <>
      <Tooltip title={tooltip}>
        <Button
          size="small"
          onClick={(event) => setAnchor(event.currentTarget)}
          sx={{
            color: 'var(--text-muted)',
            textTransform: 'none',
            fontSize: '0.72rem',
            py: 0,
            px: 0.75,
            minWidth: 0,
          }}
        >
          {verify?.displayName ?? verifyPortfolioId}
        </Button>
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={anchor !== null}
        onClose={() => setAnchor(null)}
        slotProps={{
          paper: {
            sx: {
              bgcolor: 'var(--surface-bg)',
              color: 'var(--text)',
              border: '1px solid var(--surface-border)',
              minWidth: 280,
            },
          },
        }}
      >
        <SectionHeader>Verify with</SectionHeader>
        {availablePortfolios.map((p) => (
          <PortfolioOption
            key={`verify-${p.id}`}
            portfolio={p}
            selected={p.id === verifyPortfolioId}
            onSelect={() => {
              setAnchor(null);
              onVerifyPortfolioChange(p.id);
            }}
          />
        ))}
        {validationPortfolioId !== null && validationPortfolioId !== undefined && onValidationPortfolioChange && (
          <>
            <Divider sx={{ borderColor: 'var(--surface-border)' }} />
            <SectionHeader>Validate with</SectionHeader>
            {availablePortfolios.map((p) => (
              <PortfolioOption
                key={`validate-${p.id}`}
                portfolio={p}
                selected={p.id === validationPortfolioId}
                onSelect={() => {
                  setAnchor(null);
                  onValidationPortfolioChange(p.id);
                }}
              />
            ))}
          </>
        )}
      </Menu>
    </>
  );
}

function SectionHeader({ children }: { children: React.ReactNode }): React.JSX.Element {
  return (
    <Box sx={{ px: 2, pt: 0.75, pb: 0.25 }}>
      <Typography
        sx={{
          fontSize: '0.7rem',
          color: 'var(--text-muted)',
          textTransform: 'uppercase',
          letterSpacing: '0.04em',
        }}
      >
        {children}
      </Typography>
    </Box>
  );
}

function PortfolioOption({
  portfolio,
  selected,
  onSelect,
}: {
  portfolio: PortfolioInfo;
  selected: boolean;
  onSelect: () => void;
}): React.JSX.Element {
  return (
    <MenuItem
      selected={selected}
      disabled={!portfolio.available}
      onClick={onSelect}
      sx={{ py: 0.5, alignItems: 'flex-start' }}
    >
      <ListItemText
        primary={
          <Typography sx={{ fontSize: '0.85rem', color: portfolio.available ? 'var(--text)' : 'var(--text-muted)' }}>
            {portfolio.displayName}
            {!portfolio.available && (
              <Typography component="span" sx={{ fontSize: '0.7rem', ml: 1, color: 'var(--warning)' }}>
                (unavailable)
              </Typography>
            )}
          </Typography>
        }
        secondary={
          <Typography sx={{ fontSize: '0.72rem', color: 'var(--text-muted)', whiteSpace: 'normal' }}>
            {portfolio.description}
          </Typography>
        }
        slotProps={{ secondary: { sx: { mt: 0.25 } } }}
      />
    </MenuItem>
  );
}
