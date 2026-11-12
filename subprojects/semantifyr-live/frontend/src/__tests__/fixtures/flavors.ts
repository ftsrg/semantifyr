/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { FlavorInfo } from '@semantifyr/editor-common';

function oxstsFlavor(id: string, displayName: string): FlavorInfo {
  return {
    id,
    displayName,
    description: `${displayName} description`,
    languageId: 'oxsts',
    fileName: 'snippet.oxsts',
    verificationCommand: 'oxsts.case.verify',
    discoveryCommand: 'oxsts.case.discover',
    validateWitnessCommand: 'oxsts.case.validateWitness',
    validateWitnessByCase: false,
  };
}

export const servedFlavors: FlavorInfo[] = [
  oxstsFlavor('oxsts', 'OXSTS'),
  oxstsFlavor('oxsts-with-gamma-library', 'OXSTS with the Gamma library'),
  oxstsFlavor('oxsts-with-sysmlv2-library', 'OXSTS with the SysML v2 library'),
  {
    id: 'gamma',
    displayName: 'Gamma',
    description: 'Gamma description',
    languageId: 'gamma',
    fileName: 'snippet.gamma',
    verificationCommand: 'gamma.case.verify',
    discoveryCommand: 'gamma.case.discover',
    validateWitnessCommand: 'gamma.witness.validate',
    validateWitnessByCase: true,
  },
];
