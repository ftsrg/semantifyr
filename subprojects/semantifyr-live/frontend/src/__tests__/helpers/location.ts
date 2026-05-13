/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export function setLocation(url: string): void {
  const parsed = new URL(url);
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: {
      ancestorOrigins: window.location.ancestorOrigins,
      assign: window.location.assign.bind(window.location),
      reload: window.location.reload.bind(window.location),
      replace: window.location.replace.bind(window.location),
      toString: () => parsed.href,
      href: parsed.href,
      origin: parsed.origin,
      hostname: parsed.hostname,
      host: parsed.host,
      protocol: parsed.protocol,
      pathname: parsed.pathname,
      search: parsed.search,
      hash: parsed.hash,
    },
  });
}
