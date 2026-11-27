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
import { FenceContext, OpenLinkContext } from '@site/src/lib/fence';
import styles from './styles.module.css';

type Props = WrapperProps<typeof ButtonsType>;

export default function ButtonsWrapper(props: Props): React.JSX.Element {
  const fence = useContext(FenceContext);
  const openLink = useContext(OpenLinkContext);
  return (
    <div className={styles.row}>
      {fence?.control}
      {openLink}
      <Buttons {...props} className={styles.group} />
    </div>
  );
}
