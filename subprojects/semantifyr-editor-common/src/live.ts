/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface PortfolioInfo {
    id: string;
    displayName: string;
    description: string;
    available: boolean;
}

export interface PortfoliosResponse {
    portfolios: PortfolioInfo[];
    defaultPortfolio: string;
}

export interface FlavorInfo {
    id: string;
    displayName: string;
    description: string;
    languageId: string;
    fileName: string;
    verificationCommand: string;
    discoveryCommand: string;
    validateWitnessCommand: string | null;
    validateWitnessByCase: boolean;
    compiledOxstsCommand?: string | null;
}

// each library's files by their path under the library's root
export interface LibrariesResponse {
    builtin: string[];
    // by flavor, the files a session of the flavor has under its Library directory
    semantic: Record<string, string[]>;
    // the SysML v2 standard library the SysML worker loads
    sysml: string[];
}

export interface FlavorsResponse {
    flavors: FlavorInfo[];
}
