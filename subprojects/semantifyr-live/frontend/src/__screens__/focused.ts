/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// a visitor's click focuses the page; without focus WebKit refuses the editor's clipboard write before it reads the text it is given, which then rejects unobserved
window.focus()
