/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

/// <reference types="webpack/module" />

import { lineDiff } from '@site/src/lib/lineDiff';

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
const artifacts = require.context('@phases', true, /\/pipeline\/.*\.oxsts$/);
const models = require.context('@oxsts-test-models/phases', false, /\.oxsts$/);

const STEP = /^pipeline\/steps\/\d+_([a-z]+)\.oxsts$/;

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

function sourceModel(): Frame {
  const keys = models.keys();
  const key = keys[0];
  if (keys.length !== 1 || key === undefined) {
    throw new Error(`oxsts-test-models/phases holds ${keys.length} models, the pages show one`);
  }
  return { file: key.slice(2), text: textOf(models, key) };
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

function receiverOf(call: string): string {
  return call.slice(0, call.lastIndexOf('.'));
}

function captionOf(pass: string, before: Frame, after: Frame): string {
  if (pass === 'operationcallinlining') {
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
  }
  if (pass === 'expressioncallinlining') {
    const call = replacedCall(before.text, after.text);
    return `\`${call}\` gives way to the expression the property returns, as the class of \`${receiverOf(call)}\` defines it, its names written as paths from \`root\`.`;
  }
  if (pass === 'flattening') {
    return 'Every variable of every instance becomes one variable of the model, named by the path of its instance, and `contains root` goes: no instance is left.';
  }
  throw new Error(`The walk has no caption for the step ${after.file}`);
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

function walks(): { instantiation: WalkStep[], inlining: WalkStep[], flattening: WalkStep[] } {
  const instantiated = frame('pipeline/instantiated.oxsts');
  const inlined = frame('pipeline/inlined.oxsts');
  const flattened = frame('pipeline/flattened.oxsts');
  const steps = [...files.keys()]
    .filter((file) => {
      return STEP.test(file);
    })
    .sort()
    .map((file) => {
      return { frame: frame(file), pass: STEP.exec(file)?.[1] ?? '' };
    });
  // the inlining's frames end with the model inlining writes; the flattening writes its own frame only after its optimizations, so none may run between
  const lastInlining = steps.findLastIndex((step) => {
    return step.frame.text === inlined.text;
  });
  if (lastInlining === -1) {
    throw new Error('No step of the compiler wrote the model inlining ends with');
  }
  const flattening = steps.slice(lastInlining + 1);
  if (flattening.length !== 1 || flattening[0]?.pass !== 'flattening' || flattening[0].frame.text !== flattened.text) {
    throw new Error(`After inlining the compiler wrote ${flattening.length} steps, the flattening's frame alone shows the flattening`);
  }
  return {
    instantiation: checked('instantiation', [
      {
        caption: 'The compiler wraps the case in a root: the model contains `root`, an instance of the case, and calls its `init`, its main transition and its property. Instantiation leaves this text as it is: what it builds is the instance tree.',
        before: sourceModel(),
        after: instantiated,
      },
    ]),
    inlining: checked('inlining', stepsThrough(steps.slice(0, lastInlining + 1), instantiated)),
    flattening: checked('flattening', stepsThrough(flattening, inlined)),
  };
}

export const { instantiation, inlining, flattening } = walks();
