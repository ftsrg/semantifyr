/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import type { ReactNode } from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ButtonsWrapper from '../theme/CodeBlock/Buttons';
import CodeBlockWrapper from '../theme/CodeBlock';
import { FenceContext, OpenLinkContext } from '../lib/fence';

// Goal: the link into live stands in the block's row beside its other controls where the writer asked for it, and nowhere else

vi.mock('@docusaurus/theme-common/internal', () => {
  return {
    useCodeBlockContext: () => {
      return { metadata: { code: 'shown' }, wordWrap: {} };
    },
    CodeBlockContextProvider: ({ children }: { children: ReactNode }) => {
      return children;
    },
  };
});

vi.mock('@site/src/lib/live', () => {
  return {
    useLiveBackendUrl: () => {
      return 'https://live.example';
    },
  };
});

afterEach(() => {
  cleanup();
});

describe('the link into live', () => {
  it('is offered where the fence carries the open word, to the live editor with the model', async () => {
    render(<CodeBlockWrapper language="oxsts" metastring="open">{'class A {}'}</CodeBlockWrapper>);

    const link = await screen.findByRole('link', { name: 'Open in Live' });

    const address = new URL(link.getAttribute('href') ?? '');
    expect(address.origin + address.pathname).toBe('https://live.example/oxsts');
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.textContent).not.toMatch(/[←-⇿]/u);
  });

  it('is absent where no open word stands', () => {
    render(<CodeBlockWrapper language="oxsts" metastring="title=a">{'class A {}'}</CodeBlockWrapper>);

    expect(screen.queryByRole('link')).toBeNull();
  });

  it('is absent from a language live does not open', () => {
    render(<CodeBlockWrapper language="kotlin" metastring="open">{'class A'}</CodeBlockWrapper>);

    expect(screen.queryByRole('link')).toBeNull();
  });
});

describe('the row of a code block', () => {
  it('holds the control, the link, word wrap and copy in the order shown, in one row', () => {
    render(
      <FenceContext.Provider value={{ model: 'whole', control: <button type="button">Run</button> }}>
        <OpenLinkContext.Provider value={<a href="https://live.example">Open in Live</a>}>
          <ButtonsWrapper />
        </OpenLinkContext.Provider>
      </FenceContext.Provider>,
    );

    const row = screen.getByRole('button', { name: 'Run' }).parentElement;
    expect(Array.from(row?.querySelectorAll('a, button') ?? []).map((element) => {
      return element.textContent;
    })).toEqual(['Run', 'Open in Live', 'Word wrap', 'Copy']);
  });

  it('holds word wrap and copy alone in a plain block', () => {
    render(<ButtonsWrapper />);

    expect(screen.getAllByRole('button').map((button) => {
      return button.textContent;
    })).toEqual(['Word wrap', 'Copy']);
    expect(screen.queryByRole('link')).toBeNull();
  });
});
