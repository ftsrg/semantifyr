/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useState } from 'react';
import AppBar from '@mui/material/AppBar';
import MuiToolbar from '@mui/material/Toolbar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import FolderOpenOutlinedIcon from '@mui/icons-material/FolderOpenOutlined';
import GitHubIcon from '@mui/icons-material/GitHub';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';

import type { LiveExample } from '../examples';
import type { ColorModePreference } from '../lib/theme';
import type { LiveEditorStatus } from './LiveEditor';
import ConnectionStatus from './ConnectionStatus';
import CopyLinkButton from './CopyLinkButton';
import ColorModeToggle from './ColorModeToggle';

interface Props {
  logoSrc: string;
  examples: readonly LiveExample[];
  onLoadExample: (exampleId: string) => void;
  connectionStatus: LiveEditorStatus;
  onReconnect: () => void;
  onDisconnect: () => void;
  onCopyLink: () => void;
  copyConfirmation: string | null;
  colorModePreference: ColorModePreference;
  onToggleColorMode: () => void;
}

export default function Toolbar({
  logoSrc,
  examples,
  onLoadExample,
  connectionStatus,
  onReconnect,
  onDisconnect,
  onCopyLink,
  copyConfirmation,
  colorModePreference,
  onToggleColorMode,
}: Props): React.JSX.Element {
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const menuOpen = menuAnchor !== null;

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>): void => {
    setMenuAnchor(event.currentTarget);
  };

  const handleMenuClose = (): void => {
    setMenuAnchor(null);
  };

  const handleExampleSelect = (exampleId: string): void => {
    handleMenuClose();
    onLoadExample(exampleId);
  };

  return (
    <AppBar
      position="static"
      elevation={0}
      sx={{
        bgcolor: 'var(--surface-toolbar-bg)',
        borderBottom: '1px solid var(--surface-border)',
      }}
    >
      <MuiToolbar
        variant="dense"
        disableGutters
        sx={{
          px: { xs: 1, sm: 2 },
          gap: { xs: 0.5, sm: 1 },
          minHeight: { xs: 44, sm: 48 },
        }}
      >
        <a href="/" aria-label="Semantifyr Live" style={{ display: 'inline-flex', alignItems: 'center', textDecoration: 'none' }}>
          <Box
            component="img"
            src={logoSrc}
            alt="Semantifyr"
            sx={{ height: { xs: '1.3rem', sm: '1.75rem' }, width: 'auto', display: 'block' }}
          />
        </a>

        <ConnectionStatus
          status={connectionStatus}
          onReconnect={onReconnect}
          onDisconnect={onDisconnect}
        />

        <Divider
          orientation="vertical"
          flexItem
          sx={{ mx: 0.5, borderColor: 'var(--surface-border)', display: { xs: 'none', sm: 'block' } }}
        />

        <Tooltip title="Load a pre-built example into the editor">
        <Button
          size="small"
          startIcon={<FolderOpenOutlinedIcon />}
          onClick={handleMenuOpen}
          sx={{
            color: 'var(--text)',
            textTransform: 'none',
            fontSize: { xs: '0.8rem', sm: '0.85rem' },
            px: 1.5,
            whiteSpace: 'nowrap',
          }}
        >
          Load example
        </Button>
        </Tooltip>
        <Menu
          anchorEl={menuAnchor}
          open={menuOpen}
          onClose={handleMenuClose}
          slotProps={{
            paper: {
              sx: {
                bgcolor: 'var(--surface-bg)',
                color: 'var(--text)',
                border: '1px solid var(--surface-border)',
                minWidth: 240,
              },
            },
          }}
        >
          {examples.map((ex) => (
            <MenuItem
              key={ex.id}
              onClick={() => handleExampleSelect(ex.id)}
              sx={{ py: 1 }}
            >
              <ListItemText
                primary={ex.label}
                secondary={ex.description}
                slotProps={{
                  primary: { sx: { fontSize: '0.9rem' } },
                  secondary: { sx: { fontSize: '0.78rem', color: 'var(--text-muted)', whiteSpace: 'normal' } },
                }}
              />
            </MenuItem>
          ))}
        </Menu>

        <Box sx={{ flex: 1 }} />

        <CopyLinkButton onClick={onCopyLink} confirmationMessage={copyConfirmation} />
        <Tooltip title="Documentation">
          <IconButton
            size="small"
            component="a"
            href="https://ftsrg.mit.bme.hu/semantifyr"
            target="_blank"
            rel="noopener noreferrer"
            sx={{ color: 'var(--text)' }}
          >
            <MenuBookOutlinedIcon sx={{ fontSize: 20 }} />
          </IconButton>
        </Tooltip>
        <Tooltip title="GitHub">
          <IconButton
            size="small"
            component="a"
            href="https://github.com/ftsrg/semantifyr"
            target="_blank"
            rel="noopener noreferrer"
            sx={{ color: 'var(--text)' }}
          >
            <GitHubIcon sx={{ fontSize: 20 }} />
          </IconButton>
        </Tooltip>
        <ColorModeToggle preference={colorModePreference} onToggle={onToggleColorMode} />
      </MuiToolbar>
    </AppBar>
  );
}
