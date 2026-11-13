/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import { useColorMode } from '@docusaurus/theme-common';
import { liveEditorUrl, liveExampleUrl, normalizeBaseUrl, type LiveEditorLines, type LiveEditorView } from '@semantifyr/editor-common';
import { terms } from '@semantifyr/editor-common/terms';
import { FenceContext } from '@site/src/lib/fence';
import { useLiveBackendUrl } from '@site/src/lib/live';
import controlStyles from '@site/src/theme/CodeBlock/Buttons/styles.module.css';
import styles from './styles.module.css';

export interface EmbeddedExampleProps {
  model: string;
  // the registry's identifier of a served example, which opens by its address
  example?: string | undefined;
  shown: string;
  lines?: LiveEditorLines | undefined;
  language: 'oxsts' | 'gamma' | 'sysml';
  library?: 'gamma' | 'sysml' | undefined;
  view: LiveEditorView;
  open?: boolean;
}

type Phase = 'closed' | 'loading' | 'open';

interface FrameProps {
  url: string;
  initialHeight: number;
  onShown: () => void;
}

function EmbedFrame({ url, initialHeight, onShown }: FrameProps): React.JSX.Element {
  const frame = useRef<HTMLIFrameElement>(null);
  const [height, setHeight] = useState(initialHeight);

  useEffect(() => {
    const origin = new URL(url).origin;
    const receive = (event: MessageEvent): void => {
      const message = event.data as { type?: string; height?: unknown } | null;
      if (event.source !== frame.current?.contentWindow || event.origin !== origin) {
        return;
      }
      if (message?.type === 'semantifyr-live:height' && typeof message.height === 'number') {
        setHeight(Math.ceil(message.height));
      }
      if (message?.type === 'semantifyr-live:shown') {
        onShown();
      }
    };
    window.addEventListener('message', receive);
    return () => {
      window.removeEventListener('message', receive);
    };
  }, [url, onShown]);

  return <iframe ref={frame} className={styles.frame} style={{ height }} src={url} title={terms.live.term} allow="clipboard-write" />;
}

function RunIcon(): React.JSX.Element {
  return (
    <svg className={controlStyles.controlIcon} viewBox="0 0 16 16" aria-hidden="true">
      <path d="M4.5 2.8v10.4L13 8z" fill="currentColor" />
    </svg>
  );
}

function EditIcon(): React.JSX.Element {
  return (
    <svg className={controlStyles.controlIcon} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4.5 19.5h4l10-10-4-4-10 10v4zM13 7l4 4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

// closed it is the page's code block; on the press live's editor loads unseen over it and takes its place once it shows the model
export function EmbeddedExample({ model, example, shown, lines, language, library, view, open = false }: EmbeddedExampleProps): React.JSX.Element {
  const liveBackendUrl = useLiveBackendUrl();
  const { colorMode } = useColorMode();
  const [phase, setPhase] = useState<Phase>('closed');
  const [url, setUrl] = useState<string | null>(null);
  const [initialHeight, setInitialHeight] = useState(0);
  const block = useRef<HTMLDivElement>(null);
  const code = useRef<HTMLDivElement>(null);
  const control = useRef<HTMLButtonElement>(null);
  const closedAt = useRef<number | null>(null);
  const first = lines?.first;
  const last = lines?.last;
  const opened = phase !== 'closed';

  useEffect(() => {
    if (!opened) {
      setUrl(null);
      return;
    }
    let active = true;
    const range = first === undefined || last === undefined ? undefined : { first, last };
    void liveEditorUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, { language, example, library, view, theme: colorMode, lines: range, model: example === undefined ? model : undefined }).then((address) => {
      if (active) {
        setUrl(address);
      }
    });
    return () => {
      active = false;
    };
  }, [opened, liveBackendUrl, language, example, library, view, colorMode, model, first, last]);

  // the block comes back where the editor's top was, and the focus to its control
  useLayoutEffect(() => {
    if (phase !== 'closed' || closedAt.current === null || !block.current) {
      return;
    }
    window.scrollBy(0, block.current.getBoundingClientRect().top - closedAt.current);
    closedAt.current = null;
    control.current?.focus({ preventScroll: true });
  }, [phase]);

  const shownByLive = useCallback(() => {
    setPhase((current) => {
      return current === 'loading' ? 'open' : current;
    });
  }, []);

  const verifies = view === 'embed';
  const address = example === undefined ? undefined : liveExampleUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, language, example);
  const fence = useMemo(() => {
    return {
      model,
      address,
      control: phase === 'closed'
        ? (
          <button
            ref={control}
            type="button"
            className={controlStyles.control}
            title={verifies ? 'Opens this example in an editor here, where its cases verify' : 'Opens this example in an editor here'}
            onClick={() => {
              setInitialHeight(code.current?.getBoundingClientRect().height ?? 0);
              setPhase('loading');
            }}
          >
            {verifies ? <RunIcon /> : <EditIcon />}
            {verifies ? 'Run' : 'Edit'}
          </button>
        )
        : phase === 'loading' ? <span className={styles.loadingLabel} role="status">Opening the editor</span> : null,
    };
  }, [model, address, phase, verifies]);

  return (
    <div ref={block} className={`${styles.embed}${phase === 'loading' ? ` ${styles.loading}` : ''}`} aria-busy={phase === 'loading'}>
      <div ref={code} hidden={phase === 'open'}>
        <FenceContext.Provider value={fence}>
          <CodeBlock language={language === 'sysml' ? 'sysmlv2' : language} metastring={open ? 'open' : ''}>
            {shown}
          </CodeBlock>
        </FenceContext.Provider>
      </div>
      {phase === 'loading' ? <div className={styles.progress} /> : null}
      {url === null
        ? null
        : (
          <div className={phase === 'open' ? undefined : styles.pending}>
            <EmbedFrame url={url} initialHeight={initialHeight} onShown={shownByLive} />
          </div>
        )}
      {opened
        ? (
          <button
            type="button"
            className={styles.close}
            onClick={() => {
              closedAt.current = block.current?.getBoundingClientRect().top ?? null;
              setPhase('closed');
            }}
          >
            Close the editor
          </button>
        )
        : null}
    </div>
  );
}
