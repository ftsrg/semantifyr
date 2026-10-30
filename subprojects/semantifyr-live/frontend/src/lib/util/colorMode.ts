/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/** The user's preference: explicit light/dark, or follow the OS. */
export type ColorModePreference = 'light' | 'dark' | 'system';

/** The resolved theme applied to the page. */
export type ResolvedColorMode = 'light' | 'dark';
