/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode'

export interface ProblemEntry {
  severity: 'error' | 'warning' | 'info' | 'hint'
  startLine: number
  startColumn: number
  endLine: number
  endColumn: number
  message: string
  source: string
}

function severityFromString(severity: ProblemEntry['severity']): vscode.DiagnosticSeverity {
  switch (severity) {
    case 'error':
      return vscode.DiagnosticSeverity.Error
    case 'warning':
      return vscode.DiagnosticSeverity.Warning
    case 'info':
      return vscode.DiagnosticSeverity.Information
    case 'hint':
      return vscode.DiagnosticSeverity.Hint
  }
}

function severityToString(severity: vscode.DiagnosticSeverity): ProblemEntry['severity'] {
  switch (severity) {
    case vscode.DiagnosticSeverity.Error:
      return 'error'
    case vscode.DiagnosticSeverity.Warning:
      return 'warning'
    case vscode.DiagnosticSeverity.Information:
      return 'info'
    case vscode.DiagnosticSeverity.Hint:
      return 'hint'
  }
}

export function toVscodeDiagnostics(entries: readonly ProblemEntry[]): vscode.Diagnostic[] {
  return entries.map((entry) => {
    const range = new vscode.Range(
      Math.max(0, entry.startLine),
      Math.max(0, entry.startColumn),
      Math.max(0, entry.endLine),
      Math.max(0, entry.endColumn),
    )
    const diagnostic = new vscode.Diagnostic(range, entry.message, severityFromString(entry.severity))
    diagnostic.source = entry.source
    return diagnostic
  })
}

export function problemsOf(uri: vscode.Uri): ProblemEntry[] {
  try {
    return vscode.languages.getDiagnostics(uri).map((diagnostic) => {
      return {
        severity: severityToString(diagnostic.severity),
        startLine: diagnostic.range.start.line,
        startColumn: diagnostic.range.start.character,
        endLine: diagnostic.range.end.line,
        endColumn: diagnostic.range.end.character,
        message: diagnostic.message,
        source: diagnostic.source ?? '',
      }
    })
  } catch {
    return []
  }
}
