/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode'

import type { ResolvedColorMode } from '../util/colorMode'
import { initializeMonaco } from './monaco'

type TokenReader = (name: string) => string

export interface EditorColors {
  workbench: Record<string, string>
  textMateRules: { scope: string[], settings: { foreground: string, fontStyle?: string } }[]
  semanticRules: Record<string, string>
}

const TRANSPARENT = '#00000000'

function hexByte(value: number): string {
  return Math.max(0, Math.min(255, Math.round(value))).toString(16).padStart(2, '0')
}

// VS Code's color settings take hex only, the tokens are hex or rgba()
export function toHexColor(value: string): string {
  const trimmed = value.trim()
  const rgba = /^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$/.exec(trimmed)
  if (!rgba) {
    return trimmed
  }
  const [, red, green, blue, alpha] = rgba
  const alphaByte = alpha === undefined ? '' : hexByte(Number(alpha) * 255)
  return `#${hexByte(Number(red))}${hexByte(Number(green))}${hexByte(Number(blue))}${alphaByte}`
}

export function editorColorsFrom(read: TokenReader): EditorColors {
  const token = (name: string): string => {
    return toHexColor(read(`--${name}`))
  }
  const workbench: Record<string, string> = {
    'editor.background': token('surface-editor'),
    'editor.foreground': token('syntax-name'),
    'editorGutter.background': token('surface-editor'),
    'editorLineNumber.foreground': token('editor-gutter-text'),
    'editorLineNumber.activeForeground': token('editor-gutter-active'),
    'editor.lineHighlightBackground': token('editor-line-highlight'),
    'editor.lineHighlightBorder': TRANSPARENT,
    'editor.selectionBackground': token('editor-selection'),
    'editor.inactiveSelectionBackground': token('editor-selection'),
    'editor.selectionHighlightBackground': token('editor-step-highlight'),
    'editor.wordHighlightBackground': token('editor-step-highlight'),
    'editor.wordHighlightStrongBackground': token('editor-step-highlight'),
    'editor.findMatchHighlightBackground': token('attention-soft'),
    'editorCursor.foreground': token('text-primary'),
    'editorWhitespace.foreground': token('border'),
    'editorIndentGuide.background1': token('border-faint'),
    'editorIndentGuide.activeBackground1': token('border-strong'),
    'editorBracketMatch.background': TRANSPARENT,
    'editorBracketMatch.border': token('border-strong'),
    'editorStickyScroll.background': token('surface-editor'),
    'editorStickyScrollHover.background': token('surface-hover'),
    'editorStickyScroll.shadow': token('border'),
    'editorError.foreground': token('problem'),
    'editorWarning.foreground': token('attention'),
    'editorInfo.foreground': token('text-tertiary'),
    'editorWidget.background': token('surface-overlay'),
    'editorWidget.foreground': token('text-primary'),
    'editorWidget.border': token('border'),
    'editorHoverWidget.background': token('surface-overlay'),
    'editorHoverWidget.border': token('border'),
    'editorSuggestWidget.background': token('surface-overlay'),
    'editorSuggestWidget.border': token('border'),
    'editorSuggestWidget.foreground': token('text-primary'),
    'editorSuggestWidget.selectedBackground': token('surface-selected'),
    'editorSuggestWidget.selectedForeground': token('text-primary'),
    'editorSuggestWidget.highlightForeground': token('accent'),
    'editorSuggestWidget.focusHighlightForeground': token('accent'),
    'peekView.border': token('border-strong'),
    'peekViewEditor.background': token('surface-readonly'),
    'peekViewResult.background': token('surface-panel'),
    'peekViewTitle.background': token('surface-panel'),
    'list.hoverBackground': token('surface-hover'),
    'list.activeSelectionBackground': token('surface-selected'),
    'list.activeSelectionForeground': token('text-primary'),
    'list.inactiveSelectionBackground': token('surface-selected'),
    'list.focusOutline': token('border-strong'),
    'list.highlightForeground': token('accent'),
    'input.background': token('surface-inset'),
    'input.border': token('border'),
    'input.foreground': token('text-primary'),
    'inputOption.activeBorder': token('accent'),
    'focusBorder': token('accent'),
    'textLink.foreground': token('accent'),
    'textLink.activeForeground': token('accent-hover'),
    'editorLink.activeForeground': token('accent'),
    'scrollbarSlider.background': token('surface-selected'),
    'scrollbarSlider.hoverBackground': token('surface-selected'),
    'scrollbarSlider.activeBackground': token('surface-selected'),
    'scrollbar.shadow': TRANSPARENT,
    'widget.shadow': token('scrim'),
  }
  const keyword = token('syntax-keyword')
  const type = token('syntax-type')
  const name = token('syntax-name')
  const string = token('syntax-string')
  const number = token('syntax-number')
  const comment = token('syntax-comment')
  const operator = token('syntax-operator')
  const punctuation = token('syntax-punctuation')
  const functionColor = token('syntax-function')
  return {
    workbench,
    textMateRules: [
      { scope: ['comment', 'punctuation.definition.comment'], settings: { foreground: comment, fontStyle: 'italic' } },
      { scope: ['string', 'punctuation.definition.string'], settings: { foreground: string } },
      { scope: ['constant.numeric'], settings: { foreground: number } },
      { scope: ['constant.language', 'keyword', 'keyword.control', 'storage.type', 'storage.modifier'], settings: { foreground: keyword } },
      { scope: ['keyword.operator'], settings: { foreground: operator } },
      { scope: ['punctuation'], settings: { foreground: punctuation } },
      { scope: ['entity.name.type', 'entity.name.class', 'support.type'], settings: { foreground: type } },
      { scope: ['entity.name.function', 'support.function'], settings: { foreground: functionColor } },
    ],
    semanticRules: {
      namespace: name,
      type,
      class: type,
      enum: type,
      interface: type,
      struct: type,
      typeParameter: type,
      parameter: name,
      variable: name,
      property: name,
      enumMember: name,
      event: name,
      function: functionColor,
      method: functionColor,
      macro: functionColor,
      decorator: functionColor,
      keyword,
      modifier: keyword,
      comment,
      string,
      regexp: string,
      number,
      operator,
    },
  }
}

// the base theme under the customizations, which apply to whichever theme is current
function themeIdFor(colorMode: ResolvedColorMode): string {
  return colorMode === 'dark' ? 'Default Dark Modern' : 'Default Light Modern'
}

// The editor takes its colours from the page's tokens, read after the theme's tokens apply
export async function applyEditorTheme(colorMode: ResolvedColorMode): Promise<void> {
  await initializeMonaco(colorMode)
  const style = getComputedStyle(document.documentElement)
  const colors = editorColorsFrom((name) => {
    return style.getPropertyValue(name)
  })
  const themeId = themeIdFor(colorMode)
  const configuration = vscode.workspace.getConfiguration()
  try {
    await configuration.update('workbench.colorCustomizations', colors.workbench, vscode.ConfigurationTarget.Global)
    await configuration.update('editor.tokenColorCustomizations', { textMateRules: colors.textMateRules }, vscode.ConfigurationTarget.Global)
    await configuration.update('editor.semanticTokenColorCustomizations', { enabled: true, rules: colors.semanticRules }, vscode.ConfigurationTarget.Global)
    await configuration.update('workbench.colorTheme', themeId, vscode.ConfigurationTarget.Global)
  } catch (error) {
    console.warn('semantifyr-live: the editor theme could not be applied', error)
  }
}
