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
  // a served example opens in the full live by its address, not by its text
  address?: string | undefined;
}

export const FenceContext = createContext<FenceValue | null>(null);

export const OpenLinkContext = createContext<ReactNode>(null);
