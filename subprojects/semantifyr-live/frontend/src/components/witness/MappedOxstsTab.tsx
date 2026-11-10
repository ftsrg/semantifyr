/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useRef } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { DerivedDocument } from '../../lib/language/types'
import type { ReadonlyEditorOpener } from '../../lib/session/monaco'
import { FONT_SIZE } from '../../lib/util/theme'

interface Props {
  document: DerivedDocument
  openReadonlyEditor: ReadonlyEditorOpener | null
}

export default function MappedOxstsTab({ document, openReadonlyEditor }: Props): React.JSX.Element {
  const hostRef = useRef<HTMLDivElement>(null)
  const oxsts = document.text

  useEffect(() => {
    if (!openReadonlyEditor || !hostRef.current || oxsts === null) {
      return
    }
    const host = hostRef.current
    let cancelled = false
    let dispose: (() => void) | null = null
    void openReadonlyEditor(host, document.uri, document.languageId)
      .then((handle) => {
        if (cancelled) {
          handle.dispose()
          return
        }
        dispose = handle.dispose
      })
      .catch((error: unknown) => {
        console.warn('semantifyr-live: failed to attach the OXSTS view', error)
      })
    return () => {
      cancelled = true
      dispose?.()
      host.replaceChildren()
    }
  }, [openReadonlyEditor, oxsts, document.uri, document.languageId])

  if (oxsts === null) {
    return (
      <Box sx={{ flex: '1 1 auto', p: 1.5 }}>
        <Typography sx={{ fontSize: FONT_SIZE.md, color: 'text.secondary', fontStyle: 'italic' }}>
          No OXSTS: the model has errors or constructs Semantifyr does not support.
        </Typography>
      </Box>
    )
  }
  return <Box ref={hostRef} sx={{ flex: '1 1 auto', minHeight: 0, minWidth: 0, bgcolor: 'var(--page-bg)' }} />
}
