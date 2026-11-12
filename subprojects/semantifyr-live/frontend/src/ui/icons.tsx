/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

const ICONS = {
  'play': (
    <>
      <path d="M7.5 5.2v13.6a.7.7 0 0 0 1.05.6l11-6.8a.7.7 0 0 0 0-1.2l-11-6.8a.7.7 0 0 0-1.05.6z" fill="currentColor" stroke="none" />
    </>
  ),
  'stop': (
    <>
      <rect x="6.5" y="6.5" width="11" height="11" rx="1.5" fill="currentColor" stroke="none" />
    </>
  ),
  'error': (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M9 9l6 6M15 9l-6 6" />
    </>
  ),
  'monitor': (
    <>
      <rect x="3.5" y="4.5" width="17" height="11.5" rx="1.5" />
      <path d="M9 20h6M12 16v4" />
    </>
  ),
  'shield-check': (
    <>
      <path d="M12 3.5l7 2.6v5.9c0 4.4-3 7.6-7 9.5-4-1.9-7-5.1-7-9.5V6.1z" />
      <path d="M8.7 12.2l2.4 2.4 4.3-4.7" />
    </>
  ),
  'check': (
    <>
      <path d="M5 12.5l4.5 4.5L19 7.5" />
    </>
  ),
  'close': (
    <>
      <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
    </>
  ),
  'chevron-down': (
    <>
      <path d="M7 10l5 5 5-5" />
    </>
  ),
  'link': (
    <>
      <path d="M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1" />
      <path d="M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1" />
    </>
  ),
  'info': (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 11v5.5M12 7.8v.2" />
    </>
  ),
  'sun': (
    <>
      <circle cx="12" cy="12" r="3.8" />
      <path d="M12 3v1.8M12 19.2V21M3 12h1.8M19.2 12H21M5.6 5.6l1.3 1.3M17.1 17.1l1.3 1.3M5.6 18.4l1.3-1.3M17.1 6.9l1.3-1.3" />
    </>
  ),
  'moon': (
    <>
      <path d="M19.5 14.5A7.5 7.5 0 0 1 9.5 4.5a7.5 7.5 0 1 0 10 10z" />
    </>
  ),
  'sliders': (
    <>
      <path d="M4 7.5h9M18 7.5h2M4 16.5h3M11 16.5h9" />
      <circle cx="15.5" cy="7.5" r="2.2" />
      <circle cx="8.5" cy="16.5" r="2.2" />
    </>
  ),
  'alert': (
    <>
      <path d="M10.3 4.6L3.2 17.4A2 2 0 0 0 5 20.3h14a2 2 0 0 0 1.8-2.9L13.7 4.6a2 2 0 0 0-3.4 0z" />
      <path d="M12 9.5v4.5M12 16.8v.2" />
    </>
  ),
  'refresh': (
    <>
      <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3" />
      <path d="M19.5 4.5v4h-4" />
    </>
  ),
  'external': (
    <>
      <path d="M14 4.5h5.5V10M19.5 4.5l-8 8" />
      <path d="M17.5 14v4.5a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1H10" />
    </>
  ),
  'copy': (
    <>
      <rect x="8.5" y="8.5" width="11" height="11" rx="1.5" />
      <path d="M15.5 8.5V6a1.5 1.5 0 0 0-1.5-1.5H6A1.5 1.5 0 0 0 4.5 6v8A1.5 1.5 0 0 0 6 15.5h2.5" />
    </>
  ),
  'lock': (
    <>
      <rect x="5.5" y="10.5" width="13" height="9" rx="1.5" />
      <path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5" />
    </>
  ),
  'file': (
    <>
      <path d="M6.5 3.5h7.5l4 4v12a1 1 0 0 1-1 1h-10.5a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1z" />
      <path d="M13.5 3.5v4.5h4.5" />
    </>
  ),
  'edit': (
    <>
      <path d="M4.5 19.5h4l10-10-4-4-10 10v4zM13 7l4 4" />
    </>
  ),
  'book': (
    <>
      <path d="M4.5 5.5A1.5 1.5 0 0 1 6 4h5.5v15.5H6a1.5 1.5 0 0 0-1.5 1.5zM19.5 5.5A1.5 1.5 0 0 0 18 4h-5.5v15.5H18a1.5 1.5 0 0 1 1.5 1.5z" />
    </>
  ),
  'code': (
    <>
      <path d="M8.5 7.5L4 12l4.5 4.5M15.5 7.5L20 12l-4.5 4.5M13.5 5l-3 14" />
    </>
  ),
  'globe': (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M3.5 12h17M12 3.5c2.5 2.6 3.5 5.4 3.5 8.5s-1 5.9-3.5 8.5c-2.5-2.6-3.5-5.4-3.5-8.5s1-5.9 3.5-8.5z" />
    </>
  ),
  'terminal': (
    <>
      <rect x="3.5" y="5" width="17" height="14" rx="1.5" />
      <path d="M7.5 10l2.5 2-2.5 2M12.5 14.5h4" />
    </>
  ),
  'panel-collapse': (
    <>
      <rect x="3.5" y="4.5" width="17" height="15" rx="2" />
      <path d="M14.5 4.5v15M8 9.5l2.5 2.5L8 14.5" />
    </>
  ),
  'panel-expand': (
    <>
      <rect x="3.5" y="4.5" width="17" height="15" rx="2" />
      <path d="M14.5 4.5v15M10.5 9.5L8 12l2.5 2.5" />
    </>
  ),
  'files': (
    <>
      <path d="M9 3.5h5.5l4 4v9a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1v-12a1 1 0 0 1 1-1z" />
      <path d="M14.5 3.5v4h4M5 7.5v12a1 1 0 0 0 1 1h8.5" />
    </>
  ),
  'input': (
    <>
      <path d="M3.5 12h11M10.5 7.5L15 12l-4.5 4.5M19.5 5v14" />
    </>
  ),
  'transition': (
    <>
      <circle cx="5.5" cy="12" r="2.5" />
      <circle cx="18.5" cy="12" r="2.5" />
      <path d="M8 12h7M13 9.5l2.5 2.5-2.5 2.5" />
    </>
  ),
  'send': (
    <>
      <path d="M4.5 12h14M14 7.5l4.5 4.5-4.5 4.5" />
    </>
  ),
  'value': (
    <>
      <path d="M5.5 9h13M5.5 15h13" />
    </>
  ),
  'dropped': (
    <>
      <path d="M3.5 12h9" />
      <path d="M15 8.5l6 7M21 8.5l-6 7" />
    </>
  ),
  'clock': (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </>
  ),
  'list': (
    <>
      <path d="M9.5 6.5h10M9.5 12h10M9.5 17.5h10" />
      <circle cx="5" cy="6.5" r="1.2" />
      <circle cx="5" cy="12" r="1.2" />
      <circle cx="5" cy="17.5" r="1.2" />
    </>
  ),
  'diagram': (
    <>
      <path d="M5 3.5v17M12 3.5v17M19 3.5v17" strokeDasharray="2 2.6" />
      <path d="M5 9h6.2M9.6 7.4L11.2 9l-1.6 1.6M19 15h-6.2M14.4 13.4L12.8 15l1.6 1.6" />
    </>
  ),
  'open': (
    <>
      <path d="M3.5 18.5V6.5a1.5 1.5 0 0 1 1.5-1.5h4l2 2h7.5A1.5 1.5 0 0 1 20 8.5V10" />
      <path d="M3.5 18.5l2.6-7.2A1.5 1.5 0 0 1 7.5 10.3H21l-2.6 7.2a1.5 1.5 0 0 1-1.4 1H3.5z" />
    </>
  ),
  'save': (
    <>
      <path d="M5 4.5h11l3.5 3.5v11a1 1 0 0 1-1 1H5.5a1 1 0 0 1-1-1V5a.5.5 0 0 1 .5-.5z" />
      <path d="M8 4.5v4.5h7V4.5M8 20v-5.5h8V20" />
    </>
  ),
  'save-as': (
    <>
      <path d="M12 20H5.5a1 1 0 0 1-1-1V5a.5.5 0 0 1 .5-.5h11l3.5 3.5v3" />
      <path d="M8 4.5v4.5h7V4.5" />
      <path d="M14 20v-2.2l5-5 2.2 2.2-5 5H14z" />
    </>
  ),
  'download': (
    <>
      <path d="M12 4.5v10.5M7.5 10.5l4.5 4.5 4.5-4.5M5 19.5h14" />
    </>
  ),
  'upload': (
    <>
      <path d="M12 15.5V4.5M7.5 9l4.5-4.5L16.5 9" />
      <path d="M4.5 15v3.5a1 1 0 0 0 1 1h13a1 1 0 0 0 1-1V15" />
    </>
  ),
  'more': (
    <>
      <circle cx="5.5" cy="12" r="1.5" fill="currentColor" stroke="none" />
      <circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none" />
      <circle cx="18.5" cy="12" r="1.5" fill="currentColor" stroke="none" />
    </>
  ),
  'chevron-right': (
    <>
      <path d="M10 7l5 5-5 5" />
    </>
  ),
} as const

export type IconName = keyof typeof ICONS

interface IconProps {
  name: IconName
  size?: 'small' | 'tiny' | undefined
}

export function Icon({ name, size }: IconProps): React.JSX.Element {
  const sizeClass = size ? ` icon-${size}` : ''
  return (
    <svg className={`icon icon-${name}${sizeClass}`} viewBox="0 0 24 24" aria-hidden="true">
      {ICONS[name]}
    </svg>
  )
}
