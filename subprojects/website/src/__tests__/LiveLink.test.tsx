/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { decodeCompressedBase64Url, encodeCompressedBase64Url } from '@semantifyr/editor-common';
import { afterEach, describe, expect, it, vi } from 'vitest';

import LiveLink from '../components/LiveLink';
import type { PageModel } from '../components/LiveEmbed';

// Goal: a link into live opens a lesson's stage with its text in the address and a served example by the example's address

vi.mock('@site/src/lib/live', () => {
  return {
    useLiveBackendUrl: () => {
      return 'https://live.example';
    },
  };
});

const MODEL = 'class Counter {\n    var value: int := 0\n}\n';

async function pageModel(id?: string): Promise<PageModel> {
  return {
    language: 'oxsts',
    file: 'oxsts-test-models/tutorial/counter.semantifyr',
    id,
    text: MODEL,
    compressedModel: await encodeCompressedBase64Url(MODEL),
    verdicts: {},
  };
}

afterEach(() => {
  cleanup();
});

describe('LiveLink', () => {
  it('opens a model no registry serves with its text in the address', async () => {
    render(<LiveLink example={await pageModel()}>the stage</LiveLink>);

    const address = new URL(screen.getByRole('link', { name: 'the stage' }).getAttribute('href') ?? '');
    expect(address.origin + address.pathname).toBe('https://live.example/language');
    expect(await decodeCompressedBase64Url(new URLSearchParams(address.hash.slice(1)).get('model') ?? '')).toBe(MODEL);
  });

  it('opens a served example by its address', async () => {
    render(<LiveLink example={await pageModel('counter')}>the example</LiveLink>);

    expect(screen.getByRole('link', { name: 'the example' }).getAttribute('href')).toBe('https://live.example/language/counter');
  });
});
