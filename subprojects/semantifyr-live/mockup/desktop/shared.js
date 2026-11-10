const flamePath = 'M360.262 240.337C369.037 275.925 332.475 311.025 296.4 296.4 266.175 285.675 254.475 250.575 275.437 220.35 322.725 157.462 288.112 58.5 209.625 24.375 245.212 91.65 190.612 153.562 159.412 179.4 128.212 205.237 107.25 232.05 100.912 247.65 69.225 324.675 116.512 398.287 148.687 415.837 134.062 382.687 120.412 319.312 177.937 259.837 177.937 259.837 161.362 323.212 197.437 367.087 233.512 410.962 233.025 443.625 233.025 443.625 289.087 443.625 342.225 409.987 364.65 354.9 381.712 322.237 383.175 270.562 360.262 240.337';

function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function icon(name, extraClass = '') {
  return `<svg class="icon icon-${name} ${extraClass}"><use href="#icon-${name}"/></svg>`;
}

function logo() {
  return `<svg class="logo" viewBox="8 22 2952 566" role="img" aria-label="Semantifyr">
    <path class="logo-flame" transform="translate(1060 9)" d="${flamePath}"/>
    <text x="15.955" y="451">S</text><text x="306.42" y="451">e</text><text x="618.09" y="451">m</text><text x="1456.52" y="451">ntifyr</text>
  </svg>`;
}

const connectionTitles = {
  connected: 'Connected to live.semantifyr.org',
  busy: 'Connected, initializing',
  connecting: 'Connecting to live.semantifyr.org',
  offline: 'Disconnected. Click to connect',
  lost: 'Connection lost, reconnecting',
  unreachable: 'The server is not answering. Retrying',
  refused: 'The server refused the session. Retrying with a backoff',
  terminated: 'The session was terminated. Not reconnecting until you ask',
};

function cloud(connection) {
  const shape = 'M7.1 18.6h10.4a4.15 4.15 0 0 0 .55-8.26A5.7 5.7 0 0 0 7.2 9a4.8 4.8 0 0 0-.1 9.6z';
  const dots = '<circle class="cloud-dot" cx="9" cy="14.4" r="1.05"/><circle class="cloud-dot" cx="12.3" cy="14.4" r="1.05"/><circle class="cloud-dot" cx="15.6" cy="14.4" r="1.05"/>';
  const bodies = {
    connected: `<path class="cloud-body" d="${shape}"/>`,
    busy: `<path class="cloud-body" d="${shape}"/>${dots}`,
    connecting: `<path class="cloud-outline" d="${shape}"/>`,
    offline: `<path class="cloud-outline" d="${shape}"/><path class="cloud-slash" d="M4 4.5l16 15"/>`,
    lost: `<path class="cloud-outline" d="${shape}"/><path class="cloud-slash" d="M4 4.5l16 15"/>`,
    unreachable: `<path class="cloud-outline" d="${shape}"/><path class="cloud-slash" d="M4 4.5l16 15"/>`,
    refused: `<path class="cloud-outline" d="${shape}"/><path class="cloud-slash" d="M4 4.5l16 15"/>`,
    terminated: `<path class="cloud-outline" d="${shape}"/><path class="cloud-slash" d="M4 4.5l16 15"/>`,
  };
  return `<svg class="cloud" viewBox="0 0 24 24">${bodies[connection]}</svg>`;
}

const connectionReasons = {
  unreachable: 'retrying',
  refused: 'refused, retrying',
  terminated: 'terminated',
};

function connectionIndicator(connection, expanded = false) {
  const kind = connection === 'busy' ? 'connecting is-busy' : connection;
  const failure = connection in connectionReasons;
  const reason = failure ? `<span class="connection-reason">${connectionReasons[connection]}</span>` : '';
  return `<button class="connection is-${kind} ${failure ? 'is-failure' : ''}" data-action="connection" aria-expanded="${expanded}" title="${connectionTitles[connection]}">${cloud(connection)}<span>Live</span>${reason}</button>`;
}


function verdictMark(status) {
  const ring = '<circle class="ring" cx="8" cy="8" r="6.75"/>';
  const dashed = '<circle class="ring" cx="8" cy="8" r="6.75" stroke-dasharray="2.1 2.3"/>';
  const disc = '<circle class="disc" cx="8" cy="8" r="7.5"/>';
  const clock = '<path class="glyph" d="M8 4.9V8l2.1 1.4"/>';
  const glyphs = {
    passed: disc + '<path class="glyph on-disc" d="M4.9 8.2l2.15 2.15 4.1-4.45"/>',
    failed: disc + '<path class="glyph on-disc" d="M5.5 5.5l5 5M10.5 5.5l-5 5"/>',
    inconclusive: ring + '<path class="glyph" d="M6.2 6.3a1.8 1.8 0 1 1 2.5 1.65c-.45.2-.7.55-.7 1v.3"/><circle class="glyph-fill" cx="8" cy="11.25" r=".95"/>',
    error: ring + '<path class="glyph" d="M8 4.6v4.1"/><circle class="glyph-fill" cx="8" cy="11.1" r=".95"/>',
    'not-run': dashed,
    queued: dashed + clock,
    running: '<circle class="ring" cx="8" cy="8" r="6.75" opacity="0.22"/><path class="ring spinner" stroke-linecap="round" d="M8 1.25a6.75 6.75 0 0 1 6.75 6.75"/>',
  };
  return `<svg class="verdict-mark status-${status}" viewBox="0 0 16 16">${glyphs[status]}</svg>`;
}

const validationWords = {
  validating: 'validating',
  validated: 'validated',
  'validation-failed': 'validation failed',
};

function validationMark(validation) {
  if (!validation) return '';
  const shield = 'M8 1.7l5 1.8v4c0 3.1-2.1 5.4-5 6.8-2.9-1.4-5-3.7-5-6.8v-4z';
  const glyphs = {
    validating: `<path class="shield spinner-dash" d="${shield}" stroke-dasharray="3 2.4"/>`,
    validated: `<path class="shield" d="${shield}"/><path class="glyph" d="M5.6 8.1l1.7 1.7 3.2-3.5"/>`,
    'validation-failed': `<path class="shield" d="${shield}"/><path class="glyph" d="M8 5.3v3"/><circle class="glyph-fill" cx="8" cy="10.5" r=".8"/>`,
  };
  const word = validationWords[validation.state];
  return `<svg class="validation-mark is-${validation.state}" viewBox="0 0 16 16" role="img" aria-label="${word}"><title>${word}</title>${glyphs[validation.state]}</svg>`;
}

function languageMark(language) {
  const shapes = {
    sysml: '<rect class="fill" x="1.75" y="2.25" width="12.5" height="11.5" rx="2.5"/><path d="M1.75 6.25h12.5"/>',
    gamma: '<path d="M1.8 4.3c1.7-.7 3.3.2 4.3 2.2L8 10.3M14.2 3.8L8 10.3c-1.5 1.8-1.5 4.6 0 4.6s1.5-2.8 0-4.6"/>',
    oxsts: `<path class="fill" transform="translate(.6 .25) scale(.0334)" stroke-width="40" d="${flamePath}"/>`,
  };
  return `<svg class="language-mark" data-language="${language}" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.45" stroke-linecap="round" stroke-linejoin="round">${shapes[language]}</svg>`;
}


const validatedBy = (backend, time) => ({ state: 'validated', time, portfolio: 'Automatic', backend });

const outcomes = {
  notRun: { status: 'not-run', word: 'Not run' },
  queued: { status: 'queued', word: 'Queued' },
  running: { status: 'running', word: 'Running', time: '2.4 s' },
  cancelled: { status: 'not-run', word: 'Not run', message: 'cancelled' },
  failed: { status: 'failed', word: 'Failed', time: '2.9 s', trace: 'failing', run: { portfolio: 'Automatic', backend: 'Theta' }, validation: validatedBy('Spin', '0.6 s') },
  passedWithTrace: { status: 'passed', word: 'Passed', time: '0.8 s', trace: 'passing', run: { portfolio: 'Automatic', backend: 'Spin' }, validation: validatedBy('Theta', '0.4 s') },
  passed: { status: 'passed', word: 'Passed', time: '4.0 s', run: { portfolio: 'Automatic', backend: 'Spin' } },
  passedFixedWithTrace: { status: 'passed', word: 'Passed', time: '1.1 s', trace: 'passingFixed', run: { portfolio: 'Automatic', backend: 'Theta' }, validation: validatedBy('Spin', '0.5 s') },
  timedOut: { status: 'inconclusive', word: 'Inconclusive', time: '60 s', message: 'timed out after 60 s' },
  unsupported: { status: 'inconclusive', word: 'Inconclusive', time: '0.3 s', message: 'not supported: unbounded attribute trains' },
  error: { status: 'error', word: 'Error', time: '3.2 s', message: 'the verifier stopped with an internal error' },
};

function withValidation(outcome, validation) {
  return { ...outcome, validation };
}

const countGroups = [
  ['passed', 'passed', 'Passed'],
  ['failed', 'failed', 'Failed'],
  ['inconclusive', 'inconclusive', 'Inconclusive'],
  ['error', 'error', 'Error'],
  ['running', 'running', 'Running or queued'],
];

function countGroupOf(status) {
  if (status === 'running' || status === 'queued') return 'running';
  if (status === 'not-run') return null;
  return status;
}

function renderCounts(outcomeList, filter, compact = false) {
  const totals = Object.fromEntries(countGroups.map(([group]) => [group, 0]));
  outcomeList.forEach((outcome) => {
    const group = countGroupOf(outcome.status);
    if (group) totals[group]++;
  });
  const chips = countGroups
    .filter(([group]) => group !== 'running' || totals.running > 0)
    .map(([group, status, label]) => `<button class="count status-${status} ${totals[group] ? '' : 'is-zero'}" data-action="filter" data-value="${group}" aria-pressed="${filter === group}" title="${label}: ${totals[group]}">${verdictMark(status)}<span>${totals[group]}</span></button>`).join('');
  return `<span class="counts">${chips}${compact ? '' : `<span class="counts-total">of ${outcomeList.length}</span>`}</span>`;
}

const portfolioChoices = [
  ['Automatic', 'tries every engine at once and takes the first trusted answer'],
  ['Cross-check', 'runs every engine and requires them to agree'],
];

const engines = ['Theta', 'Spin'];

function renderPortfolioList(kind, current) {
  const entry = (name, detail) => `
    <button class="menu-item portfolio-item" data-action="choose-portfolio" data-value="${kind}:${name}">
      <span class="radio ${name === current ? 'is-checked' : ''}"></span>
      <span class="menu-item-label">${name}</span>
      ${detail ? `<span class="menu-item-detail">${detail}</span>` : ''}
    </button>`;
  return portfolioChoices.map(([name, detail]) => entry(name, detail)).join('')
    + '<div class="menu-heading">Single engine</div>'
    + engines.map((name) => entry(name, '')).join('');
}

function renderRunDetails(outcome) {
  const run = outcome.run;
  const verification = run
    ? `<span class="run-line"><span class="run-label">Verification</span>${outcome.time}, ${run.portfolio}, decided by ${run.backend}</span>`
    : '';
  const validation = outcome.validation;
  let validationLine = '<span class="run-line"><span class="run-label">Validation</span>not run</span>';
  if (validation && validation.state === 'validating') validationLine = `<span class="run-line"><span class="run-label">Validation</span>validating, ${validation.portfolio}</span>`;
  else if (validation) validationLine = `<span class="run-line"><span class="run-label">Validation</span>${validationWords[validation.state]}, ${validation.time}, ${validation.portfolio}, decided by ${validation.backend}</span>`;
  return `<div class="run-details">${verification}${validationLine}</div>`;
}

function renderGlyphTip(name, outcome) {
  const time = outcome.time && !['queued', 'not-run'].includes(outcome.status) ? `<span>${outcome.time}</span>` : '';
  return `<span class="glyph-tip"><span class="mono">${name}</span><span>${outcome.word}</span>${time}</span>`;
}

const signalOf = { approach: 'ApproachSignal', near: 'NearSignal', enter: 'EnterSignal', leave: 'LeaveSignal' };

const parts = [
  { id: 'trackA', type: 'Track' },
  { id: 'trackB', type: 'Track' },
  { id: 'controller', type: 'Controller' },
  { id: 'barrier', type: 'Barrier' },
];

const scenarios = {
  failing: {
    trace: 'sysmlTrace',
    traceName: 'BarrierDownWhileTrainInCrossingTrace',
    start: { trackA: 'Free', trackB: 'Free', controller: 'Idle', barrier: 'Up' },
    steps: [
      {
        environment: [['trackB', 'approach']],
        changes: { trackB: 'Approaching' },
        sends: [{ from: 'trackB', to: 'controller', item: 'TrainApproaching', find: ['send new TrainApproaching()', 'part def Track'] }],
      },
      {
        environment: [['trackB', 'near']],
        changes: { trackB: 'Near', controller: 'TrainPresent' },
        sends: [{ from: 'controller', to: 'barrier', item: 'Lower', find: ['accept : TrainApproaching via trackB', 'part def Controller'] }],
      },
      {
        environment: [['trackB', 'enter']],
        changes: { trackB: 'InCrossing', barrier: 'Down' },
        sends: [],
      },
      {
        environment: [['trackA', 'approach'], ['trackB', 'leave']],
        changes: { trackA: 'Approaching', trackB: 'Free' },
        sends: [
          { from: 'trackA', to: 'controller', item: 'TrainApproaching', dropped: true, find: ['send new TrainApproaching()', 'part def Track'] },
          { from: 'trackB', to: 'controller', item: 'TrainLeft', find: ['send new TrainLeft()', 'part def Track'] },
        ],
      },
      {
        environment: [['trackA', 'near']],
        changes: { trackA: 'Near', controller: 'Idle' },
        sends: [{ from: 'controller', to: 'barrier', item: 'Lift', find: ['accept : TrainLeft via trackB', 'part def Controller'] }],
      },
      {
        environment: [['trackA', 'enter']],
        changes: { trackA: 'InCrossing', barrier: 'Up' },
        sends: [],
        endParts: ['trackA', 'barrier'],
      },
    ],
  },
  passing: {
    trace: 'sysmlTraceReachable',
    traceName: 'TwoTrainsCanBeInTheCrossingTrace',
    start: { trackA: 'Free', trackB: 'Free', controller: 'Idle', barrier: 'Up' },
    steps: [
      {
        environment: [['trackA', 'approach'], ['trackB', 'approach']],
        changes: { trackA: 'Approaching', trackB: 'Approaching' },
        sends: [
          { from: 'trackA', to: 'controller', item: 'TrainApproaching', find: ['send new TrainApproaching()', 'part def Track'] },
          { from: 'trackB', to: 'controller', item: 'TrainApproaching', find: ['send new TrainApproaching()', 'part def Track'] },
        ],
      },
      {
        environment: [['trackA', 'near'], ['trackB', 'near']],
        changes: { trackA: 'Near', trackB: 'Near', controller: 'TrainPresent' },
        sends: [{ from: 'controller', to: 'barrier', item: 'Lower', find: ['accept : TrainApproaching via trackA', 'part def Controller'] }],
      },
      {
        environment: [['trackA', 'enter'], ['trackB', 'enter']],
        changes: { trackA: 'InCrossing', trackB: 'InCrossing', barrier: 'Down' },
        sends: [],
        endParts: ['trackA', 'trackB'],
      },
    ],
  },
  passingFixed: {
    trace: 'sysmlTraceReachable',
    traceName: 'TwoTrainsCanBeInTheCrossingTrace',
    start: { trackA: 'Free', trackB: 'Free', controller: 'Watching', barrier: 'Up' },
    startValues: [['controller.trains', '0']],
    steps: [
      {
        environment: [['trackA', 'approach'], ['trackB', 'approach']],
        changes: { trackA: 'Approaching', trackB: 'Approaching' },
        sends: [
          { from: 'trackA', to: 'controller', item: 'TrainApproaching', find: ['send new TrainApproaching()', 'part def Track'] },
          { from: 'trackB', to: 'controller', item: 'TrainApproaching', find: ['send new TrainApproaching()', 'part def Track'] },
        ],
      },
      {
        environment: [['trackA', 'near'], ['trackB', 'near']],
        changes: { trackA: 'Near', trackB: 'Near' },
        values: [['controller.trains', '0', '2', ['assign trains := trains + 1', 'part def Controller']]],
        accepts: [
          ['controller.TrackA', 'TrainApproaching', 'trackA', ['accept : TrainApproaching via trackA if trains == 0', 'part def Controller']],
          ['controller.TrackB', 'TrainApproaching', 'trackB', ['accept : TrainApproaching via trackB if trains > 0', 'part def Controller']],
        ],
        sends: [{ from: 'controller', to: 'barrier', item: 'Lower', find: ['send new Lower() via barrier', 'part def Controller'] }],
      },
      {
        environment: [['trackA', 'enter'], ['trackB', 'enter']],
        changes: { trackA: 'InCrossing', trackB: 'InCrossing', barrier: 'Down' },
        sends: [],
        endParts: ['trackA', 'trackB'],
      },
    ],
  },
};

function statesAt(scenario, step) {
  const result = { ...scenario.start };
  scenario.steps.slice(0, step).forEach((entry) => Object.assign(result, entry.changes));
  return result;
}

function findLine(lines, [text, after]) {
  const start = after ? lines.findIndex((line) => line.includes(after)) : 0;
  return lines.findIndex((line, index) => index >= start && line.includes(text));
}

function partType(id) {
  return parts.find((part) => part.id === id).type;
}

function reference(text) {
  return `<span class="reference">${escapeHtml(text)}</span>`;
}

const lowerFirst = (text) => text[0].toLowerCase() + text.slice(1);

const longPartNames = { trackA: 'northboundApproachTrackSection', trackB: 'southboundApproachTrackSection', controller: 'crossingSupervisionController', barrier: 'roadBarrierAssembly' };
let useLongNames = false;

function partName(id) {
  return useLongNames ? longPartNames[id] : id;
}

const stateEnums = { Track: 'TrackState', Controller: 'ControllerState', Barrier: 'BarrierState' };
const itemFlags = { TrainApproaching: 'trainApproaching', TrainLeft: 'trainLeft', Lower: 'lower', Lift: 'lift' };

function sendPorts(send) {
  const fromPort = send.from === 'controller' ? 'barrier' : 'controller';
  const toPort = send.to === 'controller' ? send.from : 'controller';
  return [fromPort, toPort];
}

function stateVariable(language, part) {
  if (language === 'oxsts') return `root.${partName(part)}.state`;
  return `root.system.${partName(part)}.${language === 'sysml' ? 'Behaviour' : 'Main'}.activeState`;
}

function stateValue(language, part, value) {
  if (language === 'oxsts') return `${stateEnums[partType(part)]}::${value}`;
  return `root.system.${partName(part)}.${language === 'sysml' ? 'Behaviour' : 'Main'}.${value}`;
}

function itemVariables(language, send) {
  const [fromPort, toPort] = sendPorts(send);
  if (language === 'sysml') {
    return [
      [`root.system.${partName(send.from)}.${fromPort}.outgoingItem`, `global_${send.item}`, 'nothing'],
      [`root.system.${partName(send.to)}.${toPort}.incomingItem`, `global_${send.item}`, 'nothing'],
    ];
  }
  if (language === 'gamma') {
    return [[`root.system.${partName(send.to)}.${toPort}.${lowerFirst(send.item)}.isRaised`, 'true', 'false']];
  }
  const owner = ['TrainApproaching', 'TrainLeft'].includes(send.item) ? send.from : 'controller';
  return [[`root.${partName(owner)}.${itemFlags[send.item]}`, 'true', 'false']];
}

function stateMaps(language, scenario) {
  const defaults = {};
  scenario.steps.forEach((step) => step.sends.forEach((send) => itemVariables(language, send).forEach(([variable, , fallback]) => {
    defaults[variable] = fallback;
  })));
  const first = {};
  Object.entries(scenario.start).forEach(([part, value]) => {
    first[stateVariable(language, part)] = stateValue(language, part, value);
  });
  (scenario.startValues || []).forEach(([name, value]) => {
    first[`root.system.${name}.value`] = value;
  });
  const maps = [first];
  scenario.steps.forEach((step, index) => {
    const next = { ...maps[index], ...defaults };
    Object.entries(step.changes).forEach(([part, value]) => {
      next[stateVariable(language, part)] = stateValue(language, part, value);
    });
    (step.values || []).forEach(([name, , to]) => {
      next[`root.system.${name}.value`] = to;
    });
    step.sends.forEach((send) => itemVariables(language, send).forEach(([variable, value]) => {
      next[variable] = value;
    }));
    maps.push(next);
  });
  return { maps, defaults };
}

function transitionNumber(scenario, index, part, to) {
  if (partType(part) === 'Barrier') return to === 'Down' ? 1 : 2;
  if (partType(part) === 'Track') return ['Approaching', 'Near', 'InCrossing', 'Free'].indexOf(to) + 1;
  const wanted = to === 'TrainPresent' ? 'TrainApproaching' : 'TrainLeft';
  const trigger = index > 0 ? scenario.steps[index - 1].sends.find((send) => send.to === 'controller' && send.item === wanted) : null;
  return (to === 'TrainPresent' ? 0 : 2) + (trigger && trigger.from === 'trackB' ? 2 : 1);
}

function call(self, transition, args = [], inner = []) {
  return { self, transition, args, inner };
}

function sysmlCalls(scenario, index) {
  const step = scenario.steps[index];
  const pending = [...step.sends];
  const takeSends = (part) => {
    const mine = pending.filter((send) => send.from === part);
    mine.forEach((send) => pending.splice(pending.indexOf(send), 1));
    return mine.map((send) => call(`root.system.${partName(send.from)}.${sendPorts(send)[0]}`, 'semantifyr::sysml::ports::Port::setOutgoingItem', [['item', `global_${send.item}`]]));
  };
  const fire = 'semantifyr::sysml::states::Transition::fire';
  const calls = [];
  Object.entries(step.changes).forEach(([part, to]) => {
    calls.push(call(`root.system.${partName(part)}.Behaviour.UnnamedTransitionUsage${transitionNumber(scenario, index, part, to)}`, fire, [], takeSends(part)));
  });
  (step.accepts || []).forEach(([region], position) => {
    const [part, name] = region.split('.');
    calls.push(call(`root.system.${partName(part)}.${name}.UnnamedTransitionUsage${position + 1}`, fire, [], position === 0 ? takeSends(part) : []));
  });
  pending.forEach((send) => calls.push(call(`root.system.${partName(send.from)}.${sendPorts(send)[0]}`, 'semantifyr::sysml::ports::Port::setOutgoingItem', [['item', `global_${send.item}`]])));
  calls.push(call('root', 'semantifyr::sysml::verification::Verification::passTime'));
  return calls;
}

function gammaCalls(scenario, index) {
  const step = scenario.steps[index];
  const pending = [...step.sends];
  const raise = (send) => {
    const [, toPort] = sendPorts(send);
    return call(`root.system.${partName(send.to)}.${toPort}`, 'semantifyr::gamma::ports::Port::raiseInputEvent', [['event', `root.system.${partName(send.to)}.${toPort}.interface.${lowerFirst(send.item)}`]]);
  };
  const calls = step.environment.map(([part, event]) => call(`root.system.${partName(part)}.sensors`, 'semantifyr::gamma::ports::Port::raiseInputEvent', [['event', `root.system.trackA.sensors.interface.${event}`]]));
  Object.entries(step.changes).forEach(([part, to]) => {
    const mine = pending.filter((send) => send.from === part);
    mine.forEach((send) => pending.splice(pending.indexOf(send), 1));
    calls.push(call(`root.system.${partName(part)}.Main.transition${transitionNumber(scenario, index, part, to) + 1}`, 'semantifyr::gamma::statecharts::Transition::fire', [], mine.map(raise)));
  });
  return calls;
}

function oxstsCalls(scenario, index) {
  const step = scenario.steps[index];
  const previous = index > 0 ? scenario.steps[index - 1].sends : [];
  const state = statesAt(scenario, index);
  const flag = (item) => String(previous.some((send) => send.item === item));
  const inner = [
    call(`root.${partName('barrier')}`, 'example::levelcrossing::naive::Barrier::step', [['lower', flag('Lower')], ['lift', flag('Lift')]]),
    call(`root.${partName('controller')}`, 'example::levelcrossing::naive::Controller::step', [['trackA', `root.${partName('trackA')}`], ['trackB', `root.${partName('trackB')}`]]),
  ];
  Object.entries(step.changes).filter(([part]) => partType(part) === 'Track').forEach(([part, to]) => {
    inner.push(call(`root.${partName(part)}`, 'example::levelcrossing::naive::Track::move', [['from', `TrackState::${state[part]}`], ['to', `TrackState::${to}`]]));
  });
  return [call('root', 'example::levelcrossing::naive::LevelCrossing::main', [], inner)];
}

const callBuilders = { sysml: sysmlCalls, gamma: gammaCalls, oxsts: oxstsCalls };

function oxstsTrace(language, scenario, withoutCalls = false) {
  const { maps, defaults } = stateMaps(language, scenario);
  const valueOf = (map, variable) => (variable in map ? map[variable] : defaults[variable] || 'nothing');
  const steps = scenario.steps.map((step, index) => {
    const variables = [...new Set([...Object.keys(maps[index]), ...Object.keys(maps[index + 1])])];
    const changes = variables
      .filter((variable) => valueOf(maps[index], variable) !== valueOf(maps[index + 1], variable))
      .map((variable) => ({ variable, previous: valueOf(maps[index], variable), value: valueOf(maps[index + 1], variable) }));
    const everything = [...new Set([...Object.keys(maps[index + 1]), ...Object.keys(defaults)])];
    const variablesAfter = everything.map((variable) => ({ variable, value: valueOf(maps[index + 1], variable), changed: changes.some((change) => change.variable === variable) }));
    return { changes, variables: variablesAfter, calls: withoutCalls ? [] : callBuilders[language](scenario, index) };
  });
  const start = Object.entries(maps[0]).map(([variable, value]) => ({ variable, value }));
  return { start, steps };
}

function shortPath(path, keep = 2) {
  const segments = path.split('.');
  return segments.length <= keep ? path : `…${segments.slice(-keep).join('.')}`;
}

function shortInstance(path, budget = 22) {
  const segments = path.split('.');
  let kept = [segments.pop()];
  while (segments.length && kept.join('.').length + segments[segments.length - 1].length + 1 <= budget) kept = [segments.pop(), ...kept];
  return segments.length ? `…${kept.join('.')}` : kept.join('.');
}

function pathReference(path, keep = 3) {
  return `<span class="reference" title="${escapeHtml(path)}">${escapeHtml(shortPath(path, keep))}</span>`;
}

function valueReference(value, className = 'reference') {
  const short = value.includes('::') ? value : value.split('.').pop();
  return `<span class="${className}" title="${escapeHtml(value)}">${escapeHtml(short)}</span>`;
}

function oxstsItems(step) {
  return step.changes.map((change) => ({
    kind: change.variable.endsWith('activeState') || change.variable.endsWith('.state') ? 'transition' : 'value',
    icon: change.variable.endsWith('activeState') || change.variable.endsWith('.state') ? 'transition' : 'value',
    html: `${pathReference(change.variable)} ${valueReference(change.previous, 'step-from')}<span class="step-arrow">→</span>${valueReference(change.value)}`,
  }));
}

const propertyTexts = {
  sysml: {
    failing: 'mustAlways(not (isStateActive(system.trackA.Behaviour.InCrossing) or isStateActive(system.trackB.Behaviour.InCrossing)) or isStateActive(system.barrier.Behaviour.Down))',
  },
  gamma: {
    failing: 'AG ((!isActive (system.trackA.Main.InCrossing) && !isActive (system.trackB.Main.InCrossing)) || isActive (system.barrier.Main.Down))',
  },
  oxsts: {
    failing: 'AG (!(trackA.state == TrackState::InCrossing || trackB.state == TrackState::InCrossing) || barrier.state == BarrierState::Down)',
  },
};

function renderStepList(startHtml, steps, options) {
  const { status, closing, expanded } = options;
  const rendered = steps.map((step, index) => {
    const isFinal = index === steps.length - 1;
    const rows = oxstsItems(step).map((item) => `<div class="step-item is-${item.kind}">${icon(item.icon)}<span>${item.html}</span></div>`).join('');
    const isOpen = expanded === index;
    const variables = isOpen ? `<div class="step-variables">${step.variables.map((entry) => `<div class="step-variable ${entry.changed ? 'is-changed' : ''}">${pathReference(entry.variable)}<span class="step-equals">=</span>${valueReference(entry.value)}</div>`).join('')}</div>` : '';
    const end = isFinal && closing ? `<div class="step-end">${verdictMark(status)}<span class="verdict-word">Fails</span><code class="property-text">${highlight(closing.text, closing.language)}</code></div>` : '';
    return `<div class="step ${isFinal ? 'is-final status-' + status : ''} ${isOpen ? 'is-open' : ''}">
      <span class="step-number">${index + 1}</span>
      <div class="step-items">${rows}${variables}${end}</div>
      <button class="step-toggle" data-action="expand-step" data-value="${index}" aria-expanded="${isOpen}" title="${isOpen ? 'Collapse' : 'Every variable at this step'}">${icon('chevron-down', 'icon-tiny')}</button>
    </div>`;
  }).join('');
  return `<div class="steps">
    <div class="step"><span class="step-number">0</span><div class="step-start">${startHtml}</div></div>
    ${rendered}
  </div>`;
}

function renderCalls(steps, layout) {
  const { column, rowHeight, gutter } = layout;
  const lifelines = [];
  const collect = (entry) => {
    if (!lifelines.includes(entry.self)) lifelines.push(entry.self);
    entry.inner.forEach(collect);
  };
  steps.forEach((step) => step.calls.forEach(collect));
  if (!lifelines.length) {
    return `<div class="empty-hint calls-empty">${icon('alert', 'icon-small')}<span>No calls were recorded for this trace. A call is recorded only for a transition annotated with <span class="mono">@Trace</span>, and this model annotates none.</span></div>`;
  }
  const x = Object.fromEntries(lifelines.map((id, index) => [id, gutter + column * (index + 0.5)]));
  let width = gutter + column * lifelines.length;
  let backgrounds = '';
  let foreground = '';
  let y = 0;
  const labelOf = (entry) => `${entry.transition.split('::').pop()}(${entry.args.map(([key, value]) => `${key} = ${value}`).join(', ')})`;
  steps.forEach((step, index) => {
    const top = y;
    let cursor = top + 6;
    const nodes = [];
    const place = (entry, parent) => {
      const node = { entry, parent, centerY: cursor + rowHeight / 2, bottom: 0 };
      nodes.push(node);
      cursor += rowHeight;
      entry.inner.forEach((child) => place(child, node));
      node.bottom = cursor - 3;
    };
    step.calls.forEach((entry) => place(entry, null));
    if (!nodes.length) cursor += rowHeight;
    nodes.forEach((node) => {
      const center = x[node.entry.self];
      const label = labelOf(node.entry);
      const labelX = center + 11;
      width = Math.max(width, labelX + label.length * 6.5 + 16);
      foreground += `<g class="call">
        <title>${escapeHtml(`${node.entry.self}\n${node.entry.transition}`)}</title>
        <rect class="activation" x="${center - 4}" y="${node.centerY - 11}" width="8" height="${Math.max(22, node.bottom - node.centerY + 11)}" rx="2"/>
        <text x="${labelX}" y="${node.centerY - 1}">${escapeHtml(label)}</text>
      </g>`;
      if (!node.parent) return;
      const parentCenter = x[node.parent.entry.self];
      const direction = center > parentCenter ? 1 : -1;
      const from = parentCenter + 4 * direction;
      const to = center - 4 * direction;
      foreground += `<g class="call-arrow"><path d="M${from} ${node.centerY + 6} H${to - 7 * direction}"/><path class="arrowhead" d="M${to} ${node.centerY + 6} l${-7 * direction} -3.5 v7z"/></g>`;
    });
    if (!step.calls.length) foreground += `<text class="band-empty" x="${gutter + 10}" y="${top + 6 + rowHeight / 2 + 4}">no calls</text>`;
    cursor += 6;
    backgrounds += `<g class="band">
      <rect class="band-fill" x="0" y="${top}" width="100%" height="${cursor - top}"/>
      <line class="band-rule" x1="0" y1="${cursor}" x2="100%" y2="${cursor}"/>
      <text class="band-number" x="${gutter - 10}" y="${top + 6 + rowHeight / 2 + 4}">${index + 1}</text>
    </g>`;
    y = cursor;
  });
  const lines = lifelines.map((id) => `<line class="lifeline" x1="${x[id]}" y1="0" x2="${x[id]}" y2="${y}"/>`).join('');
  const heads = lifelines.map((id) => `<div class="calls-head" style="left:${x[id]}px" title="${escapeHtml(id)}"><span class="calls-head-name">${escapeHtml(shortInstance(id))}</span></div>`).join('');
  return `<div class="calls-scroll"><div class="calls" style="width:${width}px"><div class="calls-heads">${heads}</div><svg width="${width}" height="${y}" viewBox="0 0 ${width} ${y}">${backgrounds}${lines}${foreground}</svg></div></div>`;
}

const traceViewLabels = {
  sysml: [['own', 'SysML trace'], ['oxsts', 'OXSTS steps'], ['calls', 'Calls'], ['witness', 'OXSTS witness']],
  gamma: [['own', 'Gamma trace'], ['oxsts', 'OXSTS steps'], ['calls', 'Calls'], ['witness', 'OXSTS witness']],
  oxsts: [['own', 'Steps'], ['calls', 'Calls'], ['witness', 'Witness']],
};

function renderTraceViews(language, view, small = true) {
  const buttons = traceViewLabels[language].map(([id, label], index) => `${index === 1 ? '<span class="views-divider"></span>' : ''}<button class="segmented-option" aria-pressed="${view === id}" data-action="trace-view" data-value="${id}">${label}</button>`).join('');
  return `<div class="segmented ${small ? 'segmented-small' : ''} segmented-plain trace-views">${buttons}</div>`;
}

function traceTextOf(language, scenarioId, view, caseName) {
  const scenario = scenarios[scenarioId];
  if (view === 'witness') {
    if (language === 'sysml' && scenarioId === 'failing') return { text: documentSources.sysmlWitnessOxsts, label: `${caseName}.witness.oxsts`, language: 'oxsts', tab: 'oxsts-trace' };
    return { text: witnessSource(language, scenario, caseName), label: `${caseName}.witness.oxsts`, language: 'oxsts', tab: 'oxsts-trace' };
  }
  if (language === 'sysml') return { text: sources[scenario.trace], label: `${caseName}Trace.sysml`, language: 'sysml', tab: 'trace-text', links: true };
  return { text: gammaTraceSource(scenario, caseName), label: `${caseName}.trace.gamma`, language: 'gamma', tab: 'trace-text', links: true };
}

function renderTraceText(text, openable) {
  const open = openable ? `<button class="frame-open" data-action="open-document" data-value="${text.tab}" title="Open in the editor as a read-only tab">${icon('external', 'icon-tiny')}</button>` : '';
  const hint = text.links ? 'Ctrl and click a name' : 'read-only';
  return `<div class="trace-frame"><div class="trace-frame-bar">${icon('lock', 'icon-small')}<span class="mono">${text.label}</span><span>${hint}</span>${open}</div><div class="code trace-text">${renderPlainCode(text.text, text.language, text.links)}</div></div>`;
}

function renderTraceBody(options) {
  const { language, scenario, scenarioId, outcome, view, expanded, withoutCalls, layout, caseName, openable } = options;
  if (view === 'calls') return renderCalls(oxstsTrace(language, scenario, withoutCalls).steps, layout);
  const textual = view === 'witness' || (view === 'own' && language !== 'oxsts');
  if (textual) return renderTraceText(traceTextOf(language, scenarioId, view, caseName), openable);
  const trace = oxstsTrace(language, scenario, withoutCalls);
  const closing = outcome.status === 'failed' ? { text: propertyTexts[language].failing, language } : null;
  const start = `Start${trace.start.map((entry) => `<div class="step-start-line">${pathReference(entry.variable)} <span class="step-equals">=</span> ${valueReference(entry.value)}</div>`).join('')}`;
  return renderStepList(start, trace.steps, { status: outcome.status, closing, expanded });
}

function witnessSource(language, scenario, caseName) {
  const headers = { gamma: `LevelCrossingNaive::${caseName}`, oxsts: `example::levelcrossing::naive::${caseName}`, sysml: `semantifyr::sysml::model::${caseName}` };
  const trace = oxstsTrace(language, scenario);
  const assumes = (changes) => changes.map((change) => `    assume (${change.variable} == ${change.value})`).join('\n');
  const blocks = trace.steps.map((step, index) => `    assume (step == ${index + 1})\n    inline root.main()\n${assumes(step.changes.map((change) => ({ variable: change.variable, value: change.value })))}\n    step := ${index + 2}`);
  return `witness of ${headers[language]}

var step : int := 0

contains root : ${headers[language]}

init {
    assume (step == 0)
    inline root.init()
${assumes(trace.start.map((entry) => ({ variable: entry.variable, value: entry.value })))}
    step := 1
}

tran {
${blocks.join('\n} or {\n')}
}
`;
}

function gammaTraceSource(scenario, caseName) {
  const before = (index) => statesAt(scenario, index);
  const steps = scenario.steps.map((step, index) => {
    const acts = step.environment.map(([part, event]) => `            raise system.${partName(part)}.sensors.${event}`).join('\n');
    const asserts = [
      ...Object.entries(step.changes).map(([part, to]) => `            transition system.${partName(part)}.Main ${before(index)[part]} to ${to}\n            state system.${partName(part)}.Main.${to}`),
      ...step.sends.map((send) => `            raised system.${partName(send.from)}.${sendPorts(send)[0]}.${lowerFirst(send.item)}`),
    ].join('\n');
    return `    step {\n        act {\n${acts}\n        }\n        assert {\n${asserts}\n        }\n    }`;
  });
  return `trace ${caseName}Trace of system {\n${steps.join('\n')}\n}\n`;
}

const keywordSets = {
  sysml: 'private import item def port out in part exhibit state transition first then accept via do action send new bind flow from to verification subject objective verify requirement require constraint not or and attribute default assign if parallel calc return snapshot timeslice assert connection connect succession start abstract datatype specializes library standard package doc',
  gamma: 'package interface out event statechart port requires provides region entry transition to state when raise sync component bind channel verification case property AG EF',
  oxsts: 'package enum class var tran assume choice or inline redefine if then else contains prop return true false bool int AG EF import abstract derived refers opposite container nothing witness init global containment',
};

function highlight(text, language) {
  const keywords = new Set(keywordSets[language].split(' '));
  const pattern = /(\/\/.*$|\/\*.*?\*\/)|("[^"]*")|(@\w+)|([A-Za-z_]\w*)|(\d+(?:\.\d+)?)|(:>>|:>|::|:=|==|!=|\|\||&&|<=|>=|->|[~!=<>+\-*/?])|([{}()\[\];,.:])|(\s+)|(.)/g;
  let html = '';
  let match;
  while ((match = pattern.exec(text)) !== null) {
    const [token, comment, string, annotation, word, number, operator, punctuation] = match;
    const escaped = escapeHtml(token);
    if (comment) html += `<span class="syntax-comment">${escaped}</span>`;
    else if (string) html += `<span class="syntax-string">${escaped}</span>`;
    else if (annotation) html += `<span class="syntax-function">${escaped}</span>`;
    else if (word && keywords.has(word)) html += `<span class="syntax-keyword">${escaped}</span>`;
    else if (word && (text[pattern.lastIndex] === '(' || language === 'gamma' && text.slice(pattern.lastIndex, pattern.lastIndex + 2) === ' (')) html += `<span class="syntax-function">${escaped}</span>`;
    else if (word && /^[A-Z]/.test(word)) html += `<span class="syntax-type">${escaped}</span>`;
    else if (word) html += `<span class="syntax-name">${escaped}</span>`;
    else if (number) html += `<span class="syntax-number">${escaped}</span>`;
    else if (operator) html += `<span class="syntax-operator">${escaped}</span>`;
    else if (punctuation) html += `<span class="syntax-punctuation">${escaped}</span>`;
    else html += escaped;
  }
  return html;
}

function renderPlainCode(source, language, linkReferences = false) {
  return source.replace(/\n+$/, '').split('\n').map((line, index) => {
    const indent = line.match(/^ */)[0].length;
    let html = highlight(line.slice(indent), language);
    if (linkReferences) html = html.replace(/(<span class="syntax-operator">:&gt;&gt;?<\/span> <span class="syntax-type">)(\w+)(<\/span>)/g, '$1<span class="trace-reference" title="Go to definition">$2</span>$3');
    return `<div class="code-line is-plain"><span class="line-number">${index + 1}</span><span></span><span class="line-text" style="--indent:${indent}">${html || ' '}</span></div>`;
  }).join('');
}
