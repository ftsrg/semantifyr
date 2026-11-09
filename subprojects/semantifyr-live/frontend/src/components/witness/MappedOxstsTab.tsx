/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useRef, useState } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { LiveEditorHandle } from '../editor/LiveEditor'
import { SYSML_OXSTS_URI } from '../../lib/session/sysmlBridge'
import { FONT_SIZE } from '../../lib/util/theme'

interface Props {
  editorHandle: LiveEditorHandle | null
}

export default function MappedOxstsTab({ editorHandle }: Props): React.JSX.Element {
  const hostRef = useRef<HTMLDivElement>(null)
  const [oxsts, setOxsts] = useState<string | null>(() => editorHandle?.getMappedOxsts() ?? null)

  useEffect(() => {
    if (!editorHandle) {
      return
    }
    setOxsts(editorHandle.getMappedOxsts())
    return editorHandle.addMappingListener(() => {
      setOxsts(editorHandle.getMappedOxsts())
    })
  }, [editorHandle])

  useEffect(() => {
    if (!editorHandle || !hostRef.current || oxsts === null) {
      return
    }
    const host = hostRef.current
    let cancelled = false
    let dispose: (() => void) | null = null
    void editorHandle
      .attachReadonlyEditor(host, SYSML_OXSTS_URI, 'oxsts')
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
  }, [editorHandle, oxsts])

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
