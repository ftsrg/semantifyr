/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useRef, useState } from 'react'
import { editor as monacoEditor, Uri } from 'monaco-editor'
import type { VerificationTrace } from '@semantifyr/editor-common'

import type { LanguageRuntime } from '../lib/language/types'
import { openDerivedDocument, whenMonacoReady } from '../lib/session/monaco'
import { traceUriOf } from '../lib/session/readOnlyDocuments'
import type { ReadOnlyDocument, TraceOfTab } from '../lib/workspace/documentTabs'
import type { Live } from '../lib/workspace/live'
import { Icon } from '../ui/icons'
import type { TraceSourceContext, TraceView, TraceViewProps } from './registry'

export interface TraceTextSource {
  // the read-only tab the text opens in, one per kind of text
  tabId: string
  // the file the verification wrote, read through the session
  fileUri: string
  label: string
  languageId: string
  banner: string
}

type TextModel = monacoEditor.ITextModel

type TraceText =
  | { state: 'loading' }
  | { state: 'missing' }
  | { state: 'ready', text: string, model: TextModel }

const SCROLLBAR: monacoEditor.IEditorScrollbarOptions = { verticalScrollbarSize: 8, horizontalScrollbarSize: 8, alwaysConsumeMouseWheel: false }

const EDITOR_OPTIONS: monacoEditor.IStandaloneEditorConstructionOptions = {
  readOnly: true,
  readOnlyMessage: { value: 'Read-only.' },
  automaticLayout: true,
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  fontSize: 12,
  lineHeight: 18,
  lineNumbersMinChars: 3,
  glyphMargin: false,
  folding: false,
  renderLineHighlight: 'none',
  overviewRulerLanes: 0,
  stickyScroll: { enabled: false },
  scrollbar: SCROLLBAR,
  padding: { top: 6, bottom: 6 },
}

// the text of a trace in the model the editor's services own, so the language server of the session sees it
async function traceModel(uri: string, languageId: string, text: string): Promise<TextModel | null> {
  await whenMonacoReady()
  const existing = monacoEditor.getModel(Uri.parse(uri))
  if (existing) {
    if (existing.getValue() !== text) {
      existing.pushEditOperations([], [{ range: existing.getFullModelRange(), text }], () => {
        return null
      })
    }
    return existing
  }
  await openDerivedDocument(uri, text)
  const created = monacoEditor.getModel(Uri.parse(uri))
  if (created && created.getLanguageId() !== languageId) {
    monacoEditor.setModelLanguage(created, languageId)
  }
  return created
}

export async function readText(runtime: LanguageRuntime | null, fileUri: string): Promise<string | null> {
  try {
    return await runtime?.api.readDocument(fileUri) ?? null
  } catch {
    return null
  }
}

export function traceTabOf(source: TraceTextSource, text: string, trace?: TraceOfTab): ReadOnlyDocument {
  return {
    id: source.tabId,
    label: source.label,
    uri: traceUriOf(source.fileUri),
    languageId: source.languageId,
    banner: source.banner,
    text,
    trace,
  }
}

// opens the text as the read-only tab the trace area's Open opens
export async function openTraceText(live: Live, runtime: LanguageRuntime | null, source: TraceTextSource, trace: TraceOfTab): Promise<void> {
  const text = await readText(runtime, source.fileUri)
  if (text !== null) {
    live.tabs.open(traceTabOf(source, text, trace))
  }
}

function useTraceText(source: TraceTextSource | null, runtime: LanguageRuntime | null, trace: VerificationTrace): TraceText {
  const [text, setText] = useState<TraceText>({ state: 'loading' })
  const fileUri = source?.fileUri ?? null
  const languageId = source?.languageId ?? ''
  useEffect(() => {
    let current = true
    const stale = (): boolean => {
      return !current
    }
    setText({ state: 'loading' })
    void (async () => {
      const read = fileUri === null ? null : await readText(runtime, fileUri)
      if (stale()) {
        return
      }
      if (fileUri === null || read === null) {
        setText({ state: 'missing' })
        return
      }
      const uri = traceUriOf(fileUri)
      const model = await traceModel(uri, languageId, read)
      if (stale()) {
        return
      }
      setText(model ? { state: 'ready', text: read, model } : { state: 'missing' })
    })()
    return () => {
      current = false
    }
  }, [runtime, fileUri, languageId, trace])
  return text
}

function InlineEditor({ model, growing, wrapping }: { model: TextModel, growing: boolean, wrapping: boolean }): React.JSX.Element {
  const host = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const element = host.current
    if (!element) {
      return
    }
    const created = monacoEditor.create(element, {
      ...EDITOR_OPTIONS,
      model,
      wordWrap: wrapping ? 'on' : 'off',
      scrollbar: growing ? { ...SCROLLBAR, vertical: 'hidden', handleMouseWheel: false } : SCROLLBAR,
    })
    const fit = (): void => {
      element.style.height = `${created.getContentHeight()}px`
    }
    const contentSize = growing ? created.onDidContentSizeChange(fit) : null
    if (growing) {
      fit()
    }
    return () => {
      contentSize?.dispose()
      created.dispose()
    }
  }, [model, growing, wrapping])
  return <div ref={host} className={`trace-editor${growing ? ' is-growing' : ''}`} />
}

function TextBody({ source, context }: { source: TraceTextSource | null, context: TraceViewProps }): React.JSX.Element {
  const text = useTraceText(source, context.runtime, context.trace)
  if (!source || text.state === 'missing') {
    return <div className="trace-hint">The text of this trace could not be read.</div>
  }
  const { openTab } = context
  return (
    <div className="trace-frame">
      <div className="trace-frame-bar">
        <Icon name="lock" size="small" />
        <span className="mono" title={source.label}>{source.label}</span>
        <span>read-only</span>
        {openTab && text.state === 'ready'
          ? (
            <button
              type="button"
              className="button button-small button-outline frame-open"
              title="Open in the editor as a read-only tab"
              onClick={() => {
                openTab(traceTabOf(source, text.text))
              }}
            >
              <Icon name="external" size="tiny" />
              Open
            </button>
          )
          : null}
      </div>
      {text.state === 'ready' ? <InlineEditor model={text.model} growing={context.growing} wrapping={context.wrapping} /> : <div className="trace-editor is-loading" />}
    </div>
  )
}

export function textView(id: string, label: string, sourceOf: (context: TraceSourceContext) => TraceTextSource | null): TraceView {
  return {
    id,
    label,
    layout: 'text',
    source: sourceOf,
    View: (context) => {
      return <TextBody source={sourceOf(context)} context={context} />
    },
  }
}
