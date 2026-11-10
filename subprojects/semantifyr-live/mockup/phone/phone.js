const caseDefinitions = [
  { id: 'BarrierDownWhileTrainInCrossing', anchor: 'verification def BarrierDownWhileTrainInCrossing' },
  { id: 'TwoTrainsCanBeInTheCrossing', anchor: 'verification def TwoTrainsCanBeInTheCrossing' },
];

const editedLine = '        transition first TrainPresent accept : TrainApproaching via trackA then TrainPresent;';

const problems = [
  { severity: 'error', find: 'via barrier } then TrainPresent;', mark: '}', message: "missing ';' at '}'" },
  { severity: 'error', find: 'then TrainPresnt;', mark: 'TrainPresnt', message: "Couldn't resolve reference to StateUsage 'TrainPresnt'." },
  { severity: 'warning', find: 'item def Lift;', mark: 'Lift', message: "Item 'Lift' is sent but never accepted." },
];

function sysmlWithProblems() {
  return sources.sysmlNaive
    .replace('send new Lower() via barrier; } then TrainPresent;\n        transition first Idle accept : TrainApproaching via trackB', 'send new Lower() via barrier } then TrainPresent;\n        transition first Idle accept : TrainApproaching via trackB')
    .replace('via trackB do action { send new Lower() via barrier; } then TrainPresent;', 'via trackB do action { send new Lower() via barrier; } then TrainPresnt;');
}

function sysmlEdited() {
  return sources.sysmlNaive.replace('send new Lift() via barrier; } then Idle;\n    }', 'send new Lift() via barrier; } then Idle;\n' + editedLine + '\n    }');
}

const languages = {
  sysml: {
    name: 'SysML v2',
    detail: 'The OMG systems modeling language. Semantifyr verifies the behaviour of its parts.',
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
    detail: 'Gamma statecharts composed into synchronous systems.',
    examples: [
      ['Level crossing', 'Two tracks, a controller and a barrier; the controller forgets a second train.', 2],
      ['Crossroads', 'Two coordinated traffic lights at an intersection.', 35],
      ['Spacecraft', 'Small spacecraft with battery, thermal, and orbit-control subsystems.', 36],
      ['Leader-worker', 'Two statecharts coordinating via channel-bound events.', 8],
    ],
  },
  oxsts: {
    name: 'OXSTS',
    detail: 'Semantifyr\'s own language of extended transition systems, which the other two compile to.',
    examples: [
      ['Level crossing', 'Two tracks, a controller and a barrier; the controller forgets a second train.', 2],
      ['Tutorial: Basics', 'Final state from the "Direct Modeling" tutorial.', 2],
      ['Tutorial: Intermediate', 'Final state from the "Building a Library" tutorial.', 2],
      ['Tutorial: Advanced', 'Final state from the "Advanced Tutorial".', 6],
    ],
  },
};

const flavours = [
  ['plain', 'Plain'],
  ['gamma', 'Gamma library'],
  ['sysml', 'SysML v2 library'],
];

const initializationSteps = [
  ['Connecting', '0.2 s'],
  ['Starting the language server', '0.9 s'],
  ['Loading the standard library', '6 s of about 15 s'],
  ['Finding the verification cases', ''],
];

const doneStatus = { text: 'Done in 3.7 s: 1 failed, 1 passed', kind: 'done' };

function baseState() {
  return {
    pane: 'model',
    source: sources.sysmlNaive,
    orientation: true,
    connection: 'connected',
    cases: [outcomes.notRun, outcomes.notRun],
    open: null,
    view: 'own',
    expanded: null,
    definitions: null,
    addressError: null,
    updatePrompt: false,
    autoValidate: true,
    portfolio: { verify: 'Automatic', validate: 'Automatic' },
    unsaved: false,
    errors: null,
    caret: null,
    editing: false,
    verify: 'verify',
    initializing: false,
    pressed: false,
    overlay: null,
    chooserLanguage: 'sysml',
    status: { text: 'Ready: 2 verification cases', kind: 'idle' },
  };
}

const markOutdated = (cases) => cases.map((outcome) => ({ ...outcome, outdated: true }));

const resultsState = () => ({ pane: 'results', orientation: false, cases: [outcomes.failed, outcomes.passedWithTrace], open: 0, status: doneStatus });
const outdatedState = () => ({ ...resultsState(), cases: markOutdated([outcomes.failed, outcomes.passedWithTrace]), open: 0, source: sysmlEdited(), unsaved: true, caret: editedLine.trim(), status: { text: 'The model changed: 2 results are outdated', kind: 'idle' } });
const problemsState = () => ({ pane: 'results', orientation: false, source: sysmlWithProblems(), errors: problems, unsaved: true, status: { text: 'Verify all did not run: 2 errors', kind: 'problem' } });
const initializingState = () => ({ pane: 'results', connection: 'busy', initializing: true, status: { text: 'Initializing SysML v2: loading the standard library', kind: 'busy' } });

const stateDefinitions = [
  ['First visit', [
    ['first', 'Ready, not run', () => ({})],
    ['initializing', 'Initializing SysML v2', () => initializingState()],
    ['initializing-pressed', 'Pressed while initializing', () => ({ ...initializingState(), orientation: false, verify: 'cancel', pressed: true })],
    ['chooser', 'Language and example', () => ({ overlay: 'chooser' })],
    ['chooser-oxsts', 'OXSTS and its flavours', () => ({ overlay: 'chooser', chooserLanguage: 'oxsts' })],
    ['menu', 'Menu', () => ({ overlay: 'menu' })],
  ]],
  ['Reading and editing', [
    ['model-selected', 'Tap selects, no keyboard', () => ({ orientation: false, caret: 'state TrainPresent;' })],
    ['edit', 'Edit mode with the keyboard', () => ({ orientation: false, editing: true, unsaved: true, caret: 'state TrainPresent;' })],
  ]],
  ['A run', [
    ['running', 'Running', () => ({ orientation: false, pane: 'results', verify: 'cancel', cases: [outcomes.running, outcomes.queued], status: { html: 'Verifying <span class="case-ref">BarrierDownWhileTrainInCrossing</span>', kind: 'busy' } })],
    ['cancelled', 'Cancelled', () => ({ orientation: false, pane: 'results', cases: [outcomes.cancelled, outcomes.notRun], status: { text: 'Verification cancelled', kind: 'done' } })],
  ]],
  ['Results and traces', [
    ['results', 'Results', resultsState],
    ['trace', 'Trace: SysML trace text first', () => ({ ...resultsState(), pane: 'trace', open: 0 })],
    ['trace-oxsts', 'Trace: OXSTS steps', () => ({ ...resultsState(), pane: 'trace', open: 0, view: 'oxsts' })],
    ['trace-expanded', 'Trace: OXSTS steps, one step expanded', () => ({ ...resultsState(), pane: 'trace', open: 0, view: 'oxsts', expanded: 1 })],
    ['trace-witness', 'Trace: OXSTS witness as text', () => ({ ...resultsState(), pane: 'trace', open: 0, view: 'witness' })],
    ['trace-calls', 'Trace: sequence diagram of the calls', () => ({ ...resultsState(), pane: 'trace', open: 0, view: 'calls' })],
    ['validating', 'Validating', () => ({ ...resultsState(), cases: [{ ...outcomes.failed, validation: { state: 'validating', portfolio: 'Automatic' } }, outcomes.passedWithTrace] })],
    ['validation-failed', 'Validation failed', () => ({ ...resultsState(), cases: [outcomes.failed, { ...outcomes.passedWithTrace, validation: { state: 'validation-failed', time: '0.9 s', portfolio: 'Automatic', backend: 'Theta' } }] })],
    ['inconclusive-error', 'Inconclusive and error', () => ({ ...resultsState(), cases: [outcomes.timedOut, outcomes.error], status: { text: 'Done: 1 inconclusive, 1 error', kind: 'done' } })],
    ['share-opened', 'Share link opened, one differs', () => ({ ...resultsState(), shared: true, cases: [outcomes.failed, { ...outcomes.timedOut, differs: 'Passed' }], status: { text: 'Done in 62 s: 1 failed, 1 inconclusive', kind: 'done' } })],
  ]],
  ['Edits and trouble', [
    ['outdated', 'After an edit', outdatedState],
    ['outdated-model', 'After an edit, model', () => ({ ...outdatedState(), pane: 'model' })],
    ['failure-problems', 'The model has problems', problemsState],
    ['failure-problems-model', 'Problems, model', () => ({ ...problemsState(), pane: 'model' })],
    ['connection-unreachable', 'Server not answering', () => ({ ...initializingState(), connection: 'unreachable', status: { text: 'The server is not answering. Retrying in 5 s. The editor works.', kind: 'warning' } })],
    ['connection-refused', 'Session refused, backing off', () => ({ ...initializingState(), connection: 'refused', status: { text: 'The server refused the session: at capacity. Retrying in 16 s.', kind: 'warning' } })],
    ['connection-terminated', 'Session terminated', () => ({ ...resultsState(), connection: 'terminated', status: { text: 'The session was terminated. Not reconnecting until you ask.', kind: 'warning' } })],
    ['cases-changed', 'An edit added one case and removed one', () => ({ ...resultsState(), open: null, unsaved: true, definitions: [{ id: 'TwoTrainsCanBeInTheCrossing', anchor: 'verification def TwoTrainsCanBeInTheCrossing' }, { id: 'ControllerNeverStuck', anchor: 'verification def ControllerNeverStuck' }], cases: [{ ...outcomes.passedWithTrace, outdated: true }, outcomes.notRun], status: { text: 'The model changed: 1 case added, 1 removed', kind: 'idle' } })],
    ['address-unknown-example', 'Address names an unknown example', () => ({ addressError: 'No example "level-crosing" in SysML v2. Opened Level crossing in its place.' })],
    ['update-available', 'Reload to update', () => ({ ...resultsState(), updatePrompt: true })],
    ['problems-sheet', 'The problems list', () => ({ ...problemsState(), overlay: 'problems' })],
  ]],
];

const stateBuilders = Object.fromEntries(stateDefinitions.flatMap(([, entries]) => entries.map(([name, , build]) => [name, build])));

let currentName = 'first';
let theme = 'light';
function effectiveTheme() {
  if (theme !== 'system') return theme;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark-a' : 'light';
}
let state = buildState('first');

function buildState(name) {
  return { ...baseState(), ...stateBuilders[name]() };
}

function definitions() {
  return state.definitions || caseDefinitions;
}

function sourceLines() {
  return state.source.replace(/\n+$/, '').split('\n');
}

function scenarioOf(index) {
  const outcome = state.cases[index];
  return outcome && outcome.trace ? scenarios[outcome.trace] : null;
}

function renderCode() {
  const lines = sourceLines();
  const caseLines = state.initializing ? new Map() : new Map(definitions().map((definition, index) => [findLine(lines, [definition.anchor]), index]));
  const errorLines = new Map((state.errors || []).map((error) => [findLine(lines, [error.find]), error]));
  return lines.map((line, index) => {
    const indent = line.match(/^ */)[0].length;
    let html = highlight(line.slice(indent), 'sysml');
    const classes = ['code-line'];
    if (state.caret && line.trim() === state.caret) classes.push('is-current', state.editing ? 'has-caret' : 'is-tapped');
    if (state.cases.some((outcome) => outcome.outdated) && line.trim() === editedLine.trim()) classes.push('is-edited');
    const error = errorLines.get(index);
    if (error) {
      const target = error.mark === '}' ? '<span class="syntax-punctuation">}</span>' : escapeHtml(error.mark);
      const at = html.lastIndexOf(target);
      html = html.slice(0, at) + `<span class="squiggle is-${error.severity}">${target}</span>` + html.slice(at + target.length) + `<span class="inline-diagnostic">${escapeHtml(error.message)}</span>`;
    }
    const caseIndex = caseLines.get(index);
    const mark = caseIndex === undefined ? '' : verdictMark(state.cases[caseIndex].status);
    return `<div class="${classes.join(' ')}" data-line="${index}"><span class="line-glyph ${caseIndex !== undefined && state.cases[caseIndex].outdated ? 'is-outdated' : ''}" ${caseIndex === undefined ? '' : `data-action="glyph" data-value="${caseIndex}"`}>${mark}</span><span class="line-number">${index + 1}</span><span class="line-text" style="--indent:${indent}">${html || ' '}</span></div>`;
  }).join('');
}

function renderTop() {
  return `
  <header class="phone-top">
    <span class="phone-brand">${logo()}${connectionIndicator(state.connection)}</span>
    <button class="model-chooser" data-action="chooser" data-language="sysml">
      ${languageMark('sysml')}
      <span class="model-chooser-text"><span class="model-chooser-language">SysML v2</span><span class="model-chooser-example">Level crossing</span></span>
      ${icon('chevron-down', 'icon-small')}
    </button>
    <button class="phone-icon-button" data-action="menu" title="Menu">${icon('more')}</button>
  </header>`;
}

function renderModel() {
  const orientation = state.orientation ? `
    <div class="phone-orientation">
      <span class="orientation-mark"></span>
      <span><strong>Level crossing.</strong> Two tracks, a controller and a barrier; the controller forgets a second train. It has 2 verification cases. Press <strong>Verify all</strong> to check them.</span>
      <button class="phone-icon-button" data-action="dismiss" title="Dismiss">${icon('close', 'icon-small')}</button>
    </div>` : '';
  const bar = state.editing
    ? `<div class="model-bar is-editing">${icon('edit', 'icon-small')}<span class="model-bar-name">Editing level-crossing.sysml</span><button class="button button-outline model-bar-button" data-action="done">${icon('check', 'icon-small')}Done</button></div>`
    : `<div class="model-bar">${languageMark('sysml')}<span class="model-bar-name">level-crossing.sysml${state.unsaved ? '<span class="unsaved-mark"></span>' : ''}</span><span class="model-bar-hint">Read-only</span><button class="button button-outline model-bar-button" data-action="edit">${icon('edit', 'icon-small')}Edit</button></div>`;
  return `<section class="pane pane-model ${state.editing ? 'is-editing' : ''}" data-language="sysml">${orientation}${bar}<div class="pane-scroll" id="pane-scroll"><div class="code">${renderCode()}</div></div></section>`;
}

function renderResults() {
  if (state.initializing) {
    const steps = initializationSteps.map(([label, time], index) => {
      const mark = index < 2 ? icon('check') : index === 2 ? verdictMark('running') : verdictMark('not-run');
      return `<li class="phone-init-step ${index < 2 ? 'is-done' : index === 2 ? 'is-current' : ''}">${mark}<span>${label}</span><span class="phone-init-time">${index <= 2 ? time : ''}</span></li>`;
    }).join('');
    return `
    <section class="pane pane-results">
      <div class="pane-scroll" id="pane-scroll">
        <div class="phone-init" data-language="sysml">
          <div class="phone-init-title">${languageMark('sysml')}Initializing SysML v2</div>
          <ul class="phone-init-steps">${steps}</ul>
          <div class="progress-indeterminate"></div>
          <p class="phone-init-note">The standard library is the slow part; it loads once per visit. The verification cases are listed when it has finished.</p>
          ${state.pressed ? `<div class="phone-init-pressed">${icon('clock')}<span>Verify all is pressed. Every case runs as soon as initialization has finished.</span></div>` : ''}
        </div>
      </div>
    </section>`;
  }
  const sharedNotice = state.shared ? `<div class="phone-notice is-shared">${icon('link', 'icon-small')}<span>Opened from a shared link. 1 of 2 results came out differently from the shared one.</span></div>` : '';
  const rows = state.cases.map((outcome, index) => {
    const scenario = scenarioOf(index);
    const tags = (outcome.outdated ? '<span class="outdated-tag">Outdated</span>' : '')
      + (outcome.differs ? `<span class="differs-tag">${icon('alert', 'icon-tiny')}Differs, shared: ${outcome.differs}</span>` : '');
    return `
      <div class="phone-case status-${outcome.status} ${outcome.outdated ? 'is-outdated' : ''} ${state.open === index ? 'is-selected' : ''}" ${scenario ? `data-action="open-trace" data-value="${index}"` : ''}>
        ${verdictMark(outcome.status)}
        <div class="phone-case-main">
          <div class="case-name">${definitions()[index].id}</div>
          <div class="phone-case-outcome">${validationMark(outcome.validation)}<span class="verdict-word">${outcome.word}</span>${outcome.time ? `<span class="case-time">${outcome.time}</span>` : ''}${tags}</div>
          ${outcome.message ? `<div class="case-message">${outcome.message}</div>` : ''}
        </div>
        <span class="phone-case-side">${['running', 'queued'].includes(outcome.status) ? `<button class="phone-case-run" data-action="cancel-case" data-value="${index}" title="Cancel this case">${icon('stop')}</button>` : `<button class="phone-case-run" data-action="run-case" data-value="${index}" title="Run this case">${icon('play')}</button>`}${scenario ? icon('chevron-right') : ''}</span>
      </div>`;
  }).join('');
  return `
  <section class="pane pane-results">
    <div class="pane-scroll" id="pane-scroll">
      <div class="pane-header">${renderCounts(state.cases, null)}</div>
      ${sharedNotice}
      ${rows}
    </div>
  </section>`;
}

function renderTracePane() {
  const scenario = scenarioOf(state.open);
  if (!scenario) return '<section class="pane pane-trace"><p class="pane-hint">Open a case with a trace to see it here.</p></section>';
  const outcome = state.cases[state.open];
  const body = renderTraceBody({ language: 'sysml', scenario, scenarioId: outcome.trace, outcome, view: state.view, expanded: state.expanded, withoutCalls: false, layout: { column: 150, rowHeight: 30, gutter: 28 }, caseName: definitions()[state.open].id, openable: false });
  const isCalls = body.startsWith('<div class="calls-scroll"');
  return `
  <section class="pane pane-trace ${outcome.outdated ? 'is-outdated' : ''} ${isCalls ? 'is-calls' : ''}">
    <div class="trace-head status-${outcome.status}">
      <div class="trace-head-title">${verdictMark(outcome.status)}<span class="case-name">${definitions()[state.open].id}</span></div>
      <div class="trace-head-meta"><span class="verdict-word">${outcome.word}</span><span>${outcome.time}</span>${outcome.outdated ? '<span class="outdated-tag">Outdated</span>' : ''}</div>
      ${renderRunDetails(outcome)}
      ${renderTraceViews('sysml', state.view, false)}
    </div>
    <div class="trace-body" id="pane-scroll">${body}</div>
  </section>`;
}

function renderBottom() {
  const status = state.status;
  const statusIcon = status.kind === 'busy' ? verdictMark('running') : status.kind === 'warning' || status.kind === 'problem' ? icon('alert') : status.kind === 'done' ? icon('check') : '';
  const errorTotal = (state.errors || []).filter((entry) => entry.severity === 'error').length;
  const warningTotal = (state.errors || []).length - errorTotal;
  const hasTrace = state.cases.some((outcome) => outcome.trace);
  const failed = state.cases.some((outcome) => outcome.status === 'failed');
  const tab = (pane, iconName, label, extra = '') => `<button class="phone-tab" data-action="pane" data-value="${pane}" aria-pressed="${state.pane === pane}" ${pane === 'trace' && !hasTrace ? 'disabled' : ''}><span class="phone-tab-icon">${icon(iconName)}${extra}</span><span>${label}</span></button>`;
  const verifyButton = state.verify === 'cancel'
    ? `<button class="button button-cancel phone-verify" data-action="cancel">${icon('stop', 'icon-small')}Cancel</button>`
    : `<button class="button button-primary phone-verify" data-action="verify-all">${icon('play', 'icon-small')}Verify all</button>`;
  return `
  <footer class="phone-bottom">
    ${state.initializing ? '<div class="progress-indeterminate" data-language="sysml"></div>' : ''}
    <div class="phone-status is-${status.kind}">${statusIcon}<span>${status.html || escapeHtml(status.text)}</span><button class="phone-problems ${state.errors ? 'has-problems' : ''}" data-action="problems">${icon('error', 'icon-small')}${errorTotal}${icon('alert', 'icon-small')}${warningTotal}</button></div>
    <nav class="phone-bar">
      ${tab('model', 'code', 'Model')}
      ${tab('results', 'list', 'Results', failed ? `<span class="tab-dot status-failed ${state.outdated ? 'is-outdated' : ''}"></span>` : '')}
      ${tab('trace', 'sequence', 'Trace')}
      ${verifyButton}
    </nav>
  </footer>`;
}

function renderKeyboard() {
  const rows = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'].map((letters) => `<div class="keyboard-row">${letters.split('').map((letter) => `<span class="key">${letter}</span>`).join('')}</div>`).join('');
  return `<div class="keyboard" aria-label="On-screen keyboard (stand-in)">${rows}<div class="keyboard-row"><span class="key key-wide">123</span><span class="key key-space">space</span><span class="key key-wide">return</span></div></div>`;
}

function renderOverlay() {
  if (state.overlay === 'chooser') {
    const language = state.chooserLanguage;
    const definition = languages[language];
    const options = Object.entries(languages).map(([id, entry]) => `<button class="segmented-option" data-language="${id}" aria-pressed="${id === language}" data-action="chooser-language" data-value="${id}">${languageMark(id)}${entry.name}</button>`).join('');
    const flavourOptions = language === 'oxsts'
      ? `<div class="chooser-flavours"><span class="chooser-flavours-label">Flavour</span><div class="segmented segmented-plain">${flavours.map(([id, label]) => `<button class="segmented-option" aria-pressed="${id === 'plain'}">${label}</button>`).join('')}</div></div>`
      : '';
    const current = language === 'sysml' ? 'Level crossing' : null;
    const items = definition.examples.map(([label, detail, count]) => `
      <button class="chooser-item ${label === current ? 'is-current' : ''}" data-action="choose-example">
        <span class="chooser-check">${label === current ? icon('check', 'icon-small') : ''}</span>
        <span class="chooser-label">${label}</span>
        <span class="chooser-count">${count} cases</span>
        <span class="chooser-detail">${detail}</span>
      </button>`).join('');
    return `
    <div class="sheet-full" data-language="${language}">
      <div class="sheet-header"><span class="sheet-title">Choose a model</span><button class="phone-icon-button" data-action="close" title="Close">${icon('close')}</button></div>
      <div class="segmented chooser-languages">${options}</div>
      ${flavourOptions}
      <p class="chooser-intro">${definition.detail}</p>
      <div class="chooser-list">
        <div class="section-label">${definition.name} examples</div>
        ${items}
        <div class="chooser-divider"></div>
        <button class="chooser-item" data-action="choose-example"><span class="chooser-check">${icon('file', 'icon-small')}</span><span class="chooser-label">Blank model</span></button>
      </div>
    </div>`;
  }
  if (state.overlay === 'problems') {
    const lines = sourceLines();
    const entries = (state.errors || []).map((entry, index) => `<button class="sheet-item" data-action="reveal-problem" data-value="${index}">${icon(entry.severity === 'error' ? 'error' : 'alert')}<span>${escapeHtml(entry.message)}</span><span class="sheet-item-detail">Ln ${findLine(lines, [entry.find]) + 1}</span></button>`).join('');
    return `<div class="scrim" data-action="close"></div><div class="sheet-bottom"><div class="sheet-grabber"></div><div class="sheet-title">Problems in the model</div>${entries || '<div class="sheet-item"><span>No problems.</span></div>'}</div>`;
  }
  if (state.overlay === 'menu') {
    const item = (iconName, label, detail = '') => `<button class="sheet-item" data-action="close">${icon(iconName)}<span>${label}</span><span class="sheet-item-detail">${detail}</span></button>`;
    const dark = effectiveTheme() !== 'light';
    return `
    <div class="scrim" data-action="close"></div>
    <div class="sheet-bottom">
      <div class="sheet-grabber"></div>
      ${item('open', 'Open a file')}
      ${item('link', 'Share', 'Model and results')}
      ${item('sliders', 'Verification', state.portfolio.verify)}
      ${item('shield-check', 'Validation', state.portfolio.validate)}
      <div class="sheet-item sheet-switch">${icon('shield-check')}<span>Validate automatically</span><button class="switch-row" role="switch" aria-checked="${state.autoValidate}" data-action="auto-validate"><span class="switch"></span></button></div>
      ${item('info', 'About')}
      ${item('terminal', 'Developer panel')}
      <div class="sheet-item sheet-theme">${icon(dark ? 'moon' : 'sun')}<span>Theme</span>
        <div class="segmented segmented-plain">${[['system', 'System'], ['light', 'Light'], ['dark-a', 'Dark']].map(([value, label]) => `<button class="segmented-option" aria-pressed="${theme === value}" data-action="theme" data-value="${value}">${label}</button>`).join('')}</div>
      </div>
    </div>`;
  }
  return '';
}

function render() {
  document.documentElement.dataset.theme = effectiveTheme();
  document.title = 'Level crossing - SysML v2 - Semantifyr Live';
  const panes = { model: renderModel, results: renderResults, trace: renderTracePane };
  const phone = document.getElementById('phone');
  phone.classList.toggle('is-editing', state.editing);
  const address = state.addressError ? `<div class="phone-orientation is-error">${icon('alert', 'icon-small')}<span>${escapeHtml(state.addressError)}</span><button class="phone-icon-button" data-action="dismiss-address" title="Dismiss">${icon('close', 'icon-small')}</button></div>` : '';
  const toast = state.updatePrompt ? `<div class="toast" role="status"><span>A new version of Live is available.</span><button class="button button-primary button-small" data-action="reload-page">Reload</button><button class="button button-quiet button-small" data-action="later">Later</button></div>` : '';
  phone.innerHTML = renderTop() + address + panes[state.pane]() + (state.editing ? renderKeyboard() : renderBottom()) + renderOverlay() + toast;
  scrollPane();
  renderMockControls();
}

function scrollPane() {
  const scroller = document.getElementById('pane-scroll');
  if (!scroller) return;
  const target = scroller.querySelector('.state-box.is-selected, .step-item.is-selected, .code-line.is-selected, .code-line.is-current, .squiggle');
  if (!target) return;
  const box = target.getBoundingClientRect();
  scroller.scrollTop += box.top - scroller.getBoundingClientRect().top - scroller.clientHeight * 0.4;
}

function renderMockControls() {
  const container = document.getElementById('mock-controls');
  const collapsed = container.dataset.collapsed === 'true';
  const themeButton = (value, label) => `<button class="${theme === value ? 'is-current' : ''}" data-mock="theme" data-value="${value}">${label}</button>`;
  container.innerHTML = `
    <div class="mock-controls-header" data-mock="toggle"><strong>MOCK</strong><span>Phone states</span><span>${collapsed ? '+' : '-'}</span></div>
    <div class="mock-body">
      <div class="mock-pair">${themeButton('light', 'Light')}${themeButton('dark-a', 'Dark A')}${themeButton('system', 'System')}</div>
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

function startRun() {
  clearTimeout(runTimer);
  const pane = state.pane;
  if (state.initializing) {
    update({ verify: 'cancel', pressed: true, orientation: false });
    runTimer = setTimeout(() => {
      go('running');
      runTimer = setTimeout(() => go('results'), 1800);
    }, 2200);
    return;
  }
  go('running');
  update({ pane: pane === 'trace' ? 'results' : pane });
  runTimer = setTimeout(() => go('results'), 1800);
}

document.addEventListener('click', (event) => {
  const mock = event.target.closest('[data-mock]');
  if (mock) {
    const kind = mock.dataset.mock;
    if (kind === 'toggle') {
      const container = document.getElementById('mock-controls');
      container.dataset.collapsed = container.dataset.collapsed === 'true' ? 'false' : 'true';
      renderMockControls();
      return;
    }
    clearTimeout(runTimer);
    if (kind === 'theme') {
      theme = mock.dataset.value;
    }
    go(kind === 'state' ? mock.dataset.value : currentName);
    return;
  }
  const lineTarget = event.target.closest('.pane-model .code-line');
  if (lineTarget && !state.editing) {
    update({ caret: lineTarget.textContent.replace(/^\d+/, '').trim(), orientation: false });
    return;
  }
  const target = event.target.closest('[data-action]');
  if (!target) return;
  const value = target.dataset.value;
  switch (target.dataset.action) {
    case 'verify-all': startRun(); break;
    case 'cancel': clearTimeout(runTimer); go(state.initializing ? 'initializing' : 'cancelled'); break;
    case 'pane': update({ pane: value }); break;
    case 'open-trace': update({ pane: 'trace', open: Number(value), view: 'own' }); break;
    case 'trace-view': update({ view: value, expanded: null }); break;
    case 'run-case': event.stopPropagation(); break;
    case 'cancel-case': event.stopPropagation(); break;
    case 'expand-step': update({ expanded: state.expanded === Number(value) ? null : Number(value) }); break;
    case 'problems': update({ overlay: 'problems' }); break;
    case 'reveal-problem': update({ overlay: null, pane: 'model' }); break;
    case 'dismiss-address': update({ addressError: null }); break;
    case 'reload-page': go('first'); break;
    case 'later': update({ updatePrompt: false }); break;
    case 'glyph': update({ pane: 'results' }); break;
    case 'auto-validate': update({ autoValidate: !state.autoValidate }); break;
    case 'goto-problem': update({ pane: 'model' }); break;
    case 'dismiss': update({ orientation: false }); break;
    case 'edit': update({ editing: true, orientation: false }); break;
    case 'done': update({ editing: false }); break;
    case 'chooser': update({ overlay: 'chooser', chooserLanguage: 'sysml' }); break;
    case 'chooser-language': update({ chooserLanguage: value }); break;
    case 'choose-example': go('first'); break;
    case 'menu': update({ overlay: 'menu' }); break;
    case 'close': update({ overlay: null }); break;
    case 'theme': theme = value; render(); break;
  }
});

window.addEventListener('resize', () => render());

const [initialName, initialTheme] = location.hash.slice(1).split('/');
if (['light', 'dark-a', 'system'].includes(initialTheme)) theme = initialTheme;
else theme = 'system';
if (new URLSearchParams(location.search).has('shots')) document.body.classList.add('mock-hidden');
if (window.innerWidth < 700) document.getElementById('mock-controls').dataset.collapsed = 'true';
go(stateBuilders[initialName] ? initialName : 'first');

window.addEventListener('hashchange', () => {
  const [name, hashTheme] = location.hash.slice(1).split('/');
  if (['light', 'dark-a', 'system'].includes(hashTheme)) theme = hashTheme;
  if (stateBuilders[name] && name !== currentName) go(name);
});
