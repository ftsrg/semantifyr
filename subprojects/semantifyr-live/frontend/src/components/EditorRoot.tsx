/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useEffect, useState } from 'react';
import { createModelDocuments } from '../lib/session/monaco';
import { resolveColorMode, type Settings } from '../lib/stores/settings';
import type { ReadableStore, Store } from '../lib/stores/store';
import type { ColorModePreference, ResolvedColorMode } from '../lib/util/colorMode';
import { createLive } from '../lib/workspace/live';
import { LiveContext } from '../lib/workspace/liveContext';
import EditorPage from './EditorPage';

interface Props {
  settings: Store<Settings>;
  systemColorMode: ReadableStore<ResolvedColorMode>;
  backendUrl: string;
  colorMode: ResolvedColorMode;
  colorModePreference: ColorModePreference;
  onToggleColorMode: () => void;
  embedded: boolean;
}

export default function EditorRoot({ settings, systemColorMode, ...pageProperties }: Props): React.JSX.Element {
  const [live] = useState(() => {
    return createLive({
      backendUrl: pageProperties.backendUrl,
      settings,
      colorMode: () => {
        return resolveColorMode(settings.get().theme, systemColorMode.get());
      },
      modelDocuments: createModelDocuments(),
    });
  });

  useEffect(() => {
    live.start(window.location.search);
    return () => {
      void live.dispose();
    };
  }, [live]);

  return (
    <LiveContext.Provider value={live}>
      <EditorPage {...pageProperties} />
    </LiveContext.Provider>
  );
}
