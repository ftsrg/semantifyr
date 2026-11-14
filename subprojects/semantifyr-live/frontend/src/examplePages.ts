/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { fileNameOf, servedExamples } from '@semantifyr/editor-common/examples'

import { exampleDescription, exampleTitle, LANGUAGES } from './texts'

export interface ExamplePage {
  fileName: string
  source: string
}

function escapeHtml(text: string): string {
  return text.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
}

export function withHead(html: string, title: string, description: string): string {
  return html.replaceAll('%LIVE_TITLE%', escapeHtml(title)).replaceAll('%LIVE_DESCRIPTION%', escapeHtml(description))
}

// a preview of a shared link reads the page's head without running it, so each example's address gets a page whose head names the model
export function examplePages(template: string, commentOf: (fileName: string) => string | null): ExamplePage[] {
  return servedExamples.map((example) => {
    const language = LANGUAGES[example.language]?.name ?? example.language
    return {
      fileName: `${example.language}/${example.id}/index.html`,
      source: withHead(template, exampleTitle(example.label, language), exampleDescription(example.label, language, commentOf(fileNameOf(example)))),
    }
  })
}
