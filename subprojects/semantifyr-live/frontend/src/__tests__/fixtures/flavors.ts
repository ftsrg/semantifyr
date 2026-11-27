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
    fileName: 'snippet.semantifyr',
    verificationCommand: 'oxsts.case.verify',
    discoveryCommand: 'oxsts.case.discover',
    validateWitnessCommand: 'oxsts.case.validateWitness',
    validateWitnessByCase: false,
  };
}

export const servedFlavors: FlavorInfo[] = [
  oxstsFlavor('oxsts', 'Semantifyr'),
  oxstsFlavor('oxsts-with-gamma-library', 'Semantifyr with the Gamma library'),
  oxstsFlavor('oxsts-with-sysml-library', 'Semantifyr with the SysML v2 library'),
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
