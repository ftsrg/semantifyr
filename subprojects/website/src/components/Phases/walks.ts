/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/// <reference types="webpack/module" />

import { lineDiff } from '@site/src/lib/lineDiff';
import { EDGES, nodeAt, type Node } from './InstanceTree';

export interface Frame {
  file: string;
  text: string;
}

export interface WalkStep {
  caption: string;
  before: Frame;
  after: Frame;
}

// the pages' bounds: a walk of fewer than ten steps, and lines that both views show whole at a desktop's width
const MAXIMUM_STEPS = 9;
const MAXIMUM_LINE_LENGTH = 60;

// written by the phases Gradle task: the artifacts of the model's one case, with a frame after every single rewrite
const artifacts = require.context('@phases', true, /\/pipeline\/.*\.semantifyr$/);

const STEP = /^pipeline\/steps\/\d+_([a-z]+)\.semantifyr$/;

function textOf(read: (key: string) => unknown, key: string): string {
  return (read(key) as string).replace(/\n$/, '');
}

function caseFiles(): Map<string, string> {
  const directories = new Set(
    artifacts.keys().map((key) => {
      return key.split('/')[1];
    }),
  );
  if (directories.size !== 1) {
    throw new Error(`The phases task wrote ${directories.size} case directories, the pages show one case`);
  }
  return new Map(
    artifacts.keys().map((key) => {
      return [key.split('/').slice(2).join('/'), textOf(artifacts, key)];
    }),
  );
}

const files = caseFiles();

function frame(file: string): Frame {
  const text = files.get(file);
  if (text === undefined) {
    throw new Error(`The compiler wrote no ${file}`);
  }
  return { file, text };
}

function changed(before: string, after: string, kind: 'added' | 'removed'): string[] {
  return lineDiff(before, after)
    .filter((line) => {
      return line.kind === kind;
    })
    .map((line) => {
      return line.text.trim();
    });
}

// the call an expansion replaced: what the line before has where the line after differs, widened to the whole path it calls on
function replacedCall(before: string, after: string): string {
  const removed = changed(before, after, 'removed');
  const added = changed(before, after, 'added');
  const line = removed[0];
  const replacement = added[0];
  if (removed.length !== 1 || added.length !== 1 || line === undefined || replacement === undefined) {
    throw new Error(`An expansion of a property call changed ${removed.length} lines into ${added.length}, the caption names one`);
  }
  let start = 0;
  while (start < line.length && line[start] === replacement[start]) {
    start++;
  }
  while (start > 0 && /[\w.]/.test(line[start - 1] ?? '')) {
    start--;
  }
  const call = /^[\w.]+\(\)/.exec(line.slice(start))?.[0];
  if (call === undefined) {
    throw new Error(`No property call found in the expansion of ${line}`);
  }
  return call;
}

// the one variable a step stops reading: named in the lines it removes and in none it adds
function substitutedVariable(before: string, after: string): string {
  const named = (lines: string[]): Set<string> => {
    return new Set(lines.flatMap((line) => {
      return line.match(/'[^']+'/g) ?? [];
    }));
  };
  const added = named(changed(before, after, 'added'));
  const gone = [...named(changed(before, after, 'removed'))].filter((variable) => {
    return !added.has(variable);
  });
  const variable = gone[0];
  if (gone.length !== 1 || variable === undefined) {
    throw new Error(`A substitution stopped reading ${gone.length} variables, the caption names one`);
  }
  return variable;
}

const INSTANCE_PATH = /(?<![\w.'$])root(?:\.\w+)+/;

// the number each instance path of a changed line became, pairing the lines a change removes with the lines it adds in their place
function numbersOf(before: string, after: string): Map<string, string> {
  const numbers = new Map<string, string>();
  const removed: string[] = [];
  let added = 0;
  for (const line of lineDiff(before, after)) {
    if (line.kind === 'same') {
      removed.length = 0;
      added = 0;
    } else if (line.kind === 'removed') {
      removed.push(line.text);
    } else {
      const replaced = removed[added++];
      const path = replaced === undefined ? null : INSTANCE_PATH.exec(replaced);
      if (replaced !== undefined && path !== null) {
        const [prefix, suffix] = [replaced.slice(0, path.index), replaced.slice(path.index + path[0].length)];
        if (line.text.startsWith(prefix) && line.text.endsWith(suffix)) {
          numbers.set(path[0], line.text.slice(prefix.length, line.text.length - suffix.length));
        }
      }
    }
  }
  return numbers;
}

// the path of the instance tree a variable of the model is named after
function pathOf(variable: string): string {
  return variable.replace(/'/g, '').split('$$').filter((part) => {
    return part !== '';
  }).join('.');
}

function receiverOf(call: string): string {
  return call.slice(0, call.lastIndexOf('.'));
}

type Caption = (before: Frame, after: Frame) => string;

// keyed by the pass name the compiler writes into a step's file name; a pass without a caption fails the build
const CAPTIONS: Readonly<Record<string, Caption>> = {
  operationcallinlining: (before, after) => {
    const call = changed(before.text, after.text, 'removed').find((line) => {
      return line.startsWith('inline ');
    });
    if (call === undefined) {
      throw new Error(`The step ${after.file} removes no inline call`);
    }
    if (changed(before.text, after.text, 'added').length === 0) {
      return `\`${call}\` gives way to the body of the transition it calls, which is empty.`;
    }
    const receiver = receiverOf(call.slice('inline '.length));
    return `\`${call}\` gives way to the body of the transition it calls, as the class of \`${receiver}\` defines it, its names written as paths from \`root\`.`;
  },
  expressioncallinlining: (before, after) => {
    const call = replacedCall(before.text, after.text);
    return `\`${call}\` gives way to the expression the property returns, as the class of \`${receiverOf(call)}\` defines it, its names written as paths from \`root\`.`;
  },
  variablepulldown: (before, after) => {
    const declared = changed(before.text, after.text, 'added').filter((line) => {
      return line.startsWith('var ');
    });
    const variable = /^var ('[^']+')/.exec(declared[0] ?? '')?.[1];
    if (variable === undefined) {
      throw new Error(`The step ${after.file} declares no variable`);
    }
    const holdsInstance = declared.some((line) => {
      return line.includes(': Anything');
    });
    return `Every variable of every instance is declared as a variable of the model, named by its path: \`${variable}\` for \`${pathOf(variable)}\`.${holdsInstance ? ' A variable that holds an instance is typed `Anything` until the instances are numbered.' : ''} The transition and the property still use the instances' variables.`;
  },
  variablereferencerewriting: (before, after) => {
    const rewritten = changed(before.text, after.text, 'added').flatMap((line) => {
      return line.match(/'[^']+'/g) ?? [];
    });
    const variable = rewritten[0];
    if (variable === undefined) {
      throw new Error(`The step ${after.file} rewrites no use of a variable`);
    }
    const instanceValue = after.text.split('\n').some((line) => {
      return !line.startsWith('contains ') && INSTANCE_PATH.test(line);
    });
    return `Every use of a variable through an instance path is rewritten to the variable of the model: \`${pathOf(variable)}\` becomes \`${variable}\`.${instanceValue ? ' An instance named as a value, in a comparison or an assignment, is no variable and keeps its path.' : ''}`;
  },
  staticexpressionrewriting: (before, after) => {
    const numbers = numbersOf(before.text, after.text);
    const numbered = [...numbers].map(([instance, number]) => {
      return `\`${instance}\` the number \`${number}\``;
    });
    return [
      ...before.text.includes(': Anything') ? ['A variable that holds an instance becomes an `int`.'] : [],
      ...before.text.includes('nothing') ? ['`nothing` becomes `-1`.'] : [],
      ...numbered.length === 0 ? [] : [`Every instance an expression names becomes its number, here ${numbered.join(' and ')}.`],
      '`contains root` goes: no instance is left.',
    ].join(' ');
  },
  unusedvariableelimination: (before, after) => {
    const declaration = changed(before.text, after.text, 'removed').find((line) => {
      return line.startsWith('var ');
    });
    if (declaration !== undefined) {
      const variable = /^var ('[^']+')/.exec(declaration)?.[1];
      return `Nothing reads \`${variable}\` any more, so its declaration goes.`;
    }
    const variable = substitutedVariable(before.text, after.text);
    const initial = before.text.split('\n').find((line) => {
      return line.startsWith(`var ${variable}: `);
    })?.split(' := ')[1];
    if (initial === undefined) {
      throw new Error(`The step ${after.file} substitutes ${variable}, which declares no initial value`);
    }
    return `Nothing writes \`${variable}\`, so every read of it becomes its initial value, \`${initial}\`.`;
  },
  redundantoperationremoval: (before, after) => {
    const removed = changed(before.text, after.text, 'removed');
    const operation = removed[0];
    if (removed.length !== 1 || operation === undefined || changed(before.text, after.text, 'added').length !== 0) {
      throw new Error(`The step ${after.file} removes ${removed.length} lines, the caption names one`);
    }
    return `\`${operation}\` neither blocks a step nor changes a variable, so it goes.`;
  },
};

function captionOf(pass: string, before: Frame, after: Frame): string {
  const caption = CAPTIONS[pass];
  if (caption === undefined) {
    throw new Error(`The walk has no caption for the step ${after.file}`);
  }
  return caption(before, after);
}

function checked(phase: string, steps: WalkStep[]): WalkStep[] {
  if (steps.length === 0 || steps.length > MAXIMUM_STEPS) {
    throw new Error(`The walk through ${phase} has ${steps.length} steps, the pages show 1 to ${MAXIMUM_STEPS}`);
  }
  for (const step of steps) {
    if (step.before.text === step.after.text) {
      throw new Error(`The step ${step.after.file} of ${phase} changes nothing`);
    }
    for (const line of [...step.before.text.split('\n'), ...step.after.text.split('\n')]) {
      if (line.length > MAXIMUM_LINE_LENGTH) {
        throw new Error(`A line of ${phase} has ${line.length} characters, the pages show at most ${MAXIMUM_LINE_LENGTH}: ${line}`);
      }
    }
  }
  return steps;
}

function stepsThrough(frames: { frame: Frame, pass: string }[], start: Frame): WalkStep[] {
  return frames.map(({ frame: after, pass }, index) => {
    const before = frames[index - 1]?.frame ?? start;
    return { caption: captionOf(pass, before, after), before, after };
  });
}

function nameOf(node: Node): string {
  return node.order === 1 ? 'The model' : `\`${node.path}\``;
}

// the tree after the first placements, one instance a line under its holder, a shared instance under each of its holders
function treeAfter(placements: number): Frame {
  const placed = EDGES.slice(0, placements);
  const lines: string[] = [];
  const write = (index: number, depth: number, expanded: boolean): void => {
    const node = nodeAt(index);
    lines.push(`${'    '.repeat(depth)}${index === 0 ? node.path : `${node.path}: ${node.type}`}`);
    if (!expanded) {
      return;
    }
    for (const edge of placed) {
      if (edge.from === index) {
        const first = placed.find((other) => {
          return other.to === edge.to;
        });
        write(edge.to, depth + 1, first === edge);
      }
    }
  };
  write(0, 0, true);
  return { file: placements === 0 ? 'the instance tree before instantiation' : `the instance tree after placement ${placements}`, text: lines.join('\n') };
}

function instantiationSteps(): WalkStep[] {
  return EDGES.map((edge, index) => {
    const holder = nodeAt(edge.from);
    const held = nodeAt(edge.to);
    const shared = EDGES.slice(0, index).some((other) => {
      return other.to === edge.to;
    });
    let caption = `${nameOf(holder)} gets an instance for \`${edge.feature}\`: \`${held.path}\`, a \`${held.type}\`.`;
    if (shared) {
      caption = `${nameOf(holder)} gets no instance of its own for \`${edge.feature}\`, which is \`@Shared\`: the instance placed before, \`${held.path}\`, is placed in its slot too.`;
    } else if (edge.redefinedIn !== undefined) {
      caption = `${nameOf(holder)} gets an instance for \`${edge.feature}\`: \`${held.path}\`, a \`${held.type}\`, since \`${edge.redefinedIn}\` redefines the containment with that class.`;
    }
    return { caption, before: treeAfter(index), after: treeAfter(index + 1) };
  });
}

function walks(): { instantiation: WalkStep[], inlining: WalkStep[], flattening: WalkStep[] } {
  const instantiated = frame('pipeline/instantiated.semantifyr');
  const inlined = frame('pipeline/inlined.semantifyr');
  const flattened = frame('pipeline/flattened.semantifyr');
  const frames = [...files.keys()]
    .filter((file) => {
      return STEP.test(file);
    })
    .sort()
    .map((file) => {
      return { frame: frame(file), pass: STEP.exec(file)?.[1] ?? '' };
    });
  // a frame that writes the text of the one before it is no step: inlining writes one when it resolves an instance path to the instance it names
  const steps = frames.filter((step, index) => {
    return step.frame.text !== (frames[index - 1]?.frame ?? instantiated).text;
  });
  // the inlining's frames end with the model inlining writes
  const lastInlining = steps.findLastIndex((step) => {
    return step.frame.text === inlined.text;
  });
  if (lastInlining === -1) {
    throw new Error('No step of the compiler wrote the model inlining ends with');
  }
  // a frame for each rewrite of the flattening that changes the model, then one for each optimization that fires
  const flattening = steps.slice(lastInlining + 1);
  if (flattening.at(-1)?.frame.text !== flattened.text) {
    throw new Error(`The last of the ${flattening.length} steps after inlining is not the model flattening writes`);
  }
  const unused = Object.keys(CAPTIONS).filter((pass) => {
    return !steps.some((step) => {
      return step.pass === pass;
    });
  });
  if (unused.length !== 0) {
    throw new Error(`No step of the compiler is of the passes ${unused.join(', ')}, which the walk has captions for`);
  }
  return {
    instantiation: checked('instantiation', instantiationSteps()),
    inlining: checked('inlining', stepsThrough(steps.slice(0, lastInlining + 1), instantiated)),
    flattening: checked('flattening', stepsThrough(flattening, inlined)),
  };
}

export const { instantiation, inlining, flattening } = walks();
