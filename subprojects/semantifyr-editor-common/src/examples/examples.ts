/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export const EXAMPLE_LANGUAGES = [
  'sysml',
  'gamma',
  'oxsts',
] as const;

export type ExampleLanguage = (typeof EXAMPLE_LANGUAGES)[number];

export interface ServedLanguage {
  blank: string;
}

export interface ServedExample {
  id: string;
  language: ExampleLanguage;
  file: string;
  label: string;
  description?: string;
  cases: string[];
  // a model its page runs, which live's menu does not list
  hidden?: boolean;
}

// the expected verdicts of the models beside it, in the shape the corpora share; a corpus may add keys of its own
export interface CaseManifest {
  models: {
    file: string;
    cases: {
      name: string;
      verdict: 'Passed' | 'Failed' | 'Unknown';
      tier?: 'Integration' | 'Nightly';
    }[];
  }[];
}

export const servedLanguages: Readonly<Record<ExampleLanguage, ServedLanguage>> = {
  sysml: {
    blank: '',
  },
  gamma: {
    blank: 'package model\n',
  },
  oxsts: {
    blank: '',
  },
};

export const servedExamples: readonly ServedExample[] = [
  {
    id: 'level-crossing',
    language: 'sysml',
    file: 'subprojects/frontends/sysml/models/examples/level_crossing.sysml',
    label: 'Level crossing',
    description: 'Two railway tracks cross a road at one barrier, which a controller lowers and lifts on the tracks\' reports.',
    cases: [
      'BarrierDownWhileTrainInCrossing',
      'TwoTrainsCanBeInTheCrossing',
    ],
  },
  {
    id: 'crossroads',
    language: 'sysml',
    file: 'subprojects/frontends/sysml/models/examples/crossroads.sysml',
    label: 'Crossroads',
    description: 'Two traffic lights at a crossroads, cycled by one controller and interrupted by the police.',
    cases: [
      'PoliceCanInterrupt',
      'LightACanBlink',
      'RoadBCanGetGreen',
      'NeverBothGreen',
    ],
  },
  {
    id: 'spacecraft',
    language: 'sysml',
    file: 'subprojects/frontends/sysml/models/examples/compressedspacecraft.sysml',
    label: 'Spacecraft',
    description: 'A spacecraft that sends its data to a ground station while it manages its battery.',
    cases: [
      'TransmissionCanStart',
      'ChargeNeverBelow40',
      'CanTransmitWhileRecharging',
    ],
  },
  {
    id: 'landing-gear',
    language: 'sysml',
    file: 'subprojects/frontends/sysml/models/examples/landing_gear.sysml',
    label: 'Landing gear',
    description: 'An aircraft\'s landing gear behind two bay doors, which a controller moves on the pilot\'s handle.',
    cases: [
      'GearCanBeRetracted',
      'BayClosedInCruise',
      'GearMovesOnlyWithDoorsOpen',
    ],
  },
  {
    id: 'lift-step1',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/LiftStep1.gamma',
    label: 'Lift, step 1',
    description: 'A lift whose controller closes the door, waits, runs the motor for a while, then stops it and opens the door again.',
    cases: [
      'LiftCanMove',
    ],
    hidden: true,
  },
  {
    id: 'lift-step2',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/LiftStep2.gamma',
    label: 'Lift, step 2',
    description: 'A lift whose controller closes the door, waits, runs the motor for a while, then stops it and opens the door again.',
    cases: [
      'DoorClosedWhileMoving',
      'LiftCanMove',
    ],
    hidden: true,
  },
  {
    id: 'lift-step3',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/LiftStep3.gamma',
    label: 'Lift, step 3',
    description: 'The lift with a light curtain in its door: an obstacle in the closing door opens it again, and it retries two seconds later.',
    cases: [
      'DoorClosedWhileMoving',
      'LiftCanMove',
    ],
    hidden: true,
  },
  {
    id: 'lift-step4',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/LiftStep4.gamma',
    label: 'Lift, step 4',
    description: 'The lift with a light curtain in its door: an obstacle in the closing door opens it again, and it retries two seconds later.',
    cases: [
      'DoorClosedWhileMoving',
      'LiftCanMove',
      'CanSetOffWithDoorOpen',
    ],
    hidden: true,
  },
  {
    id: 'lift',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/Lift.gamma',
    label: 'Lift',
    description: 'The lift whose controller starts the motor only once the door reports that it is closed.',
    cases: [
      'DoorClosedWhileMoving',
      'LiftCanMove',
      'CanSetOffWithDoorOpen',
    ],
    hidden: true,
  },
  {
    id: 'room',
    language: 'sysml',
    file: 'subprojects/frontends/sysml/models/examples/room.sysml',
    label: 'Room',
    cases: [
      'LampLitReachable',
      'LampNeverLitViolated',
      'LampLitOnlyWhileSwitchOnViolated',
    ],
    hidden: true,
  },
  {
    id: 'level-crossing',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/LevelCrossing.gamma',
    label: 'Level crossing',
    description: 'Two railway tracks cross a road at one barrier, which a controller lowers and lifts on the tracks\' reports.',
    cases: [
      'BarrierDownWhileTrainInCrossing',
      'TwoTrainsCanBeInTheCrossing',
    ],
  },
  {
    id: 'crossroads',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/Crossroads.gamma',
    label: 'Crossroads',
    description: 'Two traffic lights at a crossroads, cycled by one controller and interrupted by the police.',
    cases: [
      'PoliceCanInterrupt',
      'LightACanBlink',
      'RoadBCanGetGreen',
      'NeverBothGreen',
    ],
  },
  {
    id: 'spacecraft',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/Spacecraft.gamma',
    label: 'Spacecraft',
    description: 'A spacecraft that sends its data to a ground station while it manages its battery.',
    cases: [
      'TransmissionCanStart',
      'ChargeNeverBelow40',
      'CanTransmitWhileRecharging',
    ],
  },
  {
    id: 'landing-gear',
    language: 'gamma',
    file: 'subprojects/frontends/gamma/models/examples/LandingGear.gamma',
    label: 'Landing gear',
    description: 'An aircraft\'s landing gear behind its bay doors, which a controller moves on the pilot\'s handle.',
    cases: [
      'GearCanBeRetracted',
      'BayClosedInCruise',
      'GearMovesOnlyWithDoorsOpen',
    ],
  },
  {
    id: 'level-crossing',
    language: 'oxsts',
    file: 'subprojects/website/models/examples/level-crossing.semantifyr',
    label: 'Level crossing',
    description: 'Two railway tracks cross a road at one barrier, which a controller lowers and lifts on the tracks\' reports.',
    cases: [
      'BarrierDownWhileTrainInCrossing',
      'TwoTrainsCanBeInTheCrossing',
    ],
  },
  {
    id: 'modeling-and-verification',
    language: 'oxsts',
    file: 'subprojects/website/models/tutorial/modeling-and-verification.semantifyr',
    label: 'Lesson 1: Modeling and Verification',
    description: 'A junction of two roads, each with a traffic light and a sensor for waiting cars, modelled directly in one class.',
    cases: [
      'NorthCanTurnGreen',
      'NeverBothGreen',
      'NorthGreenOnlyWhileEastRed',
    ],
  },
  {
    id: 'semantic-libraries',
    language: 'oxsts',
    file: 'subprojects/website/models/tutorial/semantic-libraries.semantifyr',
    label: 'Lesson 2: Semantic Libraries',
    description: 'A small statechart library of states, transitions and guards, and the junction with a crosswalk written as data on it.',
    cases: [
      'NorthCanTurnGreen',
      'NeverBothGreen',
      'NorthGreenOnlyWhileEastRed',
      'PedestriansCanWalk',
      'WalkOnlyWhileEastRed',
    ],
  },
  {
    id: 'semantic-variants',
    language: 'oxsts',
    file: 'subprojects/website/models/tutorial/semantic-variants.semantifyr',
    label: 'Lesson 3: Semantic Variants',
    description: 'A small statechart library on which every part of a system steps in every step, all at once, each reacting to the state before the step.',
    cases: [
      'NorthCanTurnGreen',
      'NeverBothGreen',
      'NorthGreenOnlyWhileEastRed',
      'PedestriansCanWalk',
      'WalkOnlyWhileEastRed',
    ],
  },
  {
    id: 'extending-the-library',
    language: 'oxsts',
    file: 'subprojects/website/models/tutorial/extending-the-library.semantifyr',
    label: 'Lesson 4: Extending the Library',
    description: 'A statechart library extended with time: a state counts the steps since it was entered and a transition waits for its minimum.',
    cases: [
      'NorthCanTurnGreen',
      'NeverBothGreen',
      'NorthGreenOnlyWhileEastRed',
      'PedestriansCanWalk',
      'WalkOnlyWhileEastRed',
      'NorthGreenForThreeSteps',
      'NorthCanTurnYellow',
    ],
  },
];

export function servedExamplesOf(language: ExampleLanguage): ServedExample[] {
  return servedExamples.filter((example) => {
    return example.language === language;
  });
}

export function fileNameOf(example: ServedExample): string {
  return example.file.slice(example.file.lastIndexOf('/') + 1);
}
