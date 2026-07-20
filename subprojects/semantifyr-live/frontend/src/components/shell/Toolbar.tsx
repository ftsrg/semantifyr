/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useState } from 'react';
import Box from '@mui/material/Box';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemText from '@mui/material/ListItemText';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import GitHubIcon from '@mui/icons-material/GitHub';
import MenuBookOutlinedIcon from '@mui/icons-material/MenuBookOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';

import type { LiveFlavor } from '../../examples';
import type { ColorModePreference } from '../../lib/util/colorMode';
import type { LiveEditorStatus } from '../editor/LiveEditor';
import AppHeader from './AppHeader';
import ConnectionStatus from './ConnectionStatus';
import ModelPicker from './ModelPicker';
import CopyLinkButton from './CopyLinkButton';
import ColorModeToggle from './ColorModeToggle';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  logoSrc: string;
  flavors: readonly [LiveFlavor, ...LiveFlavor[]];
  currentFlavorId: string;
  currentExampleId: string;
  onSelectModel: (flavorId: string, exampleId: string) => void;
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
  flavors,
  currentFlavorId,
  currentExampleId,
  onSelectModel,
  connectionStatus,
  onReconnect,
  onDisconnect,
  onCopyLink,
  copyConfirmation,
  colorModePreference,
  onToggleColorMode,
}: Props): React.JSX.Element {
  const [overflowAnchor, setOverflowAnchor] = useState<HTMLElement | null>(null);
  const overflowOpen = overflowAnchor !== null;

  return (
    <AppHeader logoSrc={logoSrc}>
      <ConnectionStatus
        status={connectionStatus}
        onReconnect={onReconnect}
        onDisconnect={onDisconnect}
      />

      <Divider
        orientation="vertical"
        flexItem
        sx={{ mx: 0.5, display: { xs: 'none', sm: 'block' } }}
      />

      <ModelPicker
        flavors={flavors}
        currentFlavorId={currentFlavorId}
        currentExampleId={currentExampleId}
        onSelectModel={onSelectModel}
      />

      <CopyLinkButton onClick={onCopyLink} confirmationMessage={copyConfirmation} />

      <Box sx={{ flex: 1 }} />

      <Tooltip title="Documentation">
        <IconButton
          size="small"
          component="a"
          href="https://ftsrg.mit.bme.hu/semantifyr"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Documentation"
          sx={{ color: 'text.primary', display: { xs: 'none', sm: 'inline-flex' } }}
        >
          <MenuBookOutlinedIcon sx={{ fontSize: ICON_SIZE.lg }} />
        </IconButton>
      </Tooltip>
      <Tooltip title="GitHub">
        <IconButton
          size="small"
          component="a"
          href="https://github.com/ftsrg/semantifyr"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="GitHub"
          sx={{ color: 'text.primary', display: { xs: 'none', sm: 'inline-flex' } }}
        >
          <GitHubIcon sx={{ fontSize: ICON_SIZE.lg }} />
        </IconButton>
      </Tooltip>
      <ColorModeToggle preference={colorModePreference} onToggle={onToggleColorMode} />

      <Tooltip title="More">
        <IconButton
          size="small"
          onClick={(e) => { setOverflowAnchor(e.currentTarget); }}
          aria-label="More"
          sx={{ color: 'text.primary', display: { xs: 'inline-flex', sm: 'none' } }}
        >
          <MoreVertIcon sx={{ fontSize: ICON_SIZE.lg }} />
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={overflowAnchor}
        open={overflowOpen}
        onClose={() => { setOverflowAnchor(null); }}
        slotProps={{ paper: { sx: { minWidth: 200 } } }}
      >
        <MenuItem
          component="a"
          href="https://ftsrg.mit.bme.hu/semantifyr"
          target="_blank"
          rel="noopener noreferrer"
          onClick={() => { setOverflowAnchor(null); }}
        >
          <MenuBookOutlinedIcon sx={{ fontSize: ICON_SIZE.md, mr: 1, color: 'text.secondary' }} />
          <ListItemText primary="Documentation" slotProps={{ primary: { sx: { fontSize: FONT_SIZE.md } } }} />
        </MenuItem>
        <MenuItem
          component="a"
          href="https://github.com/ftsrg/semantifyr"
          target="_blank"
          rel="noopener noreferrer"
          onClick={() => { setOverflowAnchor(null); }}
        >
          <GitHubIcon sx={{ fontSize: ICON_SIZE.md, mr: 1, color: 'text.secondary' }} />
          <ListItemText primary="GitHub" slotProps={{ primary: { sx: { fontSize: FONT_SIZE.md } } }} />
        </MenuItem>
      </Menu>
    </AppHeader>
  );
}
