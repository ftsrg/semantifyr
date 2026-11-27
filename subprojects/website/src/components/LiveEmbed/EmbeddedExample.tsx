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
import type { ExampleLanguage } from '@semantifyr/editor-common/examples';
import { terms } from '@semantifyr/editor-common/terms';
import { FenceContext } from '@site/src/lib/fence';
import { useLiveBackendUrl } from '@site/src/lib/live';
import OpenInLiveLink from '@site/src/theme/CodeBlock/OpenInLiveLink';
import controlStyles from '@site/src/theme/CodeBlock/Buttons/styles.module.css';
import styles from './styles.module.css';

interface EmbeddedExampleProps {
  model: string;
  example?: string | undefined;
  shown: string;
  lines: LiveEditorLines;
  language: ExampleLanguage;
  view: LiveEditorView;
}

type Phase = 'closed' | 'loading' | 'unanswered' | 'open';

// above the 7 to 10 seconds a desktop takes; a phone may take 90, so the frame keeps loading after it
export const UNANSWERED_AFTER_MILLISECONDS = 15_000;

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

function OpenIcon(): React.JSX.Element {
  return (
    <svg className={controlStyles.controlIcon} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M13 4.5h-7a1.5 1.5 0 0 0-1.5 1.5v12a1.5 1.5 0 0 0 1.5 1.5h12a1.5 1.5 0 0 0 1.5-1.5v-7M20 4l-8 8M14.5 4H20v5.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function EmbeddedExample({ model, example, shown, lines, language, view }: EmbeddedExampleProps): React.JSX.Element {
  const liveBackendUrl = useLiveBackendUrl();
  const { colorMode } = useColorMode();
  const [phase, setPhase] = useState<Phase>('closed');
  const [url, setUrl] = useState<string | null>(null);
  const [initialHeight, setInitialHeight] = useState(0);
  const block = useRef<HTMLDivElement>(null);
  const code = useRef<HTMLDivElement>(null);
  const control = useRef<HTMLButtonElement>(null);
  const closedAt = useRef<number | null>(null);
  const { first, last } = lines;
  const opened = phase !== 'closed';

  useEffect(() => {
    if (!opened) {
      setUrl(null);
      return;
    }
    let active = true;
    void liveEditorUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, { language, example, view, theme: colorMode, lines: { first, last }, model: example === undefined ? model : undefined }).then((address) => {
      if (active) {
        setUrl(address);
      }
    });
    return () => {
      active = false;
    };
  }, [opened, liveBackendUrl, language, example, view, colorMode, model, first, last]);

  useEffect(() => {
    if (phase !== 'loading') {
      return;
    }
    const timer = setTimeout(() => {
      setPhase((current) => {
        return current === 'loading' ? 'unanswered' : current;
      });
    }, UNANSWERED_AFTER_MILLISECONDS);
    return () => {
      clearTimeout(timer);
    };
  }, [phase]);

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
      return current === 'loading' || current === 'unanswered' ? 'open' : current;
    });
  }, []);

  const address = example === undefined ? undefined : liveExampleUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, language, example);
  const fence = useMemo(() => {
    return {
      control: phase === 'closed'
        ? (
          <button
            ref={control}
            type="button"
            className={controlStyles.control}
            title="Opens this example in an editor here"
            onClick={() => {
              setInitialHeight(code.current?.getBoundingClientRect().height ?? 0);
              setPhase('loading');
            }}
          >
            <OpenIcon />
            Open
          </button>
        )
        : phase === 'open'
          ? null
          : (
            <>
              <span className={styles.loadingLabel} role="status">{phase === 'loading' ? 'Opening the editor' : `${terms.live.term} is not answering`}</span>
              {phase === 'unanswered' ? <OpenInLiveLink language={language} code={model} address={address} /> : null}
            </>
          ),
    };
  }, [model, address, phase, language]);

  return (
    <div ref={block} className={`${styles.embed}${phase === 'loading' ? ` ${styles.loading}` : ''}`} aria-busy={phase === 'loading'}>
      <div ref={code} hidden={phase === 'open'}>
        <FenceContext.Provider value={fence}>
          <CodeBlock language={language === 'sysml' ? 'sysmlv2' : language}>
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
