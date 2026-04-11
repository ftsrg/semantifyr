/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface FlavorInfo {
  id: string;
  displayName: string;
  languageId: string;
  fileName: string;
  verify: boolean;
  verifyCommand: string | null;
}

export interface FlavorsResponse {
  flavors: FlavorInfo[];
}

export async function fetchFlavors(httpBase: string): Promise<FlavorInfo[]> {
  const response = await fetch(`${httpBase}/api/flavors`);
  if (!response.ok) return [];
  const data = await response.json() as FlavorsResponse;
  return data.flavors;
}

export async function fetchFlavor(httpBase: string, language: string): Promise<FlavorInfo | null> {
  const flavors = await fetchFlavors(httpBase);
  return flavors.find((f) => f.id === language) ?? null;
}
