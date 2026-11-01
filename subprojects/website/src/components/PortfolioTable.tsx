/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';

interface Props {
  listing: string;
  verifyHelp: string;
}

interface Leg {
  id: string;
  soundness: string;
}

interface Portfolio {
  id: string;
  description: string;
  legs: Leg[];
}

// The line format of `semantifyr portfolios`; availability depends on the build host, so it is dropped.
const ENTRY = /^(\S+) {2}- {2}.+? {2}(?:Available|Unavailable \(.*\))$/;
const LEG = /^ {4}leg: (\S+) \((\w+)\)$/;
const DEFAULT_PORTFOLIO = /Portfolio id\. Default: '([^']+)'/;

function parsePortfolios(listing: string): Portfolio[] {
  const portfolios: Portfolio[] = [];
  for (const line of listing.split('\n')) {
    const id = ENTRY.exec(line)?.[1];
    const [, legId, soundness] = LEG.exec(line) ?? [];
    const last = portfolios.at(-1);
    if (id !== undefined) {
      portfolios.push({ id, description: '', legs: [] });
    } else if (last && legId !== undefined && soundness !== undefined) {
      last.legs.push({ id: legId, soundness });
    } else if (last && line.startsWith('    ') && !line.trimStart().startsWith('hint:')) {
      last.description = line.trim();
    }
  }
  if (portfolios.length === 0) {
    throw new Error('semantifyr portfolios listed no portfolio');
  }
  return portfolios;
}

export default function PortfolioTable({ listing, verifyHelp }: Props): React.JSX.Element {
  const defaultId = DEFAULT_PORTFOLIO.exec(verifyHelp)?.[1];
  if (defaultId === undefined) {
    throw new Error('semantifyr verify --help names no default portfolio');
  }
  return (
    <table>
      <thead>
        <tr>
          <th>Portfolio</th>
          <th>Description</th>
          <th>Legs</th>
        </tr>
      </thead>
      <tbody>
        {parsePortfolios(listing).map((portfolio) => (
          <tr key={portfolio.id}>
            <td>
              <code>{portfolio.id}</code>
              {portfolio.id === defaultId && ' (default)'}
            </td>
            <td>{portfolio.description}</td>
            <td>
              {portfolio.legs.map((leg) => (
                <div key={leg.id}>
                  <code>{leg.id}</code> {leg.soundness}
                </div>
              ))}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
