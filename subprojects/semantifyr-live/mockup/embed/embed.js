const caseDefinitions = [
  { id: 'BarrierDownWhileTrainInCrossing', anchor: 'verification def BarrierDownWhileTrainInCrossing' },
  { id: 'TwoTrainsCanBeInTheCrossing', anchor: 'verification def TwoTrainsCanBeInTheCrossing' },
];

const editedLine = '        transition first TrainPresent accept : TrainApproaching via trackA then TrainPresent;';

const problems = [
  { find: 'via barrier } then TrainPresent;', mark: '}', message: "missing ';' at '}'" },
  { find: 'then TrainPresnt;', mark: 'TrainPresnt', message: "Couldn't resolve reference to StateUsage 'TrainPresnt'." },
];

function sysmlWithProblems() {
  return sources.sysmlNaive
    .replace('send new Lower() via barrier; } then TrainPresent;\n        transition first Idle accept : TrainApproaching via trackB', 'send new Lower() via barrier } then TrainPresent;\n        transition first Idle accept : TrainApproaching via trackB')
    .replace('via trackB do action { send new Lower() via barrier; } then TrainPresent;', 'via trackB do action { send new Lower() via barrier; } then TrainPresnt;');
}

function sysmlEdited() {
  return sources.sysmlNaive.replace('send new Lift() via barrier; } then Idle;\n    }', 'send new Lift() via barrier; } then Idle;\n' + editedLine + '\n    }');
}

const markOutdated = (cases) => cases.map((outcome) => ({ ...outcome, outdated: true }));

const doneStatus = { text: 'Done in 3.7 s: 1 failed, 1 passed', kind: 'done' };

function baseState() {
  return {
    source: sources.sysmlNaive,
    cases: [outcomes.notRun, outcomes.notRun],
    open: null,
    view: 'own',
    kind: 'verify',
    excerpt: false,
    errors: null,
    caret: null,
    verify: 'verify',
    initializing: false,
    status: { text: 'Ready: 2 verification cases', kind: 'idle' },
  };
}

const resultsState = () => ({ cases: [outcomes.failed, outcomes.passedWithTrace], status: doneStatus });

const stateDefinitions = [
  ['Before a run', [
    ['first', 'Ready, not run', () => ({})],
    ['initializing', 'Initializing SysML v2', () => ({ initializing: true, status: { text: 'Initializing SysML v2: loading the standard library', kind: 'busy' } })],
    ['initializing-pressed', 'Pressed while initializing', () => ({ initializing: true, verify: 'cancel', status: { text: 'Verify all is pressed: every case runs once initialization has finished', kind: 'busy' } })],
  ]],
  ['A run', [
    ['running', 'Running', () => ({ verify: 'cancel', cases: [outcomes.running, outcomes.queued], status: { html: 'Verifying <span class="case-ref">BarrierDownWhileTrainInCrossing</span>, 1 of 2', kind: 'busy' } })],
    ['cancelled', 'Cancelled', () => ({ cases: [outcomes.cancelled, outcomes.notRun], status: { text: 'Verification cancelled', kind: 'done' } })],
  ]],
  ['Results', [
    ['results', 'Failed and passed, validated', resultsState],
    ['trace', 'Trace: SysML trace text first', () => ({ ...resultsState(), open: 0 })],
    ['trace-oxsts', 'Trace: OXSTS steps', () => ({ ...resultsState(), open: 0, view: 'oxsts' })],
    ['trace-calls', 'Trace: sequence diagram of the calls', () => ({ ...resultsState(), open: 0, view: 'calls' })],
    ['validating', 'Validating', () => ({ cases: [{ ...outcomes.failed, validation: { state: 'validating', portfolio: 'Automatic' } }, outcomes.passedWithTrace], status: doneStatus })],
    ['failure-timeout', 'Inconclusive: timed out', () => ({ cases: [outcomes.timedOut, outcomes.error], status: { text: 'Done: 1 inconclusive, 1 error', kind: 'done' } })],
  ]],
  ['Edit-only view', [
    ['edit-only', 'Editor alone, for excerpts', () => ({ kind: 'edit' })],
  ]],
  ['Edits and trouble', [
    ['outdated', 'After an edit', () => ({ cases: markOutdated([outcomes.failed, outcomes.passedWithTrace]), open: 0, source: sysmlEdited(), caret: editedLine.trim(), status: { text: 'The model changed: 2 results are outdated', kind: 'idle' } })],
    ['failure-problems', 'The model has problems', () => ({ source: sysmlWithProblems(), errors: problems, status: { html: 'Not run: <span class="link" data-action="goto-problem">2 problems</span> in the model', kind: 'problem' } })],
    ['connection-lost', 'Connection lost', () => ({ ...resultsState(), cases: [outcomes.failed, outcomes.notRun], status: { html: 'Connection lost. Reconnecting in 4 s. <span class="link" data-action="reconnect">Reconnect now</span>', kind: 'warning' } })],
  ]],
];

const stateBuilders = Object.fromEntries(stateDefinitions.flatMap(([, entries]) => entries.map(([name, , build]) => [name, build])));

let currentName = 'first';
let theme = 'light';
let page = 'alone';
let state = buildState('first');

function buildState(name) {
  return { ...baseState(), ...stateBuilders[name]() };
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
  const caseLines = state.initializing || state.kind === 'edit' ? new Map() : new Map(caseDefinitions.map((definition, index) => [findLine(lines, [definition.anchor]), index]));
  const errorLines = new Map((state.errors || []).map((error) => [findLine(lines, [error.find]), error]));
  return lines.map((line, index) => {
    const indent = line.match(/^ */)[0].length;
    let html = highlight(line.slice(indent), 'sysml');
    const classes = ['code-line'];
    if (state.caret && line.trim() === state.caret) classes.push('has-caret', 'is-current', 'is-edited');
    const error = errorLines.get(index);
    if (error) {
      const target = error.mark === '}' ? '<span class="syntax-punctuation">}</span>' : escapeHtml(error.mark);
      const at = html.lastIndexOf(target);
      html = html.slice(0, at) + `<span class="squiggle">${target}</span>` + html.slice(at + target.length) + `<span class="inline-diagnostic">${escapeHtml(error.message)}</span>`;
    }
    const caseIndex = caseLines.get(index);
    const mark = caseIndex === undefined ? '' : `${verdictMark(state.cases[caseIndex].status)}${renderGlyphTip(caseDefinitions[caseIndex].id, state.cases[caseIndex])}`;
    const glyph = state.kind === 'edit' ? '' : `<span class="line-glyph ${caseIndex === undefined ? '' : 'has-mark'} ${caseIndex !== undefined && state.cases[caseIndex].outdated ? 'is-outdated' : ''}">${mark}</span>`;
    return `<div class="${classes.join(' ')}" data-line="${index}">${glyph}<span class="line-number">${index + 1}</span><span class="line-text" style="--indent:${indent}">${html || ' '}</span></div>`;
  }).join('');
}

function renderCase(outcome, index) {
  const scenario = scenarioOf(index);
  const open = state.open === index;
  const outdated = outcome.outdated;
  const end = (outdated ? '<span class="outdated-tag">Outdated</span>' : '')
    + (outcome.time ? `<span class="case-time">${outcome.time}</span>` : '')
    + (scenario ? `<span class="embed-trace-hint">Trace</span>${icon('chevron-down', 'icon-small chevron')}` : '');
  const row = `
    <div class="embed-case status-${outcome.status} ${open ? 'is-open' : ''} ${outdated ? 'is-outdated' : ''} ${scenario ? 'has-trace' : ''}" ${scenario ? `data-action="toggle-case" data-value="${index}"` : ''}>
      ${verdictMark(outcome.status)}
      <span class="case-name">${caseDefinitions[index].id}</span>
      <span class="embed-case-verdict">${validationMark(outcome.validation)}<span class="verdict-word">${outcome.word}</span></span>
      <span class="embed-case-end">${end}</span>
      ${outcome.message ? `<span class="case-message">${outcome.message}</span>` : ''}
    </div>`;
  return row + (open ? renderTrace(scenario, outcome) : '');
}

function renderTrace(scenario, outcome) {
  const body = renderTraceBody({ language: 'sysml', scenario, outcome, view: state.view, selectedItem: null, withoutCalls: false, layout: { column: 168, rowHeight: 30, gutter: 34 } });
  const isCalls = body.startsWith('<div class="calls-scroll"');
  return `
    <div class="embed-trace ${outcome.outdated ? 'is-outdated' : ''}">
      <div class="embed-trace-bar">${renderRunDetails(outcome)}${renderTraceViews('sysml', state.view)}</div>
      <div class="embed-trace-body ${isCalls ? 'is-calls' : ''}">${body}</div>
    </div>`;
}

function renderEditOnly() {
  const excerpt = ['part def Track {', '    port sensors : ~Sensors;', '    port controller : TrackReports;', '', '    exhibit state Behaviour {', '        transition first start then Free;', '', '        state Free;', '        state Approaching;', '', '        transition first Free accept : ApproachSignal via sensors then Approaching;', '    }', '}'];
  state = { ...state, source: excerpt.join('\n') };
  return `
  <div class="embed is-edit-only" data-language="sysml">
    <div class="embed-editor"><div class="embed-editor-scroll" id="editor-scroll"><div class="code">${renderCode()}</div></div></div>
  </div>`;
}

function renderEmbed() {
  if (state.kind === 'edit') return renderEditOnly();
  const status = state.status;
  const statusIcon = status.kind === 'busy' ? verdictMark('running') : status.kind === 'warning' || status.kind === 'problem' ? icon('alert') : status.kind === 'done' ? icon('check') : '';
  const verifyButton = state.verify === 'cancel'
    ? `<button class="button button-cancel" data-action="cancel">${icon('stop', 'icon-small')}Cancel</button>`
    : `<button class="button button-primary" data-action="verify-all">${icon('play', 'icon-small')}Verify all</button>`;
  const cases = state.initializing
    ? '<div class="embed-initializing">The verification cases are listed once the SysML v2 standard library has loaded.</div>'
    : state.cases.map(renderCase).join('');
  return `
  <div class="embed" data-language="sysml">
    <div class="embed-editor"><div class="embed-editor-scroll" id="editor-scroll"><div class="code">${renderCode()}</div></div></div>
    <div class="embed-strip">
      ${state.initializing ? '<div class="progress-indeterminate"></div>' : ''}
      <div class="strip-bar">
        ${verifyButton}
        <span class="strip-status is-${status.kind}">${statusIcon}<span>${status.html || escapeHtml(status.text)}</span></span>
        <button class="button button-quiet open-live" title="Opens live.semantifyr.org with this model and its results">Open in Live${icon('external', 'icon-small')}</button>
      </div>
      <div class="embed-cases">${cases}</div>
    </div>
  </div>`;
}

function renderPage(embed) {
  if (state.kind === 'edit') {
    return `
  <article class="doc-page">
    <div class="doc-crumbs">Language reference / SysML v2 / Parts</div>
    <h1>Part definitions</h1>
    <p>A part definition names a kind of part. This one gives a track two ports and a small state machine; the editor below is for reading and trying the syntax, and nothing in it is verified.</p>
    ${embed}
    <p>A part usage of this definition inherits its ports and its states.</p>
  </article>`;
  }
  return `
  <article class="doc-page">
    <div class="doc-crumbs">Tutorials / SysML v2 / Verifying a model</div>
    <h1>Checking the level crossing</h1>
    <p>The model below describes a level crossing: two tracks report approaching and leaving trains to a controller, which lowers and lifts a barrier. Its first verification case asks that the barrier is down whenever a train is in the crossing; the second asks whether two trains can be in the crossing at once.</p>
    <p>Press <strong>Verify all</strong> and open the failed case to see the run that fails it.</p>
    ${embed}
    <p>The trace shows the flaw. The controller keeps a single flag for both tracks, so when a second train approaches while the first is still in the crossing, its report is not accepted, and the first train leaving lifts the barrier. The next section gives the controller a counter.</p>
  </article>`;
}

function render() {
  document.documentElement.dataset.theme = theme;
  document.body.dataset.view = page;
  useLongNames = false;
  const embed = renderEmbed();
  document.getElementById('stage').innerHTML = page === 'page' ? renderPage(embed) : `<div class="alone">${embed}</div>`;
  scrollEditor();
  renderMockControls();
}

function scrollEditor() {
  const scroller = document.getElementById('editor-scroll');
  const target = scroller.querySelector('.is-selected, .has-caret, .squiggle');
  if (target) scroller.scrollTop = target.closest('.code-line').offsetTop - scroller.clientHeight * 0.4;
}

function renderMockControls() {
  const container = document.getElementById('mock-controls');
  const collapsed = container.dataset.collapsed === 'true';
  const pair = (kind, current, options) => `<div class="mock-pair">${options.map(([value, label]) => `<button class="${value === current ? 'is-current' : ''}" data-mock="${kind}" data-value="${value}">${label}</button>`).join('')}</div>`;
  container.innerHTML = `
    <div class="mock-controls-header" data-mock="toggle"><strong>MOCK</strong><span>Embed states</span><span>${collapsed ? '+' : '-'}</span></div>
    <div class="mock-body">
      ${pair('page', page, [['alone', 'Alone'], ['page', 'In a page']])}
      ${pair('theme', theme, [['light', 'Light'], ['dark-a', 'Dark A']])}
      ${stateDefinitions.map(([group, entries]) => `<div class="mock-group"><div class="mock-group-title">${group}</div>${entries.map(([name, label]) => `<button class="mock-state ${name === currentName ? 'is-current' : ''}" data-mock="state" data-value="${name}">${label}</button>`).join('')}</div>`).join('')}
    </div>`;
}

function go(name) {
  currentName = name;
  state = buildState(name);
  history.replaceState(null, '', `#${name}/${theme}/${page}`);
  render();
}

function update(changes) {
  state = { ...state, ...changes };
  render();
}

let runTimer = null;

function startRun() {
  clearTimeout(runTimer);
  go(state.initializing ? 'initializing-pressed' : 'running');
  const next = () => {
    if (currentName === 'initializing-pressed') {
      go('running');
      runTimer = setTimeout(next, 1800);
    } else if (currentName === 'running') {
      go('results');
    }
  };
  runTimer = setTimeout(next, 1800);
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
    if (kind === 'theme') theme = mock.dataset.value;
    if (kind === 'page') page = mock.dataset.value;
    go(kind === 'state' ? mock.dataset.value : currentName);
    return;
  }
  const target = event.target.closest('[data-action]');
  if (!target) return;
  const value = target.dataset.value;
  switch (target.dataset.action) {
    case 'verify-all': startRun(); break;
    case 'cancel': clearTimeout(runTimer); go(state.initializing ? 'initializing' : 'cancelled'); break;
    case 'toggle-case': update({ open: state.open === Number(value) ? null : Number(value), view: 'own' }); break;
    case 'trace-view': update({ view: value }); break;
    case 'goto-problem': scrollEditor(); break;
    case 'reconnect': go('results'); break;
  }
});

window.addEventListener('resize', () => render());

const [initialName, initialTheme, initialPage] = location.hash.slice(1).split('/');
if (['light', 'dark-a'].includes(initialTheme)) theme = initialTheme;
else if (window.matchMedia('(prefers-color-scheme: dark)').matches) theme = 'dark-a';
if (initialPage === 'page') page = 'page';
if (new URLSearchParams(location.search).has('shots')) document.body.classList.add('mock-hidden');
go(stateBuilders[initialName] ? initialName : 'first');
