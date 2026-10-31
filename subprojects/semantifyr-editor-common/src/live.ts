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
    languageId: string;
    fileName: string;
    verificationCommand: string;
    discoveryCommand: string;
    validateWitnessCommand: string | null;
    peekCompiledOutput: boolean;
}

export interface FlavorsResponse {
    flavors: FlavorInfo[];
}
