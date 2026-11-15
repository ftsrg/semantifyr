/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useContext } from 'react';
import Buttons from '@theme-original/CodeBlock/Buttons';
import type ButtonsType from '@theme/CodeBlock/Buttons';
import type { WrapperProps } from '@docusaurus/types';
import { CodeBlockContextProvider, useCodeBlockContext } from '@docusaurus/theme-common/internal';
import { FenceContext, OpenLinkContext } from '@site/src/lib/fence';
import styles from './styles.module.css';

type Props = WrapperProps<typeof ButtonsType>;

// the row holds an embedded fence's control, the link into live and the original buttons; the copy button copies the fence's whole model
export default function ButtonsWrapper(props: Props): React.JSX.Element {
  const fence = useContext(FenceContext);
  const openLink = useContext(OpenLinkContext);
  const { metadata, wordWrap } = useCodeBlockContext();
  const row = (
    <div className={styles.row}>
      {fence?.control}
      {openLink}
      <Buttons {...props} className={styles.group} />
    </div>
  );
  if (fence === null) {
    return row;
  }
  return (
    <CodeBlockContextProvider metadata={{ ...metadata, code: fence.model }} wordWrap={wordWrap}>
      {row}
    </CodeBlockContextProvider>
  );
}
