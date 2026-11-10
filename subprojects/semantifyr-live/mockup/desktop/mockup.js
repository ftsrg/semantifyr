const languages = {
  sysml: {
    name: 'SysML v2',
    source: 'sysmlNaive',
    documentName: 'level-crossing.sysml',
    model: 'SysML v2 model',
    detail: 'The OMG systems modeling language. Semantifyr verifies the behaviour of its parts.',
    initialization: [
      ['Connecting to live.semantifyr.org', '0.2 s'],
      ['Starting the SysML v2 language server', '0.9 s'],
      ['Loading the SysML v2 standard library', 'about 15 s'],
      ['Finding the verification cases', ''],
    ],
    elapsed: '6 s of about 15 s',
    initializationNote: 'The standard library is the slow part; it loads once per visit. The verification cases are known when it has finished.',
    examples: [
      ['Level crossing', 'Two tracks, a controller and a barrier; the controller forgets a second train.', 2],
      ['Level crossing, fixed', 'The same crossing with a controller that counts the trains.', 2],
      ['Door access', 'Door-access model with parallel state regions.', 9],
      ['Orion protocol', 'Orion protocol with item flows and timing transitions.', 11],
      ['Crossroads', 'Two coordinated traffic lights at an intersection.', 34],
      ['Spacecraft (compressed)', 'Compressed variant of the spacecraft model.', 42],
    ],
  },
  gamma: {
    name: 'Gamma',
    source: 'gammaNaive',
    documentName: 'level-crossing.gamma',
    model: 'Gamma model',
    detail: 'Gamma statecharts composed into synchronous systems.',
    initialization: [
      ['Connecting to live.semantifyr.org', '0.2 s'],
      ['Starting the Gamma language server', '0.5 s'],
      ['Loading the Gamma semantic library', '0.4 s'],
      ['Finding the verification cases', ''],
    ],
    elapsed: '0.3 s',
    initializationNote: 'Takes about a second.',
    examples: [
      ['Level crossing', 'Two tracks, a controller and a barrier; the controller forgets a second train.', 2],
      ['Crossroads', 'Two coordinated traffic lights at an intersection.', 35],
      ['Spacecraft', 'Small spacecraft with battery, thermal, and orbit-control subsystems.', 36],
      ['Leader-worker', 'Two statecharts coordinating via channel-bound events.', 8],
    ],
  },
  oxsts: {
    name: 'OXSTS',
    source: 'oxstsNaive',
    documentName: 'level-crossing.oxsts',
    model: 'OXSTS model',
    detail: 'Semantifyr\'s own language of extended transition systems, which the other two compile to.',
    initialization: [
      ['Connecting to live.semantifyr.org', '0.2 s'],
      ['Starting the OXSTS language server', '0.4 s'],
      ['Loading the OXSTS library', '0.1 s'],
      ['Finding the verification cases', ''],
    ],
    elapsed: '0.1 s',
    initializationNote: 'Takes about a second.',
    examples: [
      ['Level crossing', 'Two tracks, a controller and a barrier; the controller forgets a second train.', 2],
      ['Tutorial: Basics', 'Final state from the "Direct Modeling" tutorial.', 2],
      ['Tutorial: Intermediate', 'Final state from the "Building a Library" tutorial.', 2],
      ['Tutorial: Advanced', 'Final state from the "Advanced Tutorial".', 6],
    ],
  },
};

const flavours = [
  ['plain', 'Plain', 'OXSTS on its own.'],
  ['gamma', 'With the Gamma library', 'Gamma\'s semantics as an OXSTS library to build on.'],
  ['sysml', 'With the SysML v2 library', 'SysML v2\'s semantics as an OXSTS library to build on.'],
];

const caseDefinitions = {
  sysml: [
    { id: 'BarrierDownWhileTrainInCrossing', anchor: 'verification def BarrierDownWhileTrainInCrossing' },
    { id: 'TwoTrainsCanBeInTheCrossing', anchor: 'verification def TwoTrainsCanBeInTheCrossing' },
  ],
  gamma: [
    { id: 'BarrierDownWhileTrainInCrossing', anchor: 'verification case BarrierDownWhileTrainInCrossing' },
    { id: 'TwoTrainsCanBeInTheCrossing', anchor: 'verification case TwoTrainsCanBeInTheCrossing' },
  ],
  oxsts: [
    { id: 'BarrierDownWhileTrainInCrossing', anchor: 'class BarrierDownWhileTrainInCrossing' },
    { id: 'TwoTrainsCanBeInTheCrossing', anchor: 'class TwoTrainsCanBeInTheCrossing' },
  ],
};


const editedLine = '        transition first TrainPresent accept : TrainApproaching via trackA then TrainPresent;';

function sysmlWithProblems() {
  return sources.sysmlNaive
    .replace('send new Lower() via barrier; } then TrainPresent;\n        transition first Idle accept : TrainApproaching via trackB', 'send new Lower() via barrier } then TrainPresent;\n        transition first Idle accept : TrainApproaching via trackB')
    .replace('via trackB do action { send new Lower() via barrier; } then TrainPresent;', 'via trackB do action { send new Lower() via barrier; } then TrainPresnt;');
}

function sysmlEdited() {
  return sources.sysmlNaive.replace('send new Lift() via barrier; } then Idle;\n    }', 'send new Lift() via barrier; } then Idle;\n' + editedLine + '\n    }');
}


const documentMenuLabels = {
  'trace-text': 'Trace of the selected case',
  oxsts: 'OXSTS of the model',
  'oxsts-trace': 'OXSTS of the trace',
  library: 'Library',
  'semantic-library': 'OXSTS semantic library',
};

const documentIds = {
  sysml: ['trace-text', 'oxsts', 'oxsts-trace', 'library', 'semantic-library'],
  gamma: ['trace-text', 'oxsts', 'oxsts-trace', 'semantic-library'],
  oxsts: ['oxsts-trace', 'library'],
};

function readOnlyDocument(id) {
  const language = state.language;
  const caseName = caseNameOf(state.selectedCase === null ? 0 : state.selectedCase);
  const scenario = scenarioOf(selectedOutcome()) || scenarios.failing;
  const failing = scenario === scenarios.failing;
  const witness = {
    label: `${caseName}.witness.oxsts`,
    language: 'oxsts',
    source: () => (language === 'sysml' && failing ? documentSources.sysmlWitnessOxsts : witnessSource(language, scenario, caseName)),
    banner: `The trace of ${caseName}, as the verifier returned it in OXSTS.`,
  };
  const documents = {
    sysml: {
      'trace-text': { label: `${caseName}Trace.sysml`, language: 'sysml', source: () => sources[scenario.trace], banner: `The trace of ${caseName}, written in SysML v2.`, links: true },
      oxsts: { label: 'level-crossing.oxsts', language: 'oxsts', source: () => documentSources.sysmlModelOxsts, banner: 'The OXSTS that level-crossing.sysml compiles to. It follows your edits.' },
      'oxsts-trace': witness,
      library: { label: 'ScalarValues.kerml', language: 'sysml', source: () => documentSources.sysmlScalarValues, banner: 'From the SysML v2 standard library.' },
      'semantic-library': { label: 'States.oxsts', language: 'oxsts', source: () => documentSources.sysmlStatesLibrary, banner: 'How Semantifyr reads SysML v2 state machines: part of its semantic library, in OXSTS.' },
    },
    gamma: {
      'trace-text': { label: `${caseName}.trace.gamma`, language: 'gamma', source: () => gammaTraceSource(scenario, caseName), banner: `The trace of ${caseName}, written in Gamma.` },
      oxsts: { label: 'level-crossing.oxsts', language: 'oxsts', source: () => documentSources.gammaModelOxsts, banner: 'The OXSTS that level-crossing.gamma compiles to. It follows your edits.' },
      'oxsts-trace': witness,
      'semantic-library': { label: 'Statecharts.oxsts', language: 'oxsts', source: () => documentSources.gammaStatecharts, banner: 'How Semantifyr reads Gamma statecharts: part of its semantic library, in OXSTS.' },
    },
    oxsts: {
      'oxsts-trace': witness,
      library: { label: 'semantifyr.oxsts', language: 'oxsts', source: () => documentSources.oxstsLibrary, banner: 'The OXSTS library every model builds on.' },
    },
  };
  return documents[language][id];
}

function baseState() {
  return {
    language: 'sysml',
    flavour: 'plain',
    source: sources.sysmlNaive,
    documentName: 'level-crossing.sysml',
    exampleLabel: 'Level crossing',
    unsaved: false,
    orientation: true,
    connection: 'connected',
    initialization: null,
    verify: 'verify',
    portfolio: { verify: 'Automatic', validate: 'Automatic' },
    portfolioKind: 'verify',
    autoValidate: true,
    cases: [outcomes.notRun, outcomes.notRun],
    names: null,
    selectedCase: null,
    filter: null,
    collapsed: false,
    traceView: 'own',
    selectedItem: null,
    withoutCalls: false,
    longNames: false,
    shared: null,
    glyphHover: null,
    status: { text: 'Ready: 2 verification cases', kind: 'idle' },
    overlay: null,
    errors: null,
    caret: null,
    editedLines: [],
    tabs: ['model'],
    activeTab: 'model',
  };
}

const doneStatus = { text: 'Done in 3.7 s: 1 failed, 1 passed', kind: 'done' };

const resultsState = () => ({
  orientation: false,
  cases: [outcomes.failed, outcomes.passedWithTrace],
  selectedCase: 0,
  status: doneStatus,
});

const markOutdated = (cases) => cases.map((outcome) => (['not-run', 'queued', 'running'].includes(outcome.status) ? outcome : { ...outcome, outdated: true }));

const initializingState = (language, step = 2) => ({
  ...(language === 'sysml' ? {} : languageState(language)),
  connection: 'busy',
  initialization: { step, pressed: false, failed: false },
  status: { text: `Initializing ${languages[language].name}: ${initializationLabel(language, step)}`, kind: 'busy' },
});

function initializationLabel(language, step) {
  const label = languages[language].initialization[step][0];
  return label[0].toLowerCase() + label.slice(1);
}

const fixedState = () => ({
  orientation: false,
  source: sources.sysmlFixed,
  documentName: 'level-crossing-fixed.sysml',
  exampleLabel: 'Level crossing, fixed',
});

const problems = [
  { find: 'via barrier } then TrainPresent;', mark: '}', message: "missing ';' at '}'" },
  { find: 'then TrainPresnt;', mark: 'TrainPresnt', message: "Couldn't resolve reference to StateUsage 'TrainPresnt'." },
];

const languageResults = (language) => ({ ...languageState(language), ...resultsState() });

const sharedNotice = {
  running: "Opened from a shared link. The verdicts marked shared are the sharer's; each is replaced when your run produces its own.",
  done: 'Opened from a shared link. 1 of 2 results came out differently from the shared one.',
};

const allStatesNames = ['BarrierDownWhileTrainInCrossing', 'TwoTrainsCanBeInTheCrossing', 'ControllerNeverStuck', 'BarrierLiftsAfterLastTrain', 'TrainsCounted', 'NoDeadlock', 'SensorsAlwaysSilent', 'BarrierNeverStuckDown', 'ControllerRecovers'];

const stateDefinitions = [
  ['First visit', [
    ['first', 'Ready, not run', () => ({})],
    ['orientation-dismissed', 'Orientation dismissed', () => ({ orientation: false })],
    ['connecting', 'Connecting', () => ({
      connection: 'connecting',
      initialization: { step: 0, pressed: false, failed: false },
      status: { text: 'Connecting to live.semantifyr.org', kind: 'busy' },
    })],
    ['initializing', 'Initializing SysML v2', () => initializingState('sysml')],
    ['initializing-gamma', 'Initializing Gamma', () => initializingState('gamma', 1)],
    ['initializing-oxsts', 'Initializing OXSTS', () => initializingState('oxsts', 2)],
    ['initializing-pressed', 'Pressed while initializing', () => ({
      ...initializingState('sysml'),
      orientation: false,
      verify: 'cancel',
      initialization: { step: 2, pressed: true, failed: false },
    })],
    ['running', 'Running all', () => ({
      orientation: false,
      verify: 'cancel',
      cases: [outcomes.running, outcomes.queued],
      status: { html: 'Verifying <span class="case-ref">BarrierDownWhileTrainInCrossing</span>, 1 of 2', kind: 'busy' },
    })],
    ['running-one', 'Running one case from its row', () => ({
      orientation: false,
      verify: 'cancel',
      cases: [outcomes.running, outcomes.notRun],
      selectedCase: 0,
      status: { html: 'Verifying <span class="case-ref">BarrierDownWhileTrainInCrossing</span>', kind: 'busy' },
    })],
    ['cancelled', 'Cancelled', () => ({
      orientation: false,
      cases: [outcomes.cancelled, outcomes.cancelled],
      status: { text: 'Verification cancelled', kind: 'done' },
    })],
  ]],
  ['Results and marks', [
    ['results', 'Failed and passed, validated', () => resultsState()],
    ['all-states', 'Every row state', () => ({
      orientation: false,
      names: allStatesNames,
      cases: [outcomes.failed, outcomes.passedWithTrace, outcomes.timedOut, outcomes.unsupported, outcomes.error, outcomes.running, outcomes.cancelled, outcomes.queued, outcomes.notRun],
      selectedCase: null,
      status: { text: 'Done: 1 failed, 1 passed, 2 inconclusive, 1 error', kind: 'done' },
    })],
    ['filtered', 'Filtered by a count', () => ({ ...resultsState(), filter: 'failed' })],
    ['collapsed', 'Panel collapsed', () => ({ ...resultsState(), collapsed: true, selectedCase: null })],
    ['glyph-hover', 'Hovering the mark in the editor', () => ({ ...resultsState(), selectedCase: null, glyphHover: 0 })],
    ['validating', 'Validating', () => ({
      ...resultsState(),
      cases: [{ ...outcomes.failed, validation: { state: 'validating', portfolio: 'Automatic' } }, outcomes.passedWithTrace],
    })],
    ['validation-failed', 'Validation failed', () => ({
      ...resultsState(),
      cases: [outcomes.failed, { ...outcomes.passedWithTrace, validation: { state: 'validation-failed', time: '0.9 s', portfolio: 'Automatic', backend: 'Theta' } }],
      selectedCase: 1,
    })],
    ['validation-off', 'Validate automatically off', () => ({
      ...resultsState(),
      autoValidate: false,
      cases: [withValidation(outcomes.failed, null), withValidation(outcomes.passedWithTrace, null)],
    })],
    ['outdated', 'After an edit', () => ({
      ...resultsState(),
      source: sysmlEdited(),
      unsaved: true,
      cases: markOutdated([outcomes.failed, outcomes.passedWithTrace]),
      caret: editedLine.trim(),
      editedLines: [editedLine.trim()],
      status: { text: 'The model changed: 2 results are outdated', kind: 'idle' },
    })],
    ['trace-values', 'Passed with a value (fixed model)', () => ({
      ...fixedState(),
      cases: [outcomes.passed, outcomes.passedFixedWithTrace],
      selectedCase: 1,
      status: { text: 'Done in 5.1 s: 2 passed', kind: 'done' },
    })],
  ]],
  ['Trace: SysML', [
    ['trace-sysml', 'SysML trace text first', () => resultsState()],
    ['trace-sysml-oxsts', 'OXSTS steps behind it', () => ({ ...resultsState(), traceView: 'oxsts' })],
    ['trace-sysml-calls', 'Sequence diagram of the calls', () => ({ ...resultsState(), traceView: 'calls' })],
    ['trace-long-names', 'Long names', () => ({ ...resultsState(), traceView: 'calls', longNames: true })],
    ['trace-long-names-steps', 'Long names, steps', () => ({ ...resultsState(), traceView: 'oxsts', longNames: true })],
    ['trace-passing', 'Passed, with its trace', () => ({ ...resultsState(), selectedCase: 1, traceView: 'calls' })],
  ]],
  ['Trace: Gamma', [
    ['trace-gamma', 'Gamma steps first', () => languageResults('gamma')],
    ['trace-gamma-item', 'Step linked to the model', () => ({ ...languageResults('gamma'), selectedItem: '1:1' })],
    ['trace-gamma-oxsts', 'OXSTS steps behind it', () => ({ ...languageResults('gamma'), traceView: 'oxsts' })],
    ['trace-gamma-calls', 'Sequence diagram of the calls', () => ({ ...languageResults('gamma'), traceView: 'calls' })],
  ]],
  ['Trace: OXSTS', [
    ['trace-oxsts', 'Steps with changed values', () => languageResults('oxsts')],
    ['trace-oxsts-calls', 'Sequence diagram of the calls', () => ({ ...languageResults('oxsts'), traceView: 'calls' })],
    ['trace-oxsts-no-calls', 'No recorded calls', () => ({ ...languageResults('oxsts'), traceView: 'calls', withoutCalls: true })],
  ]],
  ['Read-only tabs', [
    ['tab-trace-text', 'SysML: trace text', () => ({ ...resultsState(), tabs: ['model', 'trace-text'], activeTab: 'trace-text' })],
    ['tab-oxsts', 'SysML: OXSTS of the model', () => ({ ...resultsState(), tabs: ['model', 'trace-text', 'oxsts'], activeTab: 'oxsts' })],
    ['tab-oxsts-trace', 'SysML: OXSTS of the trace', () => ({ ...resultsState(), tabs: ['model', 'oxsts', 'oxsts-trace'], activeTab: 'oxsts-trace' })],
    ['tab-library', 'SysML: library', () => ({ ...resultsState(), tabs: ['model', 'library'], activeTab: 'library' })],
    ['tab-semantic-library', 'SysML: OXSTS semantic library', () => ({ ...resultsState(), tabs: ['model', 'library', 'semantic-library'], activeTab: 'semantic-library' })],
    ['tab-gamma-trace-text', 'Gamma: trace text', () => ({ ...languageResults('gamma'), tabs: ['model', 'trace-text'], activeTab: 'trace-text' })],
    ['tab-gamma-semantic-library', 'Gamma: OXSTS semantic library', () => ({ ...languageResults('gamma'), tabs: ['model', 'oxsts', 'semantic-library'], activeTab: 'semantic-library' })],
    ['tab-oxsts-trace-oxsts', 'OXSTS: the trace', () => ({ ...languageResults('oxsts'), tabs: ['model', 'oxsts-trace'], activeTab: 'oxsts-trace' })],
    ['tab-oxsts-library', 'OXSTS: library', () => ({ ...languageResults('oxsts'), tabs: ['model', 'oxsts-trace', 'library'], activeTab: 'library' })],
    ['documents-menu', 'Read-only documents menu', () => ({ ...resultsState(), overlay: 'documents' })],
  ]],
  ['Languages', [
    ['examples-menu', 'Example menu', () => ({ overlay: 'examples' })],
    ['gamma', 'Gamma, ready', () => languageState('gamma')],
    ['oxsts', 'OXSTS, ready', () => languageState('oxsts')],
    ['oxsts-flavours', 'OXSTS flavour menu', () => ({ ...languageState('oxsts'), overlay: 'flavour' })],
  ]],
  ['File, share, settings', [
    ['open-dialog', 'Open: file dialog', () => ({ overlay: 'open-dialog' })],
    ['file-drop', 'Open: dropping a file', () => ({ overlay: 'drop' })],
    ['unsaved', 'Opened file, unsaved edit', () => ({
      ...fixedState(),
      unsaved: true,
      caret: 'state Watching;',
      status: { html: 'Unsaved changes. <span class="kbd">Ctrl S</span> saves to level-crossing-fixed.sysml', kind: 'idle' },
    })],
    ['file-save', 'Save menu', () => ({ ...fixedState(), unsaved: true, overlay: 'save' })],
    ['share', 'Share popover', () => ({ ...resultsState(), overlay: 'share' })],
    ['share-opened-running', 'Share link opened, running', () => ({
      orientation: false,
      verify: 'cancel',
      shared: 'running',
      cases: [{ ...outcomes.failed, shared: true }, { ...outcomes.passedWithTrace, shared: true }],
      selectedCase: 0,
      status: { html: 'Verifying <span class="case-ref">BarrierDownWhileTrainInCrossing</span>, 1 of 2', kind: 'busy' },
    })],
    ['share-opened-done', 'Share link opened, one differs', () => ({
      orientation: false,
      shared: 'done',
      cases: [outcomes.failed, { ...outcomes.timedOut, differs: 'Passed' }],
      selectedCase: 0,
      status: { text: 'Done in 62 s: 1 failed, 1 inconclusive', kind: 'done' },
    })],
    ['portfolio-verify', 'Verification portfolio', () => ({ ...resultsState(), overlay: 'portfolio', portfolioKind: 'verify' })],
    ['portfolio-validate', 'Validation portfolio', () => ({ ...resultsState(), overlay: 'portfolio', portfolioKind: 'validate' })],
    ['portfolio-engine', 'A single engine chosen', () => ({ ...resultsState(), portfolio: { verify: 'Theta', validate: 'Cross-check' } })],
    ['about', 'About', () => ({ ...resultsState(), overlay: 'about' })],
    ['connection-menu', 'Connection popover', () => ({ ...resultsState(), overlay: 'connection' })],
  ]],
  ['Session trouble', [
    ['disconnected', 'Disconnected on purpose', () => ({
      ...resultsState(),
      connection: 'offline',
      status: { html: 'Disconnected. The editor keeps working; verifying needs the connection. <button class="button-link" data-action="connect">Connect</button>', kind: 'idle' },
    })],
    ['connection-lost', 'Connection lost', () => ({
      ...resultsState(),
      cases: [outcomes.failed, outcomes.notRun],
      connection: 'lost',
      status: { html: 'Connection lost during the run. Reconnecting in 4 s. <button class="button-link" data-action="connect">Reconnect now</button>', kind: 'warning' },
    })],
    ['initialization-failed', 'Initialization failed', () => ({
      ...initializingState('sysml'),
      connection: 'lost',
      initialization: { step: 2, pressed: false, failed: true },
      status: { html: 'Initialization failed: the SysML v2 standard library did not load. <button class="button-link" data-action="reload">Reload</button>', kind: 'warning' },
    })],
    ['queued', 'Queued on a busy server', () => ({
      orientation: false,
      verify: 'cancel',
      cases: [outcomes.queued, outcomes.queued],
      status: { text: 'Queued: the server is busy with other runs; yours starts when it is free', kind: 'busy' },
    })],
  ]],
  ['Outcomes, model problems', [
    ['failure-problems', 'The model has problems', () => ({
      orientation: false,
      source: sysmlWithProblems(),
      unsaved: true,
      errors: problems,
      status: { html: 'Not run: <button class="button-link" data-action="goto-problem">2 problems</button> in the model', kind: 'problem' },
    })],
    ['failure-timeout', 'Inconclusive: timed out', () => ({
      ...resultsState(),
      selectedCase: 1,
      cases: [outcomes.timedOut, outcomes.passedWithTrace],
      status: { text: 'Done: 1 inconclusive, 1 passed', kind: 'done' },
    })],
    ['failure-unsupported', 'Inconclusive: not supported', () => ({
      ...fixedState(),
      cases: [outcomes.unsupported, outcomes.unsupported],
      status: { text: 'Done: 2 inconclusive', kind: 'done' },
    })],
    ['failure-error', 'Error', () => ({
      ...resultsState(),
      selectedCase: null,
      cases: [outcomes.error, outcomes.passedWithTrace],
      status: { text: 'Done: 1 error, 1 passed', kind: 'done' },
    })],
  ]],
];

function languageState(language) {
  const definition = languages[language];
  return {
    language,
    source: sources[definition.source],
    documentName: definition.documentName,
  };
}

const stateBuilders = Object.fromEntries(stateDefinitions.flatMap(([, entries]) => entries.map(([name, , build]) => [name, build])));

let currentName = 'first';
let theme = 'light';
let state = buildState('first');
const dismissedOrientations = new Set();

function buildState(name) {
  return { ...baseState(), ...stateBuilders[name]() };
}

function sourceLines() {
  return state.source.replace(/\n+$/, '').split('\n');
}

function definitions() {
  return caseDefinitions[state.language];
}

function caseNameOf(index) {
  return state.names ? state.names[index] : definitions()[index].id;
}

function selectedOutcome() {
  return state.selectedCase === null ? null : state.cases[state.selectedCase];
}

function scenarioOf(outcome) {
  return outcome && outcome.trace ? scenarios[outcome.trace] : null;
}

function isFinished(outcome) {
  return !['not-run', 'queued', 'running'].includes(outcome.status);
}

function orientationKey() {
  return `${state.language}:${state.exampleLabel}`;
}

function selectedFind() {
  const scenario = scenarioOf(selectedOutcome());
  if (!scenario || state.language !== 'gamma' || state.traceView !== 'own' || !state.selectedItem) return null;
  return stepItemFind(scenario, state.selectedItem);
}

function renderCodeLines(lines, language, options) {
  const caseLines = new Map();
  if (options.marks) definitions().forEach((definition, index) => caseLines.set(findLine(lines, [definition.anchor]), index));
  const errorLines = new Map((state.errors || []).map((error) => [findLine(lines, [error.find]), error]));
  return lines.map((line, index) => {
    const indent = line.match(/^ */)[0].length;
    let html = highlight(line.slice(indent), language);
    const classes = ['code-line'];
    if (options.selected === index) classes.push('is-selected');
    if (options.current === index) classes.push('is-current');
    if (state.editedLines.some((text) => line.trim() === text)) classes.push('is-edited');
    if (state.caret && line.trim() === state.caret) classes.push('has-caret', 'is-current');
    const error = errorLines.get(index);
    if (error) {
      const target = error.mark === '}' ? '<span class="syntax-punctuation">}</span>' : escapeHtml(error.mark);
      const at = html.lastIndexOf(target);
      html = html.slice(0, at) + `<span class="squiggle">${target}</span>` + html.slice(at + target.length) + `<span class="inline-diagnostic">${escapeHtml(error.message)}</span>`;
    }
    let glyph = '<span class="line-glyph"></span>';
    if (caseLines.has(index)) {
      const caseIndex = caseLines.get(index);
      const outcome = state.cases[caseIndex];
      const open = state.glyphHover === caseIndex;
      glyph = `<span class="line-glyph has-mark ${outcome.outdated ? 'is-outdated' : ''} ${open ? 'is-hovered' : ''}" data-action="glyph" data-value="${caseIndex}">${verdictMark(outcome.status)}${renderGlyphTip(caseNameOf(caseIndex), outcome)}</span>`;
      if (open) classes.push('is-hovered');
    }
    return `<div class="${classes.join(' ')}" data-line="${index}">${glyph}<span class="line-number">${index + 1}</span><span class="line-text" style="--indent:${indent}">${html || ' '}</span></div>`;
  }).join('');
}

function renderToolbar() {
  const switchOptions = Object.entries(languages).map(([id, entry]) => `
    <button class="segmented-option" data-language="${id}" aria-pressed="${id === state.language}" data-action="language" data-value="${id}" title="${entry.detail}">${languageMark(id)}${entry.name}</button>`).join('');
  const flavour = state.language === 'oxsts'
    ? `<button class="button button-quiet flavour-button" data-action="flavour" aria-expanded="${state.overlay === 'flavour'}" title="OXSTS flavour">${flavours.find(([id]) => id === state.flavour)[1]}${icon('chevron-down', 'icon-small')}</button>`
    : '';
  return `
  <header class="toolbar">
    <div class="toolbar-group">
      <div class="brand">${logo()}${connectionIndicator(state.connection, state.overlay === 'connection')}</div>
      <div class="language-switch"><div class="segmented">${switchOptions}</div>${flavour}</div>
      <span class="separator"></span>
      <button class="button button-quiet example-button" data-action="examples" aria-expanded="${state.overlay === 'examples'}">${state.exampleLabel || 'Examples'}${icon('chevron-down', 'icon-small chevron')}</button>
    </div>
    <div class="toolbar-group toolbar-group-end">
      <button class="button button-quiet" data-action="open" aria-expanded="${state.overlay === 'open-dialog'}">${icon('open')}Open</button>
      <span class="save-split">
        <button class="button button-quiet" data-action="save">${icon('save')}Save</button>
        <button class="button button-quiet" data-action="save-menu" aria-expanded="${state.overlay === 'save'}">${icon('chevron-down', 'icon-small')}</button>
      </span>
      <button class="button button-quiet" data-action="share" aria-expanded="${state.overlay === 'share'}">${icon('link')}Share</button>
      <button class="button button-quiet" data-action="about" aria-expanded="${state.overlay === 'about'}">${icon('info')}About</button>
      <button class="button button-icon" data-action="developer" title="Developer panel">${icon('terminal')}</button>
      <span class="separator"></span>
      <button class="button button-icon" data-action="theme" title="Switch theme">${icon(theme === 'light' ? 'moon' : 'sun')}</button>
    </div>
  </header>`;
}

function renderOrientation() {
  const entry = languages[state.language].examples.find(([label]) => label === state.exampleLabel);
  const visible = state.orientation && entry && !dismissedOrientations.has(orientationKey());
  if (!visible) return '';
  const [label, detail, count] = entry;
  return `
  <div class="orientation">
    <span class="orientation-mark"></span>
    <span class="orientation-text"><strong>${label}.</strong> ${detail} It has ${count} verification cases. Press <strong>Verify all</strong> to check them.</span>
    <button class="button button-icon" data-action="dismiss" title="Dismiss">${icon('close', 'icon-small')}</button>
  </div>`;
}

function renderTabs() {
  const tabs = state.tabs.map((id) => {
    const selected = id === state.activeTab;
    if (id === 'model') {
      return `<button class="tab tab-model" role="tab" aria-selected="${selected}" data-action="tab" data-value="model">${languageMark(state.language)}<span class="tab-label">${state.documentName}</span>${state.unsaved ? '<span class="unsaved-mark" title="Unsaved changes"></span>' : ''}</button>`;
    }
    const document = readOnlyDocument(id);
    return `<button class="tab is-readonly" role="tab" aria-selected="${selected}" data-action="tab" data-value="${id}" title="${document.label}, read-only">${icon('lock')}<span class="tab-label">${document.label}</span><span class="tab-close" data-action="close-tab" data-value="${id}" title="Close">${icon('close')}</span></button>`;
  }).join('');
  return `<div class="tabs" role="tablist">${tabs}<span class="tabs-spacer"></span><span class="tabs-end"><button class="button button-icon" data-action="documents" aria-expanded="${state.overlay === 'documents'}" title="Read-only documents">${icon('files', 'icon-small')}</button></span></div>`;
}

function renderEditor() {
  const drop = state.overlay === 'drop' ? `
    <div class="drop-target">
      <div class="drop-file">${icon('file', 'icon-small')}level-crossing-fixed.sysml</div>
      ${icon('upload')}
      <div class="drop-target-title">Drop to open <span class="mono">level-crossing-fixed.sysml</span></div>
      <div class="drop-target-detail">A .sysml, .gamma or .oxsts file opens in its language. Live opens single files; imports of other files are not resolved.</div>
    </div>` : '';
  if (state.activeTab !== 'model') {
    const document = readOnlyDocument(state.activeTab);
    return `
    <section class="editor is-readonly" data-language="${state.language}">
      ${renderTabs()}
      <div class="readonly-banner">${icon('lock')}<span>Read-only. ${document.banner}</span><button class="button-link" data-action="tab" data-value="model">Back to the model</button></div>
      <div class="editor-body"><div class="editor-scroll" id="editor-scroll"><div class="code">${renderPlainCode(document.source(), document.language, document.links)}</div></div></div>
    </section>`;
  }
  const lines = sourceLines();
  const find = selectedFind();
  const current = state.selectedCase !== null && !find ? findLine(lines, [definitions()[state.selectedCase].anchor]) : -1;
  return `
  <section class="editor" data-language="${state.language}">
    ${renderTabs()}
    <div class="editor-body">
      <div class="editor-scroll" id="editor-scroll"><div class="code">${renderCodeLines(lines, state.language, { marks: !state.initialization, selected: find ? findLine(lines, find) : -1, current })}</div></div>
      ${drop}
    </div>
  </section>`;
}

function renderVerifyButton() {
  return state.verify === 'cancel'
    ? `<button class="button button-cancel" data-action="cancel">${icon('stop', 'icon-small')}Cancel</button>`
    : `<button class="button button-primary" data-action="verify-all">${icon('play', 'icon-small')}Verify all</button>`;
}

function renderRunSettings() {
  const choice = (kind) => `<button class="setting-choice" data-action="portfolio" data-value="${kind}" aria-expanded="${state.overlay === 'portfolio' && state.portfolioKind === kind}">${state.portfolio[kind]}${icon('chevron-down', 'icon-tiny')}</button>`;
  return `
  <div class="run-settings">
    <div class="setting-row"><span class="setting-label">Verification</span>${choice('verify')}</div>
    <div class="setting-row"><span class="setting-label">Validation</span>${choice('validate')}
      <button class="switch-row" role="switch" aria-checked="${state.autoValidate}" data-action="auto-validate"><span class="switch"></span><span>validate automatically</span></button>
    </div>
  </div>`;
}

function renderInitialization() {
  const definition = languages[state.language];
  const initialization = state.initialization;
  const steps = definition.initialization.map(([label, time], index) => {
    if (index < initialization.step) return `<li class="initialization-step is-done">${icon('check')}<span>${label}</span><span class="initialization-step-time">${time}</span></li>`;
    if (index === initialization.step && initialization.failed) return `<li class="initialization-step is-failed">${icon('alert')}<span>${label}</span><span class="initialization-step-time">failed</span></li>`;
    if (index === initialization.step) return `<li class="initialization-step is-current">${verdictMark('running')}<span>${label}</span><span class="initialization-step-time">${definition.elapsed}</span></li>`;
    return `<li class="initialization-step">${verdictMark('not-run')}<span>${label}</span><span></span></li>`;
  }).join('');
  const pressed = initialization.pressed ? `<div class="initialization-pressed">${icon('clock')}<span>Verify all is pressed. Every case runs as soon as initialization has finished.</span></div>` : '';
  const failed = initialization.failed ? `<div><button class="button button-outline button-small" data-action="reload">${icon('refresh', 'icon-tiny')}Reload</button></div>` : '';
  return `<div class="initialization" data-language="${state.language}">
    <div class="initialization-title">${languageMark(state.language)}Initializing ${definition.name}</div>
    <ul class="initialization-steps">${steps}</ul>
    ${initialization.failed ? '' : '<div class="progress-indeterminate"></div>'}
    ${failed}
    <div class="initialization-note">${initialization.failed ? 'The verification cases are known only after initialization.' : definition.initializationNote}</div>
    ${pressed}
  </div>`;
}

function renderCaseRow(outcome, index) {
  const selected = index === state.selectedCase;
  const outdated = outcome.outdated && isFinished(outcome);
  const tags = (outdated ? '<span class="outdated-tag">Outdated</span>' : '')
    + (outcome.shared ? '<span class="shared-tag">Shared</span>' : '')
    + (outcome.differs ? `<span class="differs-tag" title="The shared verdict was ${outcome.differs}">${icon('alert', 'icon-tiny')}Differs, shared: ${outcome.differs}</span>` : '');
  const time = outcome.time ? `<span class="case-time">${outcome.time}</span>` : '';
  const validate = outcome.trace
    ? `<button class="case-action" data-action="validate-case" data-value="${index}" title="${outcome.validation ? 'Validate again' : 'Validate'}">${icon('shield-check')}</button>`
    : '';
  const message = outcome.message ? `<div class="case-message">${outcome.message}</div>` : '';
  return `
    <div class="case-row status-${outcome.status} ${selected ? 'is-selected' : ''} ${outdated ? 'is-outdated' : ''} ${outcome.shared ? 'is-shared' : ''}" data-action="select-case" data-value="${index}" title="Go to the definition of ${caseNameOf(index)}">
      ${verdictMark(outcome.status)}
      <span class="case-name">${caseNameOf(index)}</span>
      <span class="case-outcome">${tags}${validationMark(outcome.validation)}<span class="verdict-word">${outcome.word}</span>${time}</span>
      <span class="case-actions"><button class="case-action" data-action="run-case" data-value="${index}" title="Run this case">${icon('play')}</button>${validate}</span>
      ${message}
    </div>`;
}

function renderResults() {
  if (state.collapsed) {
    const running = state.verify === 'cancel';
    return `
    <aside class="rail">
      <button class="button button-icon" data-action="collapse" title="Show the verification cases">${icon('panel-expand')}</button>
      <button class="button ${running ? 'button-cancel' : 'button-primary'} rail-verify" data-action="${running ? 'cancel' : 'verify-all'}" title="${running ? 'Cancel' : 'Verify all'}">${icon(running ? 'stop' : 'play')}</button>
      <span class="rail-divider"></span>
      ${state.initialization ? verdictMark('running') : renderCounts(state.cases, state.filter, true)}
    </aside>`;
  }
  let notices = '';
  if (state.errors) {
    notices += `<div class="results-notice is-problem">${icon('alert')}<span>Not run: the model has ${state.errors.length} problems.</span><button class="button-link" data-action="goto-problem">Show them</button></div>`;
  }
  if (state.shared) {
    notices += `<div class="results-notice is-shared">${icon('link')}<span>${sharedNotice[state.shared]}</span></div>`;
  }
  if (state.filter) {
    const shown = state.cases.filter((outcome) => countGroupOf(outcome.status) === state.filter).length;
    const label = countGroups.find(([group]) => group === state.filter)[2].toLowerCase();
    notices += `<div class="results-notice is-filter"><span>Showing ${shown} of ${state.cases.length}: ${label}</span><button class="button-link" data-action="filter" data-value="${state.filter}">Show all</button></div>`;
  }
  const rows = state.cases.map((outcome, index) => [outcome, index])
    .filter(([outcome]) => !state.filter || countGroupOf(outcome.status) === state.filter)
    .map(([outcome, index]) => renderCaseRow(outcome, index)).join('');
  const header = state.initialization
    ? '<span class="results-initializing-label">Initializing</span>'
    : renderCounts(state.cases, state.filter);
  return `
  <aside class="results">
    <div class="results-header">
      <button class="button button-icon" data-action="collapse" title="Collapse the panel">${icon('panel-collapse')}</button>
      ${header}
      <span class="results-spacer"></span>
      ${renderVerifyButton()}
    </div>
    ${renderRunSettings()}
    ${notices}
    ${state.initialization ? renderInitialization() : `<div class="case-list">${rows}</div>`}
    ${state.initialization ? '' : renderTrace()}
  </aside>`;
}

function renderTrace() {
  const outcome = selectedOutcome();
  const scenario = scenarioOf(outcome);
  if (!scenario) {
    let hint;
    if (outcome && outcome.status === 'passed') hint = 'Passed, and nothing to show: no state of the model fails the case.';
    else if (outcome && outcome.status === 'failed') hint = 'No trace came back for this case.';
    else if (state.cases.some(isFinished)) hint = 'Select a case to see its trace, where it has one.';
    else hint = 'Press Verify all. Each case is answered Passed or Failed, and where a run shows why, its trace appears here in the language of the model.';
    return `<section class="trace"><div class="empty-hint">${hint}</div></section>`;
  }
  const outdatedBanner = outcome.outdated ? `<div class="outdated-banner">${icon('edit', 'icon-small')}The model changed since this trace. Press Verify all to check it again.</div>` : '';
  const body = renderTraceBody({
    language: state.language,
    scenario,
    outcome,
    view: state.traceView,
    selectedItem: state.selectedItem,
    withoutCalls: state.withoutCalls,
    layout: { column: 168, rowHeight: 30, gutter: 34 },
  });
  const isCalls = body.startsWith('<div class="calls-scroll"');
  const textDocument = state.language === 'oxsts' ? 'oxsts-trace' : 'trace-text';
  return `
  <section class="trace status-${outcome.status} ${outcome.outdated ? 'is-outdated' : ''} ${isCalls ? 'is-calls' : ''}">
    <div class="trace-head">
      <div class="trace-title"><span class="section-label">Trace</span><span class="case-name">${caseNameOf(state.selectedCase)}</span><span class="results-spacer"></span>
        <button class="button-link" data-action="open-document" data-value="${textDocument}">Open as text</button>
        ${state.language === 'oxsts' ? '' : '<button class="button-link" data-action="open-document" data-value="oxsts-trace">Show in OXSTS</button>'}
      </div>
      ${renderRunDetails(outcome)}
      ${renderTraceViews(state.language, state.traceView)}
    </div>
    <div class="trace-scroll" id="trace-scroll">${outdatedBanner}${body}</div>
  </section>`;
}

function renderStatus() {
  const status = state.status;
  const statusIcon = status.kind === 'busy' ? verdictMark('running') : status.kind === 'warning' || status.kind === 'problem' ? icon('alert') : status.kind === 'done' ? icon('check') : '';
  const definition = languages[state.language];
  const flavour = state.language === 'oxsts' && state.flavour !== 'plain' ? `, ${flavours.find(([id]) => id === state.flavour)[1].toLowerCase()}` : '';
  const position = state.activeTab !== 'model' ? 'Read-only' : state.caret ? 'Ln 63, Col 93' : 'Ln 1, Col 1';
  return `
  <footer class="statusbar">
    <span class="status-message is-${status.kind}">${statusIcon}<span>${status.html || escapeHtml(status.text)}</span></span>
    <span class="status-end">
      <span>${position}</span>
      <span class="status-language" data-language="${state.language}">${languageMark(state.language)}${definition.name}${flavour}</span>
    </span>
  </footer>`;
}

function renderOverlay() {
  const definition = languages[state.language];
  if (state.overlay === 'examples') {
    const items = definition.examples.map(([label, detail, count]) => `
      <button class="menu-item ${label === state.exampleLabel ? 'is-current' : ''}">
        <span>${label === state.exampleLabel ? icon('check', 'icon-small check') : ''}</span>
        <span class="menu-item-label">${label}</span>
        <span class="menu-item-meta">${count} cases</span>
        <span class="menu-item-detail">${detail}</span>
      </button>`).join('');
    return `<div class="menu examples-menu" id="examples-menu" data-language="${state.language}">
      <div class="language-intro">${languageMark(state.language)}<span class="language-intro-name">${definition.name} examples</span><span class="language-intro-detail">${definition.detail}</span></div>
      <div class="menu-divider"></div>
      ${items}
      <div class="menu-divider"></div>
      <button class="menu-item"><span>${icon('file', 'icon-small')}</span><span class="menu-item-label">Blank model</span><span></span></button>
    </div>`;
  }
  if (state.overlay === 'flavour') {
    const items = flavours.map(([id, label, detail]) => `
      <button class="menu-item ${id === state.flavour ? 'is-current' : ''}" data-action="choose-flavour" data-value="${id}">
        <span class="radio ${id === state.flavour ? 'is-checked' : ''}"></span>
        <span class="menu-item-label">${label}</span>
        <span class="menu-item-detail">${detail}</span>
      </button>`).join('');
    return `<div class="menu flavour-menu" id="flavour-menu"><div class="menu-heading">OXSTS flavour</div>${items}</div>`;
  }
  if (state.overlay === 'save') {
    return `<div class="menu save-menu" id="save-menu">
      <button class="menu-item"><span>${icon('save', 'icon-small')}</span><span class="menu-item-label">Save</span><span class="kbd">Ctrl S</span></button>
      <button class="menu-item"><span>${icon('save-as', 'icon-small')}</span><span class="menu-item-label">Save as...</span><span class="kbd">Ctrl Shift S</span></button>
      <button class="menu-item"><span>${icon('download', 'icon-small')}</span><span class="menu-item-label">Download</span><span></span></button>
      <div class="menu-divider"></div>
      <div class="menu-item-detail" style="padding: 4px 8px 6px">Save writes back to <span class="mono">${state.documentName}</span> on your disk.</div>
    </div>`;
  }
  if (state.overlay === 'share') {
    return `<div class="menu share-popover" id="share-popover">
      <div class="share-copied">${icon('check')}<span class="popover-title">Link copied</span></div>
      <div class="field"><span class="mono">live.semantifyr.org/#sysml/level-crossing.sysml?v=1&amp;z=eNrtWktv2zgQvu-vEHjPV9IaoIOnFp</span><button class="button button-icon" title="Copy">${icon('copy', 'icon-small')}</button></div>
      <div class="share-options">
        <div class="share-option"><span class="radio is-checked"></span><span>With results</span><span class="share-option-detail">Carries the model, the selected case and each verdict. The receiver sees your verdicts while the run reproduces them.</span></div>
        <div class="share-option"><span class="radio"></span><span>Model only</span><span class="share-option-detail">A shorter link; the receiver presses Verify all.</span></div>
      </div>
      <div class="share-note">Everything is in the link itself; nothing is stored on the server.</div>
    </div>`;
  }
  if (state.overlay === 'portfolio') {
    const kind = state.portfolioKind;
    const title = kind === 'verify' ? 'Verification portfolio' : 'Validation portfolio';
    return `<div class="menu portfolio-popover" id="portfolio-popover">
      <div class="menu-heading portfolio-title">${title}</div>
      ${renderPortfolioList(kind, state.portfolio[kind])}
    </div>`;
  }
  if (state.overlay === 'documents') {
    const hasTrace = state.selectedCase !== null && scenarioOf(selectedOutcome());
    const items = documentIds[state.language].map((id) => {
      const needsTrace = id.includes('trace');
      const disabled = needsTrace && !hasTrace;
      const label = state.language === 'oxsts' && id === 'oxsts-trace' ? 'Trace of the selected case' : documentMenuLabels[id];
      return `<button class="menu-item ${disabled ? 'is-disabled' : ''}" data-action="open-document" data-value="${id}">${icon('lock', 'icon-small')}<span class="menu-item-label">${label}</span><span class="menu-item-detail">${disabled ? 'select a case with a trace' : readOnlyDocument(id).label}</span></button>`;
    }).join('');
    return `<div class="menu documents-menu" id="documents-menu"><div class="menu-heading">Open read-only</div>${items}</div>`;
  }
  if (state.overlay === 'connection') {
    return `<div class="menu connection-popover" id="connection-popover">
      <div class="connection-row">${cloud('connected')}<span class="popover-title">Connected</span></div>
      <div class="connection-detail">To live.semantifyr.org. The SysML v2 language server is ready and the verifiers are idle. Your model is sent only to be verified, and is not stored.</div>
      <button class="button button-outline button-small" data-action="disconnect">Disconnect</button>
    </div>`;
  }
  if (state.overlay === 'about') {
    return `<div class="scrim" data-action="close"></div>
    <div class="dialog" role="dialog">
      <div class="dialog-header">
        <span class="brand">${logo()}<span class="dialog-product">Live</span></span>
        <button class="button button-icon" data-action="close">${icon('close')}</button>
      </div>
      <div class="dialog-body">
        <p class="dialog-lead">Live checks a model against its verification cases with the Semantifyr toolchain: SysML v2, Gamma and OXSTS models are compiled to transition systems and handed to model checkers, and every result comes back in the model's own terms.</p>
        <div class="dialog-section"><span class="section-label">Research prototype</span><p>Semantifyr is developed as a PhD project at the Budapest University of Technology and Economics. Live is a research prototype and comes with no guarantee of correctness or availability.</p></div>
        <div class="dialog-section"><span class="section-label">Privacy</span><p>Nothing is stored. Your model is sent to the server only to be verified and is dropped when the run ends; the browser keeps neither your edits nor your results, and a reload starts afresh. A shared link carries the model and its results in the link itself.</p></div>
        <div class="dialog-links">
          <button class="button button-outline button-small">${icon('globe', 'icon-tiny')}semantifyr.org</button>
          <button class="button button-outline button-small">${icon('book', 'icon-tiny')}Documentation</button>
          <button class="button button-outline button-small">${icon('code', 'icon-tiny')}Source code</button>
        </div>
      </div>
      <div class="dialog-footer"><span>Version 2026.11.10</span><span>EPL-2.0</span></div>
    </div>`;
  }
  if (state.overlay === 'open-dialog') {
    const files = [
      ['level-crossing-fixed.sysml', '6 KB', 'Yesterday', 'is-selected'],
      ['door-access.sysml', '9 KB', '3 Nov', ''],
      ['crossroads.gamma', '12 KB', '28 Oct', ''],
      ['traffic-light.oxsts', '2 KB', '21 Oct', ''],
      ['notes.pdf', '230 KB', '20 Oct', 'is-disabled'],
    ].map(([name, size, date, kind]) => `<div class="file-dialog-file ${kind}"><span>${name}</span><span>${size}</span><span>${date}</span></div>`).join('');
    return `<div class="scrim" data-action="close"></div>
    <div class="file-dialog" role="dialog">
      <div class="file-dialog-title">Open File</div>
      <div class="file-dialog-path"><span>Home</span><span>models</span></div>
      <div class="file-dialog-body">
        <div class="file-dialog-places"><div>Recent</div><div>Home</div><div class="is-current">models</div><div>Documents</div><div>Downloads</div></div>
        <div class="file-dialog-files">${files}</div>
      </div>
      <div class="file-dialog-footer"><span class="file-dialog-filter">Models (.sysml, .gamma, .oxsts)</span><button data-action="close">Cancel</button><button class="is-default" data-action="open-file">Open</button></div>
    </div>
    <div class="file-dialog-note">The browser's own dialog; this is a stand-in.</div>`;
  }
  return '';
}

function render() {
  document.documentElement.dataset.theme = theme;
  useLongNames = state.longNames;
  const app = document.getElementById('app');
  app.dataset.language = state.language;
  app.innerHTML = renderToolbar() + renderOrientation() + `<main class="workspace ${state.collapsed ? 'is-collapsed' : ''}">${renderEditor()}${renderResults()}</main>` + renderStatus() + `<div class="overlay-layer" id="overlay-layer">${renderOverlay()}</div>`;
  positionOverlays();
  scrollEditor();
  renderMockControls();
}

function positionOverlays() {
  const anchors = {
    'examples-menu': ['[data-action="examples"]', 'left'],
    'flavour-menu': ['[data-action="flavour"]', 'left'],
    'connection-popover': ['[data-action="connection"]', 'left'],
    'save-menu': ['[data-action="save-menu"]', 'right'],
    'share-popover': ['[data-action="share"]', 'right'],
    'portfolio-popover': [`[data-action="portfolio"][data-value="${state.portfolioKind}"]`, 'left'],
    'documents-menu': ['[data-action="documents"]', 'right'],
  };
  Object.entries(anchors).forEach(([overlayId, [selector, side]]) => {
    const overlay = document.getElementById(overlayId);
    const anchor = document.querySelector(`.app ${selector}`);
    if (!overlay || !anchor) return;
    const box = anchor.getBoundingClientRect();
    overlay.style.top = `${box.bottom + 6}px`;
    overlay.style.left = side === 'left'
      ? `${Math.min(box.left, window.innerWidth - overlay.offsetWidth - 12)}px`
      : `${Math.min(box.right - overlay.offsetWidth, window.innerWidth - overlay.offsetWidth - 12)}px`;
  });
}

function scrollEditor() {
  const scroller = document.getElementById('editor-scroll');
  const target = scroller.querySelector('.is-selected, .has-caret, .squiggle, .code-line.is-hovered, .code-line.is-current');
  if (!target) return;
  const line = target.closest('.code-line');
  const offset = line.classList.contains('is-current') && !line.classList.contains('has-caret') ? 40 : scroller.clientHeight * 0.35;
  scroller.scrollTop = line.offsetTop - offset;
}

function renderMockControls() {
  const container = document.getElementById('mock-controls');
  const collapsed = container.dataset.collapsed === 'true';
  const themeButton = (value, label) => `<button class="${theme === value ? 'is-current' : ''}" data-mock="theme" data-value="${value}">${label}</button>`;
  container.innerHTML = `
    <div class="mock-controls-header" data-mock="toggle"><strong>MOCK</strong><span>${collapsed ? 'States' : 'Review controls'}</span><span>${collapsed ? '+' : '-'}</span></div>
    <div class="mock-body">
      <div class="mock-theme">${themeButton('light', 'Light')}${themeButton('dark-a', 'Dark A')}</div>
      ${stateDefinitions.map(([group, entries]) => `<div class="mock-group"><div class="mock-group-title">${group}</div>${entries.map(([name, label]) => `<button class="mock-state ${name === currentName ? 'is-current' : ''}" data-mock="state" data-value="${name}">${label}</button>`).join('')}</div>`).join('')}
    </div>`;
}

function go(name) {
  currentName = name;
  state = buildState(name);
  history.replaceState(null, '', `#${name}/${theme}`);
  render();
}

function update(changes) {
  state = { ...state, ...changes };
  render();
}

let runTimer = null;

const finalOutcomes = [outcomes.failed, outcomes.passedWithTrace];

function startRun() {
  clearTimeout(runTimer);
  if (state.initialization) {
    update({ verify: 'cancel', orientation: false, initialization: { ...state.initialization, pressed: true } });
    runTimer = setTimeout(() => {
      go('running');
      runTimer = setTimeout(() => go('results'), 1800);
    }, 2200);
    return;
  }
  go('running');
  runTimer = setTimeout(() => go('results'), 1800);
}

function runCase(index) {
  clearTimeout(runTimer);
  update({
    cases: state.cases.map((outcome, position) => (position === index ? outcomes.running : outcome)),
    verify: 'cancel',
    orientation: false,
    status: { html: `Verifying <span class="case-ref">${caseNameOf(index)}</span>`, kind: 'busy' },
  });
  runTimer = setTimeout(() => {
    const finished = state.autoValidate && finalOutcomes[index].trace
      ? { ...finalOutcomes[index], validation: { state: 'validating', portfolio: state.portfolio.validate } }
      : withValidation(finalOutcomes[index], null);
    update({
      cases: state.cases.map((outcome, position) => (position === index ? finished : outcome)),
      verify: 'verify',
      selectedCase: index,
      status: { text: `Done in ${finished.time}: ${finished.word.toLowerCase()}`, kind: 'done' },
    });
    if (finished.validation) validateCase(index, 900);
  }, 1500);
}

function validateCase(index, delay = 1100) {
  update({ cases: state.cases.map((entry, position) => (position === index ? { ...entry, validation: { state: 'validating', portfolio: state.portfolio.validate } } : entry)) });
  setTimeout(() => {
    update({ cases: state.cases.map((entry, position) => (position === index ? { ...entry, validation: validatedBy('Theta', '0.6 s') } : entry)) });
  }, delay);
}

function cancelRun() {
  clearTimeout(runTimer);
  if (state.initialization) return go('initializing');
  update({
    cases: state.cases.map((outcome) => (['running', 'queued'].includes(outcome.status) ? outcomes.cancelled : outcome)),
    verify: 'verify',
    status: { text: 'Verification cancelled', kind: 'done' },
  });
}

function loadFile(name, text) {
  const extension = name.split('.').pop();
  const language = languages[extension] ? extension : 'sysml';
  update({ ...baseState(), language, source: text, documentName: name, exampleLabel: null, orientation: false, status: { text: `Opened ${name}`, kind: 'idle' } });
}

document.addEventListener('click', (event) => {
  const mock = event.target.closest('[data-mock]');
  if (mock) {
    const kind = mock.dataset.mock;
    if (kind === 'toggle') {
      const container = document.getElementById('mock-controls');
      container.dataset.collapsed = container.dataset.collapsed === 'true' ? 'false' : 'true';
      renderMockControls();
    } else if (kind === 'theme') {
      theme = mock.dataset.value;
      go(currentName);
    } else {
      clearTimeout(runTimer);
      go(mock.dataset.value);
    }
    return;
  }
  const target = event.target.closest('[data-action]');
  const action = target ? target.dataset.action : null;
  const value = target ? target.dataset.value : null;
  const toggleOverlay = (name) => update({ overlay: state.overlay === name ? null : name });
  const selectCase = (index) => update({ selectedCase: index, selectedItem: null, activeTab: 'model', glyphHover: null });
  switch (action) {
    case 'verify-all': startRun(); break;
    case 'cancel': cancelRun(); break;
    case 'run-case': event.stopPropagation(); runCase(Number(value)); break;
    case 'validate-case': event.stopPropagation(); validateCase(Number(value)); break;
    case 'auto-validate': update({ autoValidate: !state.autoValidate }); break;
    case 'glyph': {
      const outcome = state.cases[Number(value)];
      if (outcome.status === 'not-run' || outcome.outdated) runCase(Number(value));
      else selectCase(Number(value));
      break;
    }
    case 'select-case': selectCase(Number(value)); break;
    case 'trace-view': update({ traceView: value, selectedItem: null }); break;
    case 'select-item': update({ selectedItem: value, activeTab: 'model' }); break;
    case 'filter': update({ filter: state.filter === value ? null : value }); break;
    case 'collapse': update({ collapsed: !state.collapsed }); break;
    case 'tab': update({ activeTab: value }); break;
    case 'close-tab': {
      event.stopPropagation();
      const tabs = state.tabs.filter((id) => id !== value);
      update({ tabs, activeTab: state.activeTab === value ? 'model' : state.activeTab });
      break;
    }
    case 'open-document': if (value) update({ tabs: state.tabs.includes(value) ? state.tabs : [...state.tabs, value], activeTab: value, overlay: null }); break;
    case 'documents': toggleOverlay('documents'); break;
    case 'language': clearTimeout(runTimer); go(value === 'sysml' ? 'first' : value); break;
    case 'flavour': toggleOverlay('flavour'); break;
    case 'choose-flavour': update({ flavour: value, overlay: null }); break;
    case 'examples': toggleOverlay('examples'); break;
    case 'save-menu': toggleOverlay('save'); break;
    case 'save': update({ unsaved: false, overlay: null, status: { text: `Saved to ${state.documentName}`, kind: 'done' } }); break;
    case 'share': toggleOverlay('share'); break;
    case 'portfolio': update({ overlay: state.overlay === 'portfolio' && state.portfolioKind === value ? null : 'portfolio', portfolioKind: value }); break;
    case 'choose-portfolio': {
      const [kind, name] = value.split(':');
      update({ portfolio: { ...state.portfolio, [kind]: name }, overlay: null });
      break;
    }
    case 'about': toggleOverlay('about'); break;
    case 'connection': toggleOverlay('connection'); break;
    case 'disconnect': go('disconnected'); break;
    case 'connect': go('results'); break;
    case 'reload': go('initializing'); break;
    case 'open': toggleOverlay('open-dialog'); break;
    case 'open-file': update({ ...fixedState(), cases: [outcomes.notRun, outcomes.notRun], selectedCase: null, overlay: null, status: { text: 'Opened level-crossing-fixed.sysml', kind: 'idle' } }); break;
    case 'dismiss': dismissedOrientations.add(orientationKey()); update({ orientation: false }); break;
    case 'theme': theme = theme === 'light' ? 'dark-a' : 'light'; go(currentName); break;
    case 'close': update({ overlay: null }); break;
    case 'goto-problem': {
      const squiggle = document.querySelector('.squiggle');
      if (squiggle) squiggle.scrollIntoView({ block: 'center' });
      break;
    }
    default:
      if (state.overlay && !event.target.closest('.menu, .dialog, .file-dialog')) update({ overlay: null });
  }
});

document.addEventListener('keydown', (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key === 's') {
    event.preventDefault();
    update({ unsaved: false, overlay: null, status: { text: `Saved to ${state.documentName}`, kind: 'done' } });
  }
});

document.addEventListener('dragover', (event) => {
  event.preventDefault();
  if (state.overlay !== 'drop') update({ overlay: 'drop' });
});

document.addEventListener('dragleave', (event) => {
  if (!event.relatedTarget) update({ overlay: null });
});

document.addEventListener('drop', (event) => {
  event.preventDefault();
  const file = event.dataTransfer.files[0];
  if (!file) return update({ overlay: null });
  file.text().then((text) => loadFile(file.name, text));
});

window.addEventListener('resize', () => render());

const [initialName, initialTheme] = location.hash.slice(1).split('/');
if (['light', 'dark-a'].includes(initialTheme)) theme = initialTheme;
else if (window.matchMedia('(prefers-color-scheme: dark)').matches) theme = 'dark-a';
if (new URLSearchParams(location.search).has('shots')) document.body.classList.add('mock-hidden');
go(stateBuilders[initialName] ? initialName : 'first');
