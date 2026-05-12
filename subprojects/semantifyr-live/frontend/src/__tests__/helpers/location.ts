/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/**
 * Replace {@code window.location} with a parsed URL so tests can drive code paths that read
 * {@code search}, {@code origin}, {@code pathname}, etc. without navigating the jsdom window.
 *
 * <p>Tests that use this should snapshot the original `window.location` in a `beforeEach` and
 * restore it in `afterEach` (the helper does not own the lifecycle, so a single test file
 * stays in control of its own teardown).
 */
export function setLocation(url: string): void {
  const parsed = new URL(url);
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: {
      ...window.location,
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
