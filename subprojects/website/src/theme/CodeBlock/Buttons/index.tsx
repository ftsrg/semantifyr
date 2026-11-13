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
import { FenceContext } from '@site/src/lib/fence';
import styles from './styles.module.css';

type Props = WrapperProps<typeof ButtonsType>;

// the copy button copies the fence's whole model, and an embedded fence's control sits beside it
export default function ButtonsWrapper(props: Props): React.JSX.Element {
  const fence = useContext(FenceContext);
  const { metadata, wordWrap } = useCodeBlockContext();
  if (fence === null) {
    return <Buttons {...props} />;
  }
  return (
    <CodeBlockContextProvider metadata={{ ...metadata, code: fence.model }} wordWrap={wordWrap}>
      {fence.control === null
        ? <Buttons {...props} />
        : (
          <div className={styles.buttons}>
            {fence.control}
            <Buttons {...props} className={styles.group} />
          </div>
        )}
    </CodeBlockContextProvider>
  );
}
