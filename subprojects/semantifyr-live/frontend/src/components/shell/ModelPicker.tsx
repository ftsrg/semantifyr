/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React, { useEffect, useMemo, useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Popover from '@mui/material/Popover';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import Tooltip from '@mui/material/Tooltip';
import FolderOpenOutlinedIcon from '@mui/icons-material/FolderOpenOutlined';
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown';
import SearchIcon from '@mui/icons-material/Search';

import type { LiveExample, LiveFlavor } from '../../examples';
import { FONT_SIZE, ICON_SIZE } from '../../lib/util/theme';

interface Props {
  flavors: readonly LiveFlavor[];
  currentFlavorId: string;
  currentExampleId: string;
  onSelectModel: (flavorId: string, exampleId: string) => void;
}

const SELECTED_TEXT_SX = {
  '&.Mui-selected, &.Mui-selected:hover': {
    bgcolor: 'action.selected',
    '& .MuiListItemText-primary': { color: 'primary.main', fontWeight: 600 },
  },
} as const;

const SECTION_LABEL_SX = {
  display: 'block',
  px: 1.75,
  pt: 1.25,
  pb: 0.5,
  fontSize: FONT_SIZE.xs,
  letterSpacing: '0.06em',
  color: 'text.secondary',
} as const;

export default function ModelPicker({ flavors, currentFlavorId, currentExampleId, onSelectModel }: Props): React.JSX.Element {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [pickerFlavorId, setPickerFlavorId] = useState(currentFlavorId);
  const [filter, setFilter] = useState('');
  const open = anchorEl !== null;

  const currentFlavor = flavors.find((f) => f.id === currentFlavorId) ?? flavors[0]!;
  const pickerFlavor = flavors.find((f) => f.id === pickerFlavorId) ?? currentFlavor;

  useEffect(() => {
    if (open) {
      setPickerFlavorId(currentFlavorId);
      setFilter('');
    }
  }, [open, currentFlavorId]);

  const filteredExamples = useMemo((): readonly LiveExample[] => {
    const needle = filter.trim().toLowerCase();
    if (!needle) {
      return pickerFlavor.examples;
    }
    return pickerFlavor.examples.filter((ex) =>
      ex.label.toLowerCase().includes(needle)
        || ex.description.toLowerCase().includes(needle)
        || ex.id.toLowerCase().includes(needle),
    );
  }, [pickerFlavor, filter]);

  const close = (): void => {
    setAnchorEl(null);
  };

  const handlePickExample = (exampleId: string): void => {
    close();
    onSelectModel(pickerFlavorId, exampleId);
  };

  return (
    <>
      <Tooltip title="Choose the modelling language and load an example">
        <Button
          size="small"
          startIcon={<FolderOpenOutlinedIcon />}
          endIcon={<ArrowDropDownIcon />}
          onClick={(event) => setAnchorEl(event.currentTarget)}
          sx={{
            fontSize: { xs: FONT_SIZE.sm, sm: FONT_SIZE.md },
            px: { xs: 0.75, sm: 1.5 },
            minWidth: 'unset',
            whiteSpace: 'nowrap',
            '& .MuiButton-startIcon': { mr: { xs: 0, sm: 1 } },
            '& .MuiButton-endIcon': { ml: { xs: 0, sm: 0.25 } },
          }}
        >
          <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>
            {currentFlavor.displayName}
          </Box>
        </Button>
      </Tooltip>
      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        slotProps={{ paper: { sx: { width: { xs: 'min(560px, calc(100vw - 16px))', sm: 600 } } } }}
      >
        <Box sx={{ display: 'flex', alignItems: 'stretch' }}>
          <Box sx={{ flex: '0 0 218px', minWidth: 0, borderRight: '1px solid var(--surface-border)' }}>
            <Typography component="div" sx={SECTION_LABEL_SX}>MODELLING LANGUAGE</Typography>
            <List dense disablePadding>
              {flavors.map((f) => (
                <ListItemButton
                  key={f.id}
                  selected={f.id === pickerFlavorId}
                  onClick={() => { setPickerFlavorId(f.id); setFilter(''); }}
                  sx={{ ...SELECTED_TEXT_SX, py: 0.75 }}
                >
                  <ListItemText
                    primary={f.displayName}
                    secondary={f.description}
                    slotProps={{
                      primary: { sx: { fontSize: FONT_SIZE.md } },
                      secondary: { sx: { fontSize: FONT_SIZE.xs, color: 'text.secondary', whiteSpace: 'normal' } },
                    }}
                  />
                </ListItemButton>
              ))}
            </List>
          </Box>

          <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
            <Typography component="div" sx={SECTION_LABEL_SX}>
              EXAMPLES &middot; {pickerFlavor.displayName}
            </Typography>
            <Box sx={{ px: 1.75, pb: 1 }}>
              <TextField
                size="small"
                fullWidth
                autoFocus
                placeholder="Filter examples"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon sx={{ fontSize: ICON_SIZE.sm, color: 'text.secondary' }} />
                      </InputAdornment>
                    ),
                    sx: { fontSize: FONT_SIZE.md },
                  },
                }}
              />
            </Box>
            <List dense disablePadding sx={{ flex: 1, maxHeight: 360, overflowY: 'auto', pb: 0.5 }}>
              {filteredExamples.length === 0 ? (
                <ListItemButton disabled sx={{ py: 0.75 }}>
                  <ListItemText
                    primary="No examples match your filter"
                    slotProps={{ primary: { sx: { fontSize: FONT_SIZE.md, color: 'text.secondary' } } }}
                  />
                </ListItemButton>
              ) : (
                filteredExamples.map((ex) => (
                  <ListItemButton
                    key={ex.id}
                    selected={pickerFlavorId === currentFlavorId && ex.id === currentExampleId}
                    onClick={() => handlePickExample(ex.id)}
                    sx={{ ...SELECTED_TEXT_SX, py: 0.75 }}
                  >
                    <ListItemText
                      primary={ex.label}
                      secondary={ex.description}
                      slotProps={{
                        primary: { sx: { fontSize: FONT_SIZE.md } },
                        secondary: { sx: { fontSize: FONT_SIZE.xs, color: 'text.secondary', whiteSpace: 'normal' } },
                      }}
                    />
                  </ListItemButton>
                ))
              )}
            </List>
          </Box>
        </Box>
      </Popover>
    </>
  );
}
