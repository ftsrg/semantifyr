/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { createContext, useContext, useId } from 'react';
import styles from './styles.module.css';

type Lamp = 'Red' | 'Yellow' | 'Green' | 'Walk' | 'Stop' | 'Standby';

const LAMP_CLASS: Readonly<Record<Lamp, string | undefined>> = {
  Red: styles.red,
  Yellow: styles.yellow,
  Green: styles.green,
  Stop: styles.red,
  Walk: styles.green,
  Standby: styles.yellow,
};

interface Point {
  x: number;
  y: number;
}

interface Area {
  x: number;
  y: number;
  width: number;
  height: number;
}

const Markers = createContext('lesson');

function Picture({ width, height, label, children }: { width: number; height: number; label: string; children: React.ReactNode }): React.JSX.Element {
  const prefix = `lesson${useId().replace(/:/g, '')}`;
  return (
    <figure className={styles.picture}>
      <svg className={styles.figure} viewBox={`0 0 ${String(width)} ${String(height)}`} role="img" aria-label={label} style={{ maxWidth: `${String(width)}px` }}>
        <defs>
          <marker id={`${prefix}-arrow`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
            <path className={styles.head} d="M 0 0 L 10 5 L 0 10 z" />
          </marker>
          <marker id={`${prefix}-triangle`} viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" orient="auto-start-reverse">
            <path className={styles.hollow} d="M 1 1 L 11 6 L 1 11 z" />
          </marker>
          <marker id={`${prefix}-diamond`} viewBox="0 0 16 10" refX="15" refY="5" markerWidth="16" markerHeight="10" orient="auto-start-reverse">
            <path className={styles.head} d="M 1 5 L 8 1 L 15 5 L 8 9 z" />
          </marker>
        </defs>
        <Markers.Provider value={prefix}>{children}</Markers.Provider>
      </svg>
    </figure>
  );
}

function Line({ className, x1, y1, x2, y2, end, start }: { className: string | undefined; x1: number; y1: number; x2: number; y2: number; end?: string; start?: string }): React.JSX.Element {
  const prefix = useContext(Markers);
  return <line className={className} x1={x1} y1={y1} x2={x2} y2={y2} markerEnd={end === undefined ? undefined : `url(#${prefix}-${end})`} markerStart={start === undefined ? undefined : `url(#${prefix}-${start})`} />;
}

function Path({ className, d, end }: { className: string | undefined; d: string; end: string }): React.JSX.Element {
  const prefix = useContext(Markers);
  return <path className={className} d={d} fill="none" markerEnd={`url(#${prefix}-${end})`} />;
}

function Text({ x, y, children, anchor = 'middle', kind = 'label', size = 16 }: { x: number; y: number; children: React.ReactNode; anchor?: 'start' | 'middle' | 'end'; kind?: 'label' | 'caption' | 'code' | 'failed'; size?: number }): React.JSX.Element {
  return (
    <text className={styles[kind]} x={x} y={y} fontSize={size} textAnchor={anchor} dominantBaseline="middle">
      {children}
    </text>
  );
}

function edgeOf(area: Area, towards: Point): Point {
  const center = centerOf(area);
  const dx = towards.x - center.x;
  const dy = towards.y - center.y;
  const scale = Math.min(dx === 0 ? Infinity : area.width / 2 / Math.abs(dx), dy === 0 ? Infinity : area.height / 2 / Math.abs(dy));
  return { x: center.x + dx * scale, y: center.y + dy * scale };
}

function centerOf(area: Area): Point {
  return { x: area.x + area.width / 2, y: area.y + area.height / 2 };
}

function Connector({ from, to, kind = 'arrow' }: { from: Area; to: Area; kind?: 'arrow' | 'dashed' | 'dotted' }): React.JSX.Element {
  const begin = edgeOf(from, centerOf(to));
  const end = edgeOf(to, centerOf(from));
  return <Line className={styles[kind]} x1={begin.x} y1={begin.y} x2={end.x} y2={end.y} end="arrow" />;
}

function StateBox({ area, name, lamp }: { area: Area; name: string; lamp?: Lamp }): React.JSX.Element {
  return (
    <g>
      <rect className={styles.state} x={area.x} y={area.y} width={area.width} height={area.height} rx={Math.min(area.height / 2, 18)} />
      {lamp === undefined ? null : <circle className={LAMP_CLASS[lamp]} cx={area.x + 18} cy={area.y + area.height / 2} r={7} />}
      <Text x={area.x + area.width / 2 + (lamp === undefined ? 0 : 8)} y={area.y + area.height / 2} kind="code">{name}</Text>
    </g>
  );
}

function SignalHead({ x, y, lamps, label, labelSide = 'right' }: { x: number; y: number; lamps: Lamp[]; label: string; labelSide?: 'right' | 'left' }): React.JSX.Element {
  const height = 12 + lamps.length * 26;
  return (
    <g>
      <rect className={styles.housing} x={x} y={y} width={32} height={height} rx={6} />
      {lamps.map((lamp, index) => {
        return <circle key={lamp} className={LAMP_CLASS[lamp]} cx={x + 16} cy={y + 19 + index * 26} r={9} />;
      })}
      <Text x={labelSide === 'right' ? x + 42 : x - 10} y={y + height / 2} anchor={labelSide === 'right' ? 'start' : 'end'}>{label}</Text>
    </g>
  );
}

function SensorLoop({ area }: { area: Area }): React.JSX.Element {
  return <rect className={styles.sensor} x={area.x} y={area.y} width={area.width} height={area.height} rx={3} />;
}

export function Junction({ crosswalk = false }: { crosswalk?: boolean }): React.JSX.Element {
  const label = crosswalk
    ? 'A junction of a road from the north and a road from the east, each with a traffic light and a sensor in the road before the junction, and a crosswalk over the east road in front of the east light, with a pedestrian light and its button'
    : 'A junction of a road from the north and a road from the east, each with a traffic light and a sensor in the road before the junction';
  // the crosswalk lies between the junction and the east stop line, so the east sensor and light move out
  const eastSensor = crosswalk ? 318 : 262;
  return (
    <Picture width={420} height={300} label={label}>
      <rect className={styles.road} x={170} y={0} width={80} height={300} />
      <rect className={styles.road} x={0} y={110} width={420} height={80} />
      <rect className={styles.crossing} x={170} y={110} width={80} height={80} />
      <line className={styles.lane} x1={210} y1={0} x2={210} y2={110} />
      <line className={styles.lane} x1={210} y1={190} x2={210} y2={300} />
      <line className={styles.lane} x1={0} y1={150} x2={170} y2={150} />
      <line className={styles.lane} x1={crosswalk ? 310 : 250} y1={150} x2={420} y2={150} />
      {crosswalk
        ? [0, 1, 2, 3, 4].map((index) => {
          return <rect key={index} className={styles.zebra} x={262} y={114 + index * 15} width={40} height={8} />;
        })
        : null}
      <SensorLoop area={{ x: 176, y: 62, width: 28, height: 36 }} />
      <SensorLoop area={{ x: eastSensor, y: 116, width: 36, height: 28 }} />
      <Line className={styles.arrow} x1={190} y1={8} x2={190} y2={54} end="arrow" />
      <Line className={styles.arrow} x1={416} y1={130} x2={eastSensor + 44} y2={130} end="arrow" />
      <SignalHead x={124} y={14} lamps={['Red', 'Yellow', 'Green']} label="north" labelSide="left" />
      <Line className={styles.dotted} x1={204} y1={70} x2={262} y2={36} />
      <Text x={268} y={36} anchor="start" kind="caption" size={15}>sensor</Text>
      {crosswalk
        ? (
          <g>
            <SignalHead x={360} y={204} lamps={['Red', 'Yellow', 'Green']} label="east" labelSide="left" />
            <SignalHead x={262} y={48} lamps={['Stop', 'Walk']} label="pedestrians" />
            <rect className={styles.housing} x={272} y={198} width={20} height={20} rx={4} />
            <circle className={styles.start} cx={282} cy={208} r={5} />
            <Text x={300} y={208} anchor="start" kind="caption" size={15}>button</Text>
          </g>
        )
        : (
          <g>
            <SignalHead x={290} y={204} lamps={['Red', 'Yellow', 'Green']} label="east" />
            <Line className={styles.dotted} x1={280} y1={116} x2={280} y2={96} />
            <Text x={280} y={86} kind="caption" size={15}>sensor</Text>
          </g>
        )}
    </Picture>
  );
}

export function LightCycle(): React.JSX.Element {
  const red = { x: 10, y: 70, width: 110, height: 40 };
  const green = { x: 210, y: 70, width: 110, height: 40 };
  const yellow = { x: 410, y: 70, width: 110, height: 40 };
  return (
    <Picture width={530} height={180} label="The cycle of a traffic light: it starts red, turns green only for a waiting car, then turns yellow, then red again">
      <circle className={styles.start} cx={65} cy={22} r={7} />
      <Line className={styles.arrow} x1={65} y1={29} x2={65} y2={68} end="arrow" />
      <Connector from={red} to={green} />
      <Text x={165} y={56} kind="caption" size={15}>car waiting</Text>
      <Connector from={green} to={yellow} />
      <Path className={styles.arrow} d="M 465 110 C 465 172 65 172 65 112" end="arrow" />
      <StateBox area={red} name="Red" lamp="Red" />
      <StateBox area={green} name="Green" lamp="Green" />
      <StateBox area={yellow} name="Yellow" lamp="Yellow" />
    </Picture>
  );
}

type TraceRow = { name: string; lamps: Lamp[] } | { name: string; flags: boolean[] } | { name: string; numbers: number[] };

export function TraceTimeline({ rows, broken }: { rows: TraceRow[]; broken: string }): React.JSX.Element {
  const left = 132;
  const top = 40;
  const rowHeight = 50;
  const states = rows.map((row) => {
    return 'lamps' in row ? row.lamps.length : 'flags' in row ? row.flags.length : row.numbers.length;
  }).reduce((longest, length) => {
    return Math.max(longest, length);
  }, 0);
  // a long trace gets narrower columns, so that it stays readable on a phone
  const column = states > 5 ? 54 : 70;
  const width = left + column * states + 8;
  const height = top + rowHeight * rows.length + 34;
  const last = states - 1;
  const names = rows.map((row) => {
    return row.name;
  }).join(', ');
  return (
    <Picture width={width} height={height} label={`A trace of ${String(states)} states that shows ${names}, whose last state breaks ${broken}`}>
      <rect className={styles.broken} x={left + column * last + 4} y={4} width={column - 8} height={top + rowHeight * rows.length - 8} rx={8} />
      {Array.from({ length: states }, (_, index) => {
        return <Text key={index} x={left + column * index + column / 2} y={22} kind="caption" size={14}>{index === 0 ? 'start' : `step ${String(index)}`}</Text>;
      })}
      {rows.map((row, rowIndex) => {
        const y = top + rowHeight * rowIndex + rowHeight / 2;
        return (
          <g key={row.name}>
            <Text x={left - 12} y={y} anchor="end" size={15}>{row.name}</Text>
            {'lamps' in row
              ? row.lamps.map((lamp, index) => {
                return <circle key={index} className={LAMP_CLASS[lamp]} cx={left + column * index + column / 2} cy={y} r={13} />;
              })
              : null}
            {'flags' in row
              ? row.flags.map((flag, index) => {
                return <Text key={index} x={left + column * index + column / 2} y={y} kind={flag ? 'code' : 'caption'} size={15}>{flag ? 'yes' : 'no'}</Text>;
              })
              : null}
            {'numbers' in row
              ? row.numbers.map((value, index) => {
                return <Text key={index} x={left + column * index + column / 2} y={y} kind="code" size={16}>{String(value)}</Text>;
              })
              : null}
          </g>
        );
      })}
      <Text x={width - 8} y={height - 14} anchor="end" kind="failed" size={15}>{`breaks ${broken}`}</Text>
    </Picture>
  );
}

function ClassBox({ area, name, lines, abstract = false }: { area: Area; name: string; lines: string[]; abstract?: boolean }): React.JSX.Element {
  return (
    <g>
      <rect className={styles.classBox} x={area.x} y={area.y} width={area.width} height={area.height} rx={6} />
      <text className={styles.code} x={area.x + area.width / 2} y={area.y + 17} fontSize={16} textAnchor="middle" dominantBaseline="middle" fontStyle={abstract ? 'italic' : undefined}>{name}</text>
      {lines.length === 0 ? null : <line className={styles.rule} x1={area.x} y1={area.y + 33} x2={area.x + area.width} y2={area.y + 33} />}
      {lines.map((line, index) => {
        return <Text key={line} x={area.x + 10} y={area.y + 50 + index * 20} anchor="start" kind="caption" size={15}>{line}</Text>;
      })}
    </g>
  );
}

export function LibraryClasses(): React.JSX.Element {
  const condition = { x: 120, y: 8, width: 160, height: 62 };
  const state = { x: 8, y: 120, width: 150, height: 82 };
  const sensor = { x: 242, y: 120, width: 150, height: 62 };
  const statechart = { x: 8, y: 302, width: 150, height: 102 };
  const transition = { x: 250, y: 302, width: 142, height: 62 };
  return (
    <Picture width={400} height={414} label="The library's classes: State and Sensor are kinds of Condition, a Statechart contains states and transitions and holds its active state, and a Transition refers to the state it leaves, the state it enters and its guards, which are conditions">
      <Line className={styles.line} x1={83} y1={state.y} x2={170} y2={condition.y + condition.height + 2} end="triangle" />
      <Line className={styles.line} x1={317} y1={sensor.y} x2={230} y2={condition.y + condition.height + 2} end="triangle" />
      <Line className={styles.line} x1={60} y1={statechart.y} x2={60} y2={state.y + state.height} start="diamond" />
      <Text x={68} y={252} anchor="start" kind="caption" size={15}>states</Text>
      <Line className={styles.line} x1={statechart.x + statechart.width} y1={330} x2={transition.x} y2={330} start="diamond" />
      <Text x={204} y={314} kind="caption" size={15}>transitions</Text>
      <Line className={styles.arrow} x1={transition.x + 20} y1={transition.y} x2={state.x + state.width - 12} y2={state.y + state.height + 2} end="arrow" />
      <Text x={214} y={240} anchor="start" kind="caption" size={15}>from, to</Text>
      <Path className={styles.arrow} d={`M ${String(transition.x + transition.width - 20)} ${String(transition.y)} V 240 H 394 V 39 H ${String(condition.x + condition.width + 2)}`} end="arrow" />
      <Text x={366} y={276} anchor="end" kind="caption" size={15}>guards</Text>
      <ClassBox area={condition} name="Condition" lines={['isActive()']} abstract />
      <ClassBox area={state} name="State" lines={['isActive()', 'activate()']} />
      <ClassBox area={sensor} name="Sensor" lines={['var detects']} />
      <ClassBox area={statechart} name="Statechart" lines={['var activeState', 'initial', 'main: fires one']} />
      <ClassBox area={transition} name="Transition" lines={['main: fires']} />
    </Picture>
  );
}

function ObjectBox({ area, name }: { area: Area; name: string }): React.JSX.Element {
  return (
    <g>
      <rect className={styles.object} x={area.x} y={area.y} width={area.width} height={area.height} rx={4} />
      <Text x={area.x + area.width / 2} y={area.y + area.height / 2} kind="code" size={15}>{name}</Text>
    </g>
  );
}

export function LightAsData(): React.JSX.Element {
  const sensor = { x: 20, y: 8, width: 120, height: 30 };
  const crossing = { x: 240, y: 8, width: 190, height: 30 };
  const red = { x: 10, y: 100, width: 110, height: 38 };
  const green = { x: 330, y: 100, width: 110, height: 38 };
  const yellow = { x: 170, y: 320, width: 110, height: 38 };
  const redToGreen = { x: 160, y: 104, width: 130, height: 30 };
  const greenToYellow = { x: 290, y: 214, width: 150, height: 30 };
  const yellowToRed = { x: 10, y: 214, width: 130, height: 30 };
  return (
    <Picture width={450} height={410} label="The traffic light as data: the states red, green and yellow, and the transitions redToGreen, greenToYellow and yellowToRed, each referring to the state it leaves and the state it enters; redToGreen also refers to its guards, the light's sensor and the states it waits for, in which the crossing traffic is stopped">
      <Connector from={redToGreen} to={sensor} kind="dotted" />
      <Connector from={redToGreen} to={crossing} kind="dotted" />
      <Connector from={redToGreen} to={red} kind="dashed" />
      <Connector from={redToGreen} to={green} />
      <Connector from={greenToYellow} to={green} kind="dashed" />
      <Connector from={greenToYellow} to={yellow} />
      <Connector from={yellowToRed} to={yellow} kind="dashed" />
      <Connector from={yellowToRed} to={red} />
      <ObjectBox area={sensor} name="sensor" />
      <ObjectBox area={crossing} name="waitsFor" />
      <StateBox area={red} name="red" lamp="Red" />
      <StateBox area={green} name="green" lamp="Green" />
      <StateBox area={yellow} name="yellow" lamp="Yellow" />
      <ObjectBox area={redToGreen} name="redToGreen" />
      <ObjectBox area={greenToYellow} name="greenToYellow" />
      <ObjectBox area={yellowToRed} name="yellowToRed" />
      <Line className={styles.dashed} x1={10} y1={392} x2={44} y2={392} end="arrow" />
      <Text x={50} y={392} anchor="start" kind="caption" size={15}>from</Text>
      <Line className={styles.arrow} x1={130} y1={392} x2={164} y2={392} end="arrow" />
      <Text x={170} y={392} anchor="start" kind="caption" size={15}>to</Text>
      <Line className={styles.dotted} x1={230} y1={392} x2={264} y2={392} end="arrow" />
      <Text x={270} y={392} anchor="start" kind="caption" size={15}>guards</Text>
    </Picture>
  );
}

function RulePanel({ x, title, north, east, note, broken = false }: { x: number; title: string[]; north: Lamp; east: Lamp; note: string; broken?: boolean }): React.JSX.Element {
  const center = x + 75;
  return (
    <g>
      {title.map((line, index) => {
        return <Text key={line} x={center} y={18 + index * 20} size={15}>{line}</Text>;
      })}
      {broken ? <rect className={styles.broken} x={x + 30} y={158} width={90} height={64} rx={8} /> : null}
      <circle className={LAMP_CLASS.Red} cx={center - 22} cy={92} r={13} />
      <circle className={LAMP_CLASS.Red} cx={center + 22} cy={92} r={13} />
      <Line className={styles.arrow} x1={center} y1={114} x2={center} y2={150} end="arrow" />
      <circle className={LAMP_CLASS[north]} cx={center - 22} cy={176} r={13} />
      <circle className={LAMP_CLASS[east]} cx={center + 22} cy={176} r={13} />
      <Text x={center - 22} y={206} kind="caption" size={13}>north</Text>
      <Text x={center + 22} y={206} kind="caption" size={13}>east</Text>
      <Text x={center} y={244} kind={broken ? 'failed' : 'caption'} size={14}>{note}</Text>
    </g>
  );
}

export function CompositionRules(): React.JSX.Element {
  return (
    <Picture width={470} height={262} label="A step from both lights red with a car waiting at each, under three rules. One part in a step: the north light turns green, the east light waits. One after the other: the north light turns green, and the east light, which steps second, sees it green and stays red. All at once: both lights saw the other red before the step, and both turn green">
      <RulePanel x={0} title={['one part', 'in a step']} north="Green" east="Red" note="east waits" />
      <line className={styles.rule} x1={158} y1={10} x2={158} y2={250} />
      <RulePanel x={160} title={['one after', 'the other']} north="Green" east="Red" note="east sees green" />
      <line className={styles.rule} x1={318} y1={10} x2={318} y2={250} />
      <RulePanel x={320} title={['all at', 'once']} north="Green" east="Green" note="both saw red" broken />
    </Picture>
  );
}

export function InstanceTree(): React.JSX.Element {
  const root = { x: 120, y: 44, width: 220, height: 32 };
  const hall = { x: 170, y: 112, width: 120, height: 32 };
  const top = { x: 36, y: 186, width: 130, height: 32 };
  const bottom = { x: 294, y: 186, width: 130, height: 32 };
  const fuse = { x: 170, y: 262, width: 120, height: 32 };
  return (
    <Picture width={460} height={316} label="The instance tree of the room model inside the universe: the root, an instance of BothLampsCanBeOn, contains the hall, the hall contains the top lamp, a Lamp, and the bottom lamp, a Spot, and both lamps share one fuse">
      <rect className={styles.state} x={6} y={6} width={448} height={304} rx={14} />
      <Text x={22} y={24} anchor="start" kind="caption" size={14}>universe</Text>
      <Line className={styles.line} x1={230} y1={root.y + root.height} x2={230} y2={hall.y} start="diamond" />
      <Line className={styles.line} x1={200} y1={hall.y + hall.height} x2={101} y2={top.y} start="diamond" />
      <Line className={styles.line} x1={260} y1={hall.y + hall.height} x2={359} y2={bottom.y} start="diamond" />
      <Line className={styles.dashed} x1={101} y1={top.y + top.height} x2={200} y2={fuse.y} start="diamond" />
      <Line className={styles.dashed} x1={359} y1={bottom.y + bottom.height} x2={260} y2={fuse.y} start="diamond" />
      <ObjectBox area={root} name="root: BothLampsCanBeOn" />
      <ObjectBox area={hall} name="hall: Hall" />
      <ObjectBox area={top} name="top: Lamp" />
      <ObjectBox area={bottom} name="bottom: Spot" />
      <ObjectBox area={fuse} name="fuse: Fuse" />
      <Text x={300} y={278} anchor="start" kind="caption" size={14}>shared</Text>
    </Picture>
  );
}

export function NestedStates(): React.JSX.Element {
  const operating = { x: 8, y: 30, width: 300, height: 210 };
  const red = { x: 30, y: 84, width: 110, height: 38 };
  const green = { x: 180, y: 84, width: 110, height: 38 };
  const yellow = { x: 105, y: 180, width: 110, height: 38 };
  const standby = { x: 380, y: 116, width: 120, height: 40 };
  return (
    <Picture width={510} height={250} label="A traffic light with nested states: the colors red, green and yellow are substates of an operating state, which starts at red; beside it a standby state, which the light enters from operating and leaves back to operating">
      <rect className={styles.state} x={operating.x} y={operating.y} width={operating.width} height={operating.height} rx={18} />
      <Text x={operating.x + 16} y={operating.y + 22} anchor="start" kind="code">operating</Text>
      <circle className={styles.start} cx={85} cy={58} r={6} />
      <Line className={styles.arrow} x1={85} y1={64} x2={85} y2={82} end="arrow" />
      <Connector from={red} to={green} />
      <Connector from={green} to={yellow} />
      <Connector from={yellow} to={red} />
      <StateBox area={red} name="red" lamp="Red" />
      <StateBox area={green} name="green" lamp="Green" />
      <StateBox area={yellow} name="yellow" lamp="Yellow" />
      <Path className={styles.arrow} d="M 308 124 H 378" end="arrow" />
      <Path className={styles.arrow} d="M 378 148 H 310" end="arrow" />
      <StateBox area={standby} name="standby" lamp="Standby" />
    </Picture>
  );
}
