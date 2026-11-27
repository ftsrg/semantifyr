/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createContext, type ReactNode } from 'react';

interface FenceValue {
  control: ReactNode;
}

export const FenceContext = createContext<FenceValue | null>(null);
