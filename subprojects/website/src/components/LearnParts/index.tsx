/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Link from '@docusaurus/Link';
import { useDocsSidebar } from '@docusaurus/plugin-content-docs/client';
import type { PropSidebarItem } from '@docusaurus/plugin-content-docs';

import styles from './styles.module.css';

type ListedItem = Exclude<PropSidebarItem, { type: 'html' }> & { href: string };

function listed(items: readonly PropSidebarItem[]): ListedItem[] {
  return items.filter((item): item is ListedItem => {
    return item.type !== 'html' && item.href !== undefined;
  });
}

// Learn's parts as its sidebar holds them, each with its description and its pages
export default function LearnParts(): React.JSX.Element {
  const sidebar = useDocsSidebar();
  const parts = listed(sidebar?.items ?? []).filter((item) => {
    return item.customProps?.description !== undefined;
  });
  return (
    <div className={styles.parts}>
      {parts.map((part) => {
        const pages = part.type === 'category' ? listed(part.items) : [];
        return (
          <section key={part.label} className={styles.part}>
            <h2 className={styles.title}>
              <Link to={part.href}>{part.label}</Link>
            </h2>
            <p className={styles.description}>{String(part.customProps?.description)}</p>
            {pages.length === 0
              ? null
              : (
                <ul className={styles.pages}>
                  {pages.map((page) => {
                    return (
                      <li key={page.label}>
                        <Link to={page.href}>{page.label}</Link>
                      </li>
                    );
                  })}
                </ul>
              )}
          </section>
        );
      })}
    </div>
  );
}
