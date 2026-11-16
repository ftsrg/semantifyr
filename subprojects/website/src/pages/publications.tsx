/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Layout from '@theme/Layout';

import styles from './publications.module.css';

interface Publication {
  title: string;
  authors: string[];
  venue: string;
  year: number;
  link: string;
}

const PUBLICATIONS: Publication[] = [
  {
    title: 'Towards a Configurable Verification and Validation Framework for Critical Cyber-Physical Systems',
    authors: [
      'Ármin Zavada',
      'Géza Kulcsár',
      'Vince Molnár',
      'Ákos Horváth',
    ],
    venue: '8th IEEE International Conference on Industrial Cyber-Physical Systems (ICPS)',
    year: 2025,
    link: 'https://doi.org/10.1109/ICPS65515.2025.11087849',
  },
  {
    title: 'From Transpilers to Semantic Libraries: Formal Verification With Pluggable Semantics',
    authors: [
      'Ármin Zavada',
      'Kristóf Marussy',
      'Vince Molnár',
    ],
    venue: 'Proceedings of the ACM/IEEE 27th International Conference on Model Driven Engineering Languages and Systems (MODELS Companion)',
    year: 2024,
    link: 'https://doi.org/10.1145/3652620.3686251',
  },
  {
    title: 'From Hard-Coded to Modeled: Towards Making Semantic-Preserving Model Transformations More Flexible',
    authors: [
      'Ármin Zavada',
      'Vince Molnár',
    ],
    venue: 'Proceedings of the 31st Minisymposium of the Department of Measurement and Information Systems',
    year: 2024,
    link: 'https://www.mit.bme.hu/data/minisy/minisy_2024_proceedings.pdf',
  },
];

export default function Publications(): React.JSX.Element {
  return (
    <Layout title="Publications" description="The papers on Semantifyr and its approach of semantic libraries, with their links.">
      <main className="container margin-vert--xl">
        <h1>Publications</h1>
        <p className={styles.lead}>{'The papers on Semantifyr, newest first. To cite Semantifyr, cite the MODELS paper, as the repository\'s citation file gives it.'}</p>
        <ol className={styles.list}>
          {PUBLICATIONS.map((publication) => {
            return (
              <li key={publication.title} className={styles.publication}>
                <a href={publication.link} className={styles.title}>{publication.title}</a>
                <span className={styles.authors}>{publication.authors.join(', ')}</span>
                <span className={styles.venue}>{`${publication.venue}, ${String(publication.year)}`}</span>
              </li>
            );
          })}
        </ol>
      </main>
    </Layout>
  );
}
