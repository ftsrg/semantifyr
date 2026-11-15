/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

export type VerdictStatus =
  | 'passed'
  | 'failed'
  | 'inconclusive'
  | 'error'
  | 'not-run'
  | 'running'
  | 'queued'

const SYSML_PATH = 'M8 1.371L2.285 4.344 8 7.314 13.715 4.344 8 1.371zM1.715 5.943V12.113L6.857 14.857V8.686L1.715 5.943zM14.285 5.943L9.143 8.686V14.857L14.285 12.113V5.943z'

const GAMMA_PATH = 'M3085 14590 c-278 -19 -621 -111 -953 -256 -420 -183 -1022 -604 -1903 -1330 -178 -148 -186 -155 -202 -207 -23 -72 -27 -57 69 -231 l86 -155 62 -6 c62 -7 62 -7 161 55 307 191 664 381 788 419 879 267 1829 -248 2101 -1140 120 -397 220 -1030 286 -1814 88 -1056 76 -1837 -50 -3285 -77 -878 -223 -2269 -243 -2306 -11 -19 -294 -449 -925 -1402 -491 -742 -959 -1490 -1174 -1877 -31 -55 -72 -127 -92 -159 -72 -120 -59 -165 112 -385 224 -288 457 -510 533 -511 73 0 1430 416 1611 494 37 16 37 16 167 414 440 1346 446 1368 999 3684 l110 457 113 148 c745 969 2010 2441 3384 3938 1534 1671 2136 2437 2523 3211 378 755 479 1426 278 1850 -138 291 -320 424 -538 394 -35 -5 -435 -133 -910 -292 l-846 -283 -52 -105 -52 -105 51 -185 c136 -500 179 -812 152 -1100 -71 -734 -485 -1593 -1379 -2865 -277 -394 -1645 -2200 -2156 -2848 -78 -98 -148 -187 -156 -198 -26 -33 -82 -8 -75 35 49 281 194 1529 244 2096 207 2343 56 4112 -425 4975 -347 622 -938 926 -1699 875z'

export const FLAME_PATH = 'M360.262 240.337C369.037 275.925 332.475 311.025 296.4 296.4 266.175 285.675 254.475 250.575 275.437 220.35 322.725 157.462 288.112 58.5 209.625 24.375 245.212 91.65 190.612 153.562 159.412 179.4 128.212 205.237 107.25 232.05 100.912 247.65 69.225 324.675 116.512 398.287 148.687 415.837 134.062 382.687 120.412 319.312 177.937 259.837 177.937 259.837 161.362 323.212 197.437 367.087 233.512 410.962 233.025 443.625 233.025 443.625 289.087 443.625 342.225 409.987 364.65 354.9 381.712 322.237 383.175 270.562 360.262 240.337'

function verdictGlyph(status: VerdictStatus): React.JSX.Element {
  const ring = <circle className="ring" cx="8" cy="8" r="6.75" />
  const dashed = <circle className="ring" cx="8" cy="8" r="6.75" strokeDasharray="2.1 2.3" />
  const disc = <circle className="disc" cx="8" cy="8" r="7.5" />
  switch (status) {
    case 'passed':
      return (
        <>
          {disc}
          <path className="glyph on-disc" d="M4.9 8.2l2.15 2.15 4.1-4.45" />
        </>
      )
    case 'failed':
      return (
        <>
          {disc}
          <path className="glyph on-disc" d="M5.5 5.5l5 5M10.5 5.5l-5 5" />
        </>
      )
    case 'inconclusive':
      return (
        <>
          {ring}
          <path className="glyph" d="M6.2 6.3a1.8 1.8 0 1 1 2.5 1.65c-.45.2-.7.55-.7 1v.3" />
          <circle className="glyph-fill" cx="8" cy="11.25" r=".95" />
        </>
      )
    case 'error':
      return (
        <>
          {ring}
          <path className="glyph" d="M8 4.6v4.1" />
          <circle className="glyph-fill" cx="8" cy="11.1" r=".95" />
        </>
      )
    case 'not-run':
      return dashed
    case 'queued':
      return (
        <>
          {dashed}
          <path className="glyph" d="M8 4.9V8l2.1 1.4" />
        </>
      )
    case 'running':
      return (
        <>
          <circle className="ring" cx="8" cy="8" r="6.75" opacity="0.22" />
          <path className="ring spinner" strokeLinecap="round" d="M8 1.25a6.75 6.75 0 0 1 6.75 6.75" />
        </>
      )
  }
}

export function VerdictMark({ status, label }: { status: VerdictStatus, label?: string }): React.JSX.Element {
  return (
    <svg className={`verdict-mark status-${status}`} viewBox="0 0 16 16" role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}>
      {verdictGlyph(status)}
    </svg>
  )
}

function languageShape(languageId: string): React.JSX.Element {
  switch (languageId) {
    case 'sysml':
      return <path d={SYSML_PATH} />
    case 'gamma':
      return <path transform="translate(2.7 15) scale(.000959 -.000959)" d={GAMMA_PATH} />
    default:
      return <path transform="translate(.6 .25) scale(.0334)" d={FLAME_PATH} />
  }
}

export function LanguageMark({ languageId }: { languageId: string }): React.JSX.Element {
  return (
    <svg
      className="language-mark"
      data-language={languageId}
      viewBox="0 0 16 16"
      fill="currentColor"
      aria-hidden="true"
    >
      {languageShape(languageId)}
    </svg>
  )
}
