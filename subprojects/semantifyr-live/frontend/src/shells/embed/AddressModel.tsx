/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useState } from 'react'
import { decodeCompressedBase64Url, parseLiveEditorUrl } from '@semantifyr/editor-common'

import { findExample, liveFlavors } from '../../examples'

// the model the address carries, as text, while the server has not answered and the editor knows no language
export function AddressModel(): React.JSX.Element | null {
  const [text, setText] = useState<string | null>(null)
  useEffect(() => {
    const address = parseLiveEditorUrl(window.location)
    if (address.compressedModel !== null) {
      void decodeCompressedBase64Url(address.compressedModel).then(setText)
      return
    }
    const [sysml] = liveFlavors([])
    if (address.language === null || address.language === sysml.languageId) {
      setText((findExample(sysml, address.example) ?? sysml.examples[0])?.code ?? null)
    }
  }, [])
  if (text === null) {
    return null
  }
  return (
    <div className="embed-editor-scroll">
      <div className="code">
        {text.replace(/\n+$/, '').split('\n').map((line, index) => {
          const indent = /^ */.exec(line)?.[0].length ?? 0
          return (
            <div key={index} className="code-line is-plain">
              <span className="line-number">{index + 1}</span>
              <span />
              <span className="line-text" style={{ '--indent': indent } as React.CSSProperties}>{line.slice(indent) || ' '}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
