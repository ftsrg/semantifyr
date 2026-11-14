/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { VerdictMark } from '@semantifyr/editor-common/marks';
import { verdictWords, type Verdict } from '@semantifyr/editor-common/terms';
import type { ExpectedVerdict, ServedText } from '@site/src/components/LiveEmbed';
import styles from './styles.module.css';

const VERDICT_OF: Readonly<Record<ExpectedVerdict, Verdict | null>> = {
  Passed: 'passed',
  Failed: 'failed',
  Unknown: null,
};

export default function CaseVerdict({ example, name }: { example: ServedText; name: string }): React.JSX.Element {
  const expected = example.verdicts[name];
  if (expected === undefined) {
    throw new Error(`The manifest beside /${example.language}/${example.id} has no case ${name}`);
  }
  const verdict = VERDICT_OF[expected];
  if (verdict === null) {
    throw new Error(`The manifest gives the case ${name} of /${example.language}/${example.id} an unknown verdict, which a page cannot show`);
  }
  return (
    <span className={`${styles.verdict} status-${verdict}`}>
      <code>{name}</code>
      <VerdictMark status={verdict} />
      <span className={styles.word}>{verdictWords[verdict]}</span>
    </span>
  );
}
