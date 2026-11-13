/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createContext, type ReactNode } from 'react';

// what a code block shows may be a part of its model; copying and opening it in live take the whole model
export interface FenceValue {
  model: string;
  control: ReactNode;
}

export const FenceContext = createContext<FenceValue | null>(null);

export const OpenLinkContext = createContext<ReactNode>(null);
