/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useEffect, useRef } from 'react'

import type { ResolvedColorMode } from '../../lib/util/colorMode'
import { bindEditor } from '../../lib/session/editorBinding'
import { applyColorTheme } from '../../lib/session/monaco'
import { useLive } from '../../lib/workspace/liveContext'

import styles from './LiveEditor.module.css'

interface Props {
  languageId: string
  documentUri: string
  initialCode: string
  colorMode: ResolvedColorMode
}

// Mounted once per flavor; an example switch replaces the text of the same model
export default function LiveEditor({ languageId, documentUri, initialCode, colorMode }: Props): React.JSX.Element {
  const live = useLive()
  const hostRef = useRef<HTMLDivElement>(null)
  const mountedWith = useRef({ languageId, documentUri, initialCode, colorMode })

  useEffect(() => {
    const host = hostRef.current
    if (!host) {
      return
    }
    const { languageId: language, documentUri: uri, initialCode: text, colorMode: mode } = mountedWith.current
    const binding = bindEditor(host, live, { uri, languageId: language, text }, mode)
    return () => {
      void binding.dispose()
    }
  }, [live])

  useEffect(() => {
    void applyColorTheme(colorMode)
  }, [colorMode])

  return <div ref={hostRef} className={`${styles.editorPanel} ${styles.editorPanelFill}`} />
}
