/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resolveBackendUrl } from '../lib/config';

/**
 * resolveBackendUrl reads from `window.location` and `import.meta.env`. The unit test
 * mutates `window.location` via the jsdom escape hatch and stubs the env via Vitest's
 * vi.stubEnv helper, restoring both between tests.
 */
describe('resolveBackendUrl', () => {
  let originalLocation: Location;

  beforeEach(() => {
    originalLocation = window.location;
  });

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: originalLocation,
    });
    vi.unstubAllEnvs();
  });

  function setLocation(url: string) {
    const parsed = new URL(url);
    Object.defineProperty(window, 'location', {
      configurable: true,
      writable: true,
      value: {
        ...originalLocation,
        href: parsed.href,
        origin: parsed.origin,
        hostname: parsed.hostname,
        host: parsed.host,
        protocol: parsed.protocol,
        pathname: parsed.pathname,
        search: parsed.search,
        hash: parsed.hash,
      } as Location,
    });
  }

  it('prefers the ?backend= query parameter over everything else', () => {
    setLocation('https://live.semantifyr.org/?backend=https://other.example');
    vi.stubEnv('VITE_BACKEND_URL', 'https://baked-in.example');
    expect(resolveBackendUrl()).toBe('https://other.example');
  });

  it('falls back to VITE_BACKEND_URL when no query param is present', () => {
    setLocation('https://live.semantifyr.org/');
    vi.stubEnv('VITE_BACKEND_URL', 'https://baked-in.example');
    expect(resolveBackendUrl()).toBe('https://baked-in.example');
  });

  it('defaults to window.location.origin when neither override is provided', () => {
    setLocation('https://live.semantifyr.org/');
    expect(resolveBackendUrl()).toBe('https://live.semantifyr.org');
  });

  it('honours custom ports in the same-origin default', () => {
    setLocation('http://localhost:18080/');
    expect(resolveBackendUrl()).toBe('http://localhost:18080');
  });

  it('ignores an empty ?backend= query parameter and falls through', () => {
    setLocation('https://live.semantifyr.org/?backend=');
    expect(resolveBackendUrl()).toBe('https://live.semantifyr.org');
  });
});
