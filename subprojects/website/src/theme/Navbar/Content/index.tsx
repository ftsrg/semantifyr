/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useEffect, useId, useRef, useState } from 'react';
import Link from '@docusaurus/Link';
import { useLocation } from '@docusaurus/router';
import useBaseUrl from '@docusaurus/useBaseUrl';
import { useColorMode } from '@docusaurus/theme-common';
import { useNavbarMobileSidebar } from '@docusaurus/theme-common/internal';
import ThemedImage from '@theme/ThemedImage';
import { addressOf, learnParts, sections, SOURCE_CODE } from '@site/src/map';
import { useLiveBackendUrl } from '@site/src/lib/live';
import Icon, { GithubMark } from './Icon';

function useCurrentSection(): string | undefined {
  const { pathname } = useLocation();
  const base = useBaseUrl('/');
  return sections.find((section) => {
    return pathname.startsWith(`${base}${section.to.slice(1)}`);
  })?.label;
}

function Brand(): React.JSX.Element {
  const light = useBaseUrl('/img/logo-full-light.svg');
  const dark = useBaseUrl('/img/logo-full-dark.svg');
  return (
    <Link to="/" className="site-header-brand">
      <ThemedImage className="site-header-logo" alt="Semantifyr" sources={{ light, dark }} />
      <span className="site-header-product">Docs</span>
    </Link>
  );
}

// Learn leads to its page, and its button opens the menu of its parts
function LearnSegment({ current }: { current: boolean }): React.JSX.Element {
  const [open, setOpen] = useState(false);
  const menu = useId();
  const segment = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const { pathname } = useLocation();

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const closeOutside = (event: PointerEvent): void => {
      if (!segment.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', closeOutside);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
    };
  }, [open]);

  return (
    <div
      ref={segment}
      className="site-segment"
      onKeyDown={(event) => {
        if (event.key === 'Escape' && open) {
          setOpen(false);
          button.current?.focus();
        }
      }}
      onBlur={(event) => {
        if (!segment.current?.contains(event.relatedTarget)) {
          setOpen(false);
        }
      }}
    >
      <Link to="/learn/" className="site-segmented-option" aria-current={current ? 'page' : undefined}>Learn</Link>
      <button
        ref={button}
        type="button"
        className="site-segment-toggle"
        aria-label="Parts of Learn"
        aria-expanded={open}
        aria-controls={menu}
        onClick={() => {
          setOpen(!open);
        }}
      >
        <Icon name="chevron-down" />
      </button>
      <ul id={menu} className="site-menu" hidden={!open}>
        {learnParts.map((part) => {
          return (
            <li key={part.label}>
              <Link to={addressOf(part)} className="site-menu-item">{part.label}</Link>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function Sections(): React.JSX.Element {
  const current = useCurrentSection();
  return (
    <nav className="site-segmented" aria-label="Sections">
      {sections.map((section) => {
        if (section.label === 'Learn') {
          return <LearnSegment key={section.label} current={current === section.label} />;
        }
        return (
          <Link key={section.label} to={section.to} className="site-segmented-option" aria-current={current === section.label ? 'page' : undefined}>
            {section.label}
          </Link>
        );
      })}
    </nav>
  );
}

const THEMES = [
  {
    choice: null,
    icon: 'monitor',
    label: 'System',
  },
  {
    choice: 'light',
    icon: 'sun',
    label: 'Light',
  },
  {
    choice: 'dark',
    icon: 'moon',
    label: 'Dark',
  },
] as const;

function ThemeSwitch(): React.JSX.Element {
  const { colorModeChoice, setColorMode } = useColorMode();
  return (
    <div className="site-segmented site-segmented-small" role="group" aria-label="Theme">
      {THEMES.map((theme) => {
        return (
          <button
            key={theme.label}
            type="button"
            className="site-segmented-option"
            aria-pressed={colorModeChoice === theme.choice}
            title={theme.label}
            aria-label={theme.label}
            onClick={() => {
              setColorMode(theme.choice);
            }}
          >
            <Icon name={theme.icon} />
          </button>
        );
      })}
    </div>
  );
}

// on a phone the bar is live's: the brand, and the current section opening the menu
function PhoneMenuButton(): React.JSX.Element {
  const mobileSidebar = useNavbarMobileSidebar();
  const current = useCurrentSection();
  return (
    <button
      type="button"
      className="site-header-phone-menu"
      aria-expanded={mobileSidebar.shown}
      onClick={() => {
        mobileSidebar.toggle();
      }}
    >
      <span className="site-header-phone-menu-text">
        <span className="site-header-phone-menu-kicker">Section</span>
        <span className="site-header-phone-menu-section">{current ?? 'Home'}</span>
      </span>
      <Icon name="chevron-down" />
    </button>
  );
}

export default function NavbarContent(): React.JSX.Element {
  const liveUrl = useLiveBackendUrl();
  return (
    <div className="site-header">
      <div className="site-header-group">
        <Brand />
        <Sections />
      </div>
      <div className="site-header-group site-header-end">
        <a href={SOURCE_CODE} className="site-button">
          <GithubMark />
          Source
        </a>
        <a href={liveUrl} className="site-button">
          <Icon name="external" />
          Open Live
        </a>
        <span className="site-separator" />
        <ThemeSwitch />
      </div>
      <PhoneMenuButton />
    </div>
  );
}
