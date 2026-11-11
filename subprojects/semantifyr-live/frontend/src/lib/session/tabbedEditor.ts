/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode'
import { editor as monacoEditor, KeyCode, KeyMod, Uri } from 'monaco-editor'
import type { Range } from '@semantifyr/editor-common'

import type { ReadableStore } from '../stores/store'
import type { ResolvedColorMode } from '../util/colorMode'
import type { VerificationCaseState } from '../verification/engine'
import { caseRowOf } from '../workspace/caseRows'
import type { DocumentTabsState, ReadOnlyDocument } from '../workspace/documentTabs'
import { libraryTabOf } from '../workspace/libraryTabs'
import type { Live } from '../workspace/live'
import { createEditor, openDerivedDocument, openKeptDocument, setEditorOpener, type EditorInstance } from './monaco'
import { problemsOf } from './problems'
import { LIBRARY_SCHEME } from './readOnlyDocuments'

export interface CursorPosition {
  line: number
  column: number
}

export interface TabbedEditorOptions {
  host: HTMLElement
  live: Live
  document: { uri: string, languageId: string, text: string }
  colorMode: ResolvedColorMode
  // the cursor in the model, null while a read-only tab is shown
  onCursor: (position: CursorPosition | null) => void
  // the model takes no typing while this holds, and no on-screen keyboard opens on it
  modelLocked?: ReadableStore<boolean>
  verdictMarks?: boolean
  // the lines of every document wrap instead of scrolling sideways
  wrapping?: boolean
  // the height the model's text takes, for a host that grows with it
  onContentHeight?: (height: number) => void
}

export interface TabbedEditor {
  dispose: () => Promise<void>
}

type TextModel = monacoEditor.ITextModel

const MARK_CLASS = 'verdict-glyph'

// the selection sources of going to a definition in the document shown
const JUMP_SOURCES = [
  'code.jump',
  'code.navigation',
]

interface Place {
  // the read-only tab, null for the model
  tab: string | null
  line: number
  column: number
}

function readOnlyMessageOf(modelLocked: boolean): { value: string } {
  return { value: modelLocked ? 'Press Edit to change the model.' : 'Read-only. Back to the model to edit.' }
}

function toMonacoRange(range: Range): { startLineNumber: number, startColumn: number, endLineNumber: number, endColumn: number } {
  return {
    startLineNumber: range.start.line + 1,
    startColumn: range.start.character + 1,
    endLineNumber: range.end.line + 1,
    endColumn: range.end.character + 1,
  }
}

function markOf(caseState: VerificationCaseState): monacoEditor.IModelDeltaDecoration {
  const row = caseRowOf(caseState)
  const line = caseState.caseInfo.location.range.start.line + 1
  const time = row.time !== null ? ` · ${row.time}` : ''
  return {
    range: { startLineNumber: line, startColumn: 1, endLineNumber: line, endColumn: 1 },
    options: {
      glyphMarginClassName: `${MARK_CLASS} ${MARK_CLASS}-${row.status}${row.outdated ? ` ${MARK_CLASS}-outdated` : ''}`,
      glyphMarginHoverMessage: { value: `\`${row.name}\` · ${row.word}${row.outdated ? ' · outdated' : ''}${time}` },
      stickiness: monacoEditor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges,
    },
  }
}

// One editor instance for the model and its read-only tabs: each document keeps its view state, the model its undo history
export function bindTabbedEditor(options: TabbedEditorOptions): TabbedEditor {
  const { live } = options
  const modelUri = options.document.uri
  let instance: EditorInstance | null = null
  let disposed = false
  const cleanups: (() => void)[] = []
  const viewStates = new Map<string, monacoEditor.ICodeEditorViewState | null>()
  const ownedModels = new Map<string, TextModel>()
  const openedDocuments = new Map<string, vscode.TextDocument>()
  const caseOfMark = new Map<string, string>()
  let shownSelection = 0
  let restoreModel = (): void => {}
  let removeMarks = (): void => {}

  const caseStates = (): readonly VerificationCaseState[] => {
    const key = live.workspace.get().documentKey
    return key === null ? [] : live.verification.get().get(key)?.cases ?? []
  }

  const attach = (editorInstance: EditorInstance): void => {
    instance = editorInstance
    const editor = editorInstance.editorApp.getEditor()
    const model = editor?.getModel()
    if (!editor || !model) {
      return
    }
    const verdictMarks = options.verdictMarks !== false
    editor.updateOptions({ glyphMargin: verdictMarks, lineNumbersMinChars: 3, fontSize: 13, lineHeight: 20, wordWrap: options.wrapping === true ? 'on' : 'off' })

    const modelLocked = (): boolean => {
      return options.modelLocked?.get() ?? false
    }
    let wasLocked = false
    let everLocked = false
    const applyLock = (): void => {
      const locked = modelLocked()
      everLocked ||= locked
      if (editor.getModel() === model) {
        // the text area honours the read-only flag on a phone, the EditContext does not
        editor.updateOptions({ readOnly: locked, domReadOnly: locked, readOnlyMessage: readOnlyMessageOf(locked), ...(locked ? { editContext: false } : {}) })
        if (wasLocked && !locked) {
          editor.focus()
        }
        if (!wasLocked && locked && document.activeElement instanceof HTMLElement && editor.hasTextFocus()) {
          document.activeElement.blur()
        }
      }
      wasLocked = locked
    }
    if (options.modelLocked) {
      cleanups.push(options.modelLocked.subscribe(applyLock))
    }
    applyLock()
    // on touch the on-screen keyboard shrinks the editor; the cursor stays in sight above it
    const layout = editor.onDidLayoutChange(() => {
      const position = editor.getPosition()
      if (everLocked && position && editor.hasTextFocus()) {
        editor.revealPositionInCenterIfOutsideViewport(position)
      }
    })
    cleanups.push(() => {
      layout.dispose()
    })

    if (options.onContentHeight) {
      const report = options.onContentHeight
      const contentSize = editor.onDidContentSizeChange((event) => {
        report(event.contentHeight)
      })
      cleanups.push(() => {
        contentSize.dispose()
      })
      report(editor.getContentHeight())
    }

    const refreshProblems = (): void => {
      live.problems.set(problemsOf(vscode.Uri.parse(modelUri)))
    }
    const diagnostics = vscode.languages.onDidChangeDiagnostics(refreshProblems)
    cleanups.push(() => {
      diagnostics.dispose()
    })
    refreshProblems()

    // the model outlives the editor, so the marks an earlier editor put on it are taken off too
    const shownMarks = (): string[] => {
      return model.getAllMarginDecorations().filter((decoration) => {
        return decoration.options.glyphMarginClassName?.split(' ').includes(MARK_CLASS) === true
      }).map((decoration) => {
        return decoration.id
      })
    }
    const refreshMarks = (): void => {
      if (model.isDisposed()) {
        return
      }
      const cases = verdictMarks ? caseStates() : []
      const markIds = model.deltaDecorations(shownMarks(), cases.map(markOf))
      caseOfMark.clear()
      markIds.forEach((id, index) => {
        const caseState = cases[index]
        if (caseState) {
          caseOfMark.set(id, caseState.caseInfo.id)
        }
      })
    }
    removeMarks = () => {
      if (!model.isDisposed()) {
        model.deltaDecorations(shownMarks(), [])
      }
    }
    // replacing the whole text drops the model's decorations
    const flushed = model.onDidChangeContent((event) => {
      if (event.isFlush) {
        refreshMarks()
      }
    })
    cleanups.push(() => {
      flushed.dispose()
    })
    if (verdictMarks) {
      cleanups.push(live.verification.subscribe(refreshMarks), live.workspace.subscribe(refreshMarks))
    }
    refreshMarks()

    const mouse = editor.onMouseDown((event) => {
      if (event.target.type !== monacoEditor.MouseTargetType.GUTTER_GLYPH_MARGIN || editor.getModel() !== model) {
        return
      }
      const line = event.target.position.lineNumber
      const markId = model.getLineDecorations(line).find((decoration) => {
        return caseOfMark.has(decoration.id)
      })?.id
      const caseId = markId === undefined ? undefined : caseOfMark.get(markId)
      const caseState = caseStates().find((candidate) => {
        return candidate.caseInfo.id === caseId
      })
      if (!caseState) {
        return
      }
      const row = caseRowOf(caseState)
      if (row.status === 'not-run' || row.outdated) {
        live.verifyCase(caseState.caseInfo.id)
      } else if (row.status !== 'running' && row.status !== 'queued') {
        live.selectCase(caseState.caseInfo.id)
      }
    })
    cleanups.push(() => {
      mouse.dispose()
    })

    // the places a definition was gone to from, which Back returns to across the tabs
    const backPlaces: Place[] = []
    let lastPlace: Place | null = null
    const placeHere = (): Place | null => {
      const position = editor.getPosition()
      return position ? { tab: live.tabs.state.get().active, line: position.lineNumber, column: position.column } : null
    }
    const rememberPlace = (place: Place | null): void => {
      const top = backPlaces.at(-1)
      if (place && (top?.tab !== place.tab || top.line !== place.line || top.column !== place.column)) {
        backPlaces.push(place)
      }
    }
    const cursor = editor.onDidChangeCursorPosition((event) => {
      if (editor.getModel() === model) {
        options.onCursor({ line: event.position.lineNumber, column: event.position.column })
      }
      if (JUMP_SOURCES.includes(event.source)) {
        rememberPlace(lastPlace)
      }
      lastPlace = { tab: live.tabs.state.get().active, line: event.position.lineNumber, column: event.position.column }
    })
    cleanups.push(() => {
      cursor.dispose()
    })
    options.onCursor({ line: 1, column: 1 })

    const modelFor = async (tab: ReadOnlyDocument): Promise<TextModel | null> => {
      const uri = Uri.parse(tab.uri)
      if (tab.text !== undefined) {
        const existing = ownedModels.get(tab.uri) ?? monacoEditor.getModel(uri)
        if (existing) {
          ownedModels.set(tab.uri, existing)
          if (existing.getValue() !== tab.text) {
            existing.pushEditOperations([], [{ range: existing.getFullModelRange(), text: tab.text }], () => {
              return null
            })
          }
          return existing
        }
        openedDocuments.set(tab.uri, await openDerivedDocument(tab.uri, tab.text))
        const created = monacoEditor.getModel(uri)
        if (created) {
          monacoEditor.setModelLanguage(created, tab.languageId)
          ownedModels.set(tab.uri, created)
        }
        return created
      }
      try {
        const opened = await openKeptDocument(tab.uri)
        openedDocuments.set(tab.uri, opened)
      } catch (error) {
        console.warn('semantifyr-live: a read-only document could not be read', error)
        return null
      }
      const resolved = monacoEditor.getModel(uri)
      if (resolved && resolved.getLanguageId() !== tab.languageId) {
        monacoEditor.setModelLanguage(resolved, tab.languageId)
      }
      return resolved
    }

    const show = (target: TextModel, readOnly: boolean): void => {
      const current = editor.getModel()
      if (current === target) {
        return
      }
      if (current) {
        viewStates.set(current.uri.toString(), editor.saveViewState())
      }
      // the sticky lines are redrawn only on a scroll, so the previous document's would stay over the new one
      editor.updateOptions({ stickyScroll: { enabled: false } })
      editor.setModel(target)
      const locked = target === model && modelLocked()
      editor.updateOptions({ readOnly: readOnly || locked, domReadOnly: locked, readOnlyMessage: readOnlyMessageOf(locked) })
      const saved = viewStates.get(target.uri.toString())
      if (saved) {
        editor.restoreViewState(saved)
      }
      editor.updateOptions({ stickyScroll: { enabled: true } })
      const position = editor.getPosition()
      options.onCursor(readOnly || !position ? null : { line: position.lineNumber, column: position.column })
    }

    restoreModel = () => {
      editor.setModel(model)
    }

    // a definition is revealed with the cursor at its start; a place in a read-only tab is selected
    const reveal = (range: Range, select: boolean): void => {
      const target = toMonacoRange(range)
      editor.revealRangeInCenterIfOutsideViewport(target)
      if (select) {
        editor.setSelection(target)
      } else {
        editor.setPosition({ lineNumber: target.startLineNumber, column: target.startColumn })
      }
      editor.focus()
    }

    const applyTabs = async (state: DocumentTabsState): Promise<void> => {
      for (const tab of state.tabs) {
        if (tab.text !== undefined && ownedModels.has(tab.uri)) {
          await modelFor(tab)
        }
      }
      const activeTab = state.tabs.find((tab) => {
        return tab.id === state.active
      })
      if (!activeTab) {
        show(model, false)
        return
      }
      const target = await modelFor(activeTab)
      if (disposed || live.tabs.state.get().active !== activeTab.id || !target) {
        return
      }
      show(target, true)
      if (state.selection?.id === activeTab.id && state.selection.sequence !== shownSelection) {
        shownSelection = state.selection.sequence
        reveal(state.selection.range, true)
      }
    }
    let applying = Promise.resolve()
    cleanups.push(live.tabs.state.subscribe(() => {
      applying = applying.then(() => {
        return applyTabs(live.tabs.state.get())
      })
    }))
    void applyTabs(live.tabs.state.get())

    cleanups.push(live.reveal.subscribe(() => {
      const request = live.reveal.get()
      if (request?.uri !== modelUri) {
        return
      }
      live.tabs.show(null)
      show(model, false)
      reveal(request.range, false)
    }))

    const goBack = editor.addAction({
      id: 'semantifyr.navigateBack',
      label: 'Go Back',
      keybindings: [
        KeyMod.Alt | KeyCode.LeftArrow,
      ],
      run: () => {
        const isOpen = (candidate: Place): boolean => {
          return candidate.tab === null || live.tabs.state.get().tabs.some((tab) => {
            return tab.id === candidate.tab
          })
        }
        let place = backPlaces.pop()
        while (place && !isOpen(place)) {
          place = backPlaces.pop()
        }
        if (!place) {
          return
        }
        const position = { line: place.line - 1, character: place.column - 1 }
        if (place.tab === null) {
          live.tabs.show(null)
          show(model, false)
          reveal({ start: position, end: position }, false)
          return
        }
        const tab = live.tabs.state.get().tabs.find((candidate) => {
          return candidate.id === place.tab
        })
        if (tab) {
          live.tabs.open(tab, { start: position, end: position })
        }
      },
    })
    cleanups.push(() => {
      goBack.dispose()
    })

    cleanups.push(setEditorOpener((modelReference, editorOptions) => {
      rememberPlace(placeHere())
      const opened = modelReference.object.textEditorModel
      const uri = opened.uri.toString()
      const selection = editorOptions && 'selection' in editorOptions ? editorOptions.selection as { startLineNumber: number, startColumn: number, endLineNumber?: number, endColumn?: number } | undefined : undefined
      const range: Range | undefined = selection
        ? {
          start: { line: selection.startLineNumber - 1, character: selection.startColumn - 1 },
          end: { line: (selection.endLineNumber ?? selection.startLineNumber) - 1, character: (selection.endColumn ?? selection.startColumn) - 1 },
        }
        : undefined
      if (uri === modelUri) {
        live.tabs.show(null)
        if (range) {
          show(model, false)
          reveal(range, true)
        }
        return Promise.resolve(editor)
      }
      // a place in a trace text or the OXSTS of the model opens the tab that shows it
      const openTab = live.tabs.state.get().tabs.find((tab) => {
        return tab.uri === uri
      })
      if (openTab) {
        live.tabs.open(openTab, range)
        return Promise.resolve(editor)
      }
      if (opened.uri.scheme !== LIBRARY_SCHEME) {
        return Promise.resolve(undefined)
      }
      live.tabs.open(libraryTabOf(uri, opened.getLanguageId()), range)
      return Promise.resolve(editor)
    }))
  }

  const disposeInstance = async (editorInstance: EditorInstance): Promise<void> => {
    try {
      await editorInstance.editorApp.dispose()
    } catch {
      /* ignore */
    }
    try {
      editorInstance.fsOverlay.dispose()
    } catch {
      /* ignore */
    }
  }

  const ready = createEditor(options.host, options.colorMode, vscode.Uri.parse(modelUri), options.document.languageId, options.document.text)
    .then(async (editorInstance) => {
      if (disposed) {
        await disposeInstance(editorInstance)
        return
      }
      attach(editorInstance)
    })
    .catch((error: unknown) => {
      console.warn('semantifyr-live: the editor failed to start', error)
    })

  return {
    dispose: async () => {
      disposed = true
      for (const cleanup of cleanups) {
        cleanup()
      }
      live.problems.set([])
      options.onCursor(null)
      await ready
      if (instance) {
        restoreModel()
        removeMarks()
        await disposeInstance(instance)
        instance = null
      }
      ownedModels.clear()
      openedDocuments.clear()
      viewStates.clear()
    },
  }
}
