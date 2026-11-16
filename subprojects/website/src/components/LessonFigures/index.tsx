/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { createContext, useContext, useId } from 'react';
import styles from './styles.module.css';

export type Color = 'Red' | 'Yellow' | 'Green';

type Lamp = Color | 'Walk' | 'Stop';

const LAMP_CLASS: Readonly<Record<Lamp, string | undefined>> = {
  Red: styles.red,
  Yellow: styles.yellow,
  Green: styles.green,
  Stop: styles.red,
  Walk: styles.green,
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

function Text({ x, y, children, anchor = 'middle', kind = 'label', size = 15 }: { x: number; y: number; children: React.ReactNode; anchor?: 'start' | 'middle' | 'end'; kind?: 'label' | 'caption' | 'code' | 'failed'; size?: number }): React.JSX.Element {
  return (
    <text className={styles[kind]} x={x} y={y} fontSize={size} textAnchor={anchor} dominantBaseline="middle">
      {children}
    </text>
  );
}

// where the segment from a box's centre towards a point leaves the box
function edgeOf(area: Area, towards: Point): Point {
  const center = { x: area.x + area.width / 2, y: area.y + area.height / 2 };
  const dx = towards.x - center.x;
  const dy = towards.y - center.y;
  const scale = Math.min(dx === 0 ? Infinity : area.width / 2 / Math.abs(dx), dy === 0 ? Infinity : area.height / 2 / Math.abs(dy));
  return { x: center.x + dx * scale, y: center.y + dy * scale };
}

function centerOf(area: Area): Point {
  return { x: area.x + area.width / 2, y: area.y + area.height / 2 };
}

function Connector({ from, to, kind = 'arrow' }: { from: Area; to: Area; kind?: 'arrow' | 'dashed' }): React.JSX.Element {
  const begin = edgeOf(from, centerOf(to));
  const end = edgeOf(to, centerOf(from));
  return <Line className={styles[kind]} x1={begin.x} y1={begin.y} x2={end.x} y2={end.y} end="arrow" />;
}

function StateBox({ area, name, lamp }: { area: Area; name: string; lamp?: Lamp }): React.JSX.Element {
  return (
    <g>
      <rect className={styles.state} x={area.x} y={area.y} width={area.width} height={area.height} rx={area.height / 2} />
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
      <Text x={labelSide === 'right' ? x + 42 : x - 10} y={y + height / 2} anchor={labelSide === 'right' ? 'start' : 'end'} kind="code">{label}</Text>
    </g>
  );
}

export function Junction(): React.JSX.Element {
  return (
    <Picture width={420} height={300} label="A junction of a north-south and an east-west road, with a traffic light for each direction, north and east">
      <rect className={styles.road} x={170} y={0} width={80} height={300} />
      <rect className={styles.road} x={0} y={110} width={420} height={80} />
      <rect className={styles.crossing} x={170} y={110} width={80} height={80} />
      <line className={styles.lane} x1={210} y1={0} x2={210} y2={110} />
      <line className={styles.lane} x1={210} y1={190} x2={210} y2={300} />
      <line className={styles.lane} x1={0} y1={150} x2={170} y2={150} />
      <line className={styles.lane} x1={250} y1={150} x2={420} y2={150} />
      <Line className={styles.arrow} x1={190} y1={14} x2={190} y2={96} end="arrow" />
      <Line className={styles.arrow} x1={406} y1={130} x2={264} y2={130} end="arrow" />
      <SignalHead x={124} y={14} lamps={['Red', 'Yellow', 'Green']} label="north" labelSide="left" />
      <SignalHead x={290} y={204} lamps={['Red', 'Yellow', 'Green']} label="east" />
    </Picture>
  );
}

export function LightCycle(): React.JSX.Element {
  const red = { x: 20, y: 60, width: 110, height: 40 };
  const green = { x: 200, y: 60, width: 110, height: 40 };
  const yellow = { x: 380, y: 60, width: 110, height: 40 };
  return (
    <Picture width={510} height={170} label="The cycle of a traffic light: it starts red, turns green, then yellow, then red again">
      <circle className={styles.start} cx={75} cy={18} r={7} />
      <Line className={styles.arrow} x1={75} y1={25} x2={75} y2={58} end="arrow" />
      <Connector from={red} to={green} />
      <Connector from={green} to={yellow} />
      <Path className={styles.arrow} d="M 435 100 C 435 160 75 160 75 102" end="arrow" />
      <StateBox area={red} name="Red" lamp="Red" />
      <StateBox area={green} name="Green" lamp="Green" />
      <StateBox area={yellow} name="Yellow" lamp="Yellow" />
    </Picture>
  );
}

export function TraceTimeline({ states, broken }: { states: [Color, Color][]; broken: string }): React.JSX.Element {
  const column = 86;
  const left = 96;
  const width = left + column * states.length + 10;
  const last = states.length - 1;
  return (
    <Picture width={width} height={180} label={`A trace of ${String(states.length)} states of the north and the east light, whose last state breaks ${broken}`}>
      <rect className={styles.broken} x={left + column * last + 4} y={6} width={column - 8} height={142} rx={8} />
      <Text x={left - 12} y={66} anchor="end" kind="code">north</Text>
      <Text x={left - 12} y={116} anchor="end" kind="code">east</Text>
      {states.map(([north, east], index) => {
        const x = left + column * index + column / 2;
        return (
          <g key={index}>
            <Text x={x} y={24} kind="caption" size={13}>{index === 0 ? 'start' : `step ${String(index)}`}</Text>
            <circle className={LAMP_CLASS[north]} cx={x} cy={66} r={13} />
            <Text x={x} y={88} kind="caption" size={12}>{north}</Text>
            <circle className={LAMP_CLASS[east]} cx={x} cy={116} r={13} />
            <Text x={x} y={138} kind="caption" size={12}>{east}</Text>
          </g>
        );
      })}
      <Text x={left + column * states.length} y={166} anchor="end" kind="failed" size={13}>{`breaks ${broken}`}</Text>
    </Picture>
  );
}

function ClassBox({ area, name, lines }: { area: Area; name: string; lines: string[] }): React.JSX.Element {
  return (
    <g>
      <rect className={styles.classBox} x={area.x} y={area.y} width={area.width} height={area.height} rx={6} />
      <Text x={area.x + area.width / 2} y={area.y + 16} kind="code">{name}</Text>
      {lines.length === 0 ? null : <line className={styles.rule} x1={area.x} y1={area.y + 30} x2={area.x + area.width} y2={area.y + 30} />}
      {lines.map((line, index) => {
        return <Text key={line} x={area.x + 10} y={area.y + 46 + index * 18} anchor="start" kind="caption" size={13}>{line}</Text>;
      })}
    </g>
  );
}

export function LibraryClasses(): React.JSX.Element {
  const statechart = { x: 200, y: 10, width: 240, height: 66 };
  const state = { x: 10, y: 170, width: 170, height: 84 };
  const transition = { x: 460, y: 170, width: 170, height: 66 };
  const light = { x: 230, y: 180, width: 180, height: 32 };
  return (
    <Picture width={640} height={320} label="The library's classes: a Statechart contains states and transitions and holds its active state, a Transition refers to a from and a to state, and TrafficLight extends Statechart">
      <Path className={styles.arrow} d={`M ${String(transition.x + transition.width / 2)} ${String(transition.y + transition.height)} V 300 H ${String(state.x + state.width / 2)} V ${String(state.y + state.height + 2)}`} end="arrow" />
      <Text x={320} y={290} kind="caption" size={13}>from, to</Text>
      <Line className={styles.line} x1={light.x + light.width / 2} y1={light.y} x2={light.x + light.width / 2} y2={statechart.y + statechart.height + 2} end="triangle" />
      <ClassBox area={statechart} name="Statechart" lines={['var activeState: states[0..1]']} />
      <ClassBox area={state} name="State" lines={['isActive()', 'activate()']} />
      <ClassBox area={transition} name="Transition" lines={['main: fires']} />
      <ClassBox area={light} name="TrafficLight" lines={[]} />
      <Line className={styles.line} x1={statechart.x + 30} y1={statechart.y + statechart.height} x2={state.x + state.width / 2} y2={state.y} start="diamond" />
      <Text x={88} y={112} anchor="start" kind="caption" size={13}>states [0..*]</Text>
      <Line className={styles.line} x1={statechart.x + statechart.width - 30} y1={statechart.y + statechart.height} x2={transition.x + transition.width / 2} y2={transition.y} start="diamond" />
      <Text x={552} y={112} anchor="end" kind="caption" size={13}>transitions [0..*]</Text>
    </Picture>
  );
}

function ObjectBox({ area, name }: { area: Area; name: string }): React.JSX.Element {
  return (
    <g>
      <rect className={styles.object} x={area.x} y={area.y} width={area.width} height={area.height} rx={4} />
      <Text x={area.x + area.width / 2} y={area.y + area.height / 2} kind="code" size={14}>{name}</Text>
    </g>
  );
}

export function LightAsData(): React.JSX.Element {
  const red = { x: 30, y: 30, width: 120, height: 38 };
  const green = { x: 450, y: 30, width: 120, height: 38 };
  const yellow = { x: 240, y: 240, width: 120, height: 38 };
  const redToGreen = { x: 240, y: 34, width: 120, height: 30 };
  const greenToYellow = { x: 370, y: 140, width: 130, height: 30 };
  const yellowToRed = { x: 100, y: 140, width: 120, height: 30 };
  return (
    <Picture width={600} height={330} label="The light as data: the states red, green and yellow, and the transitions redToGreen, greenToYellow and yellowToRed, each referring to the state it leaves and the state it enters">
      <Connector from={redToGreen} to={red} kind="dashed" />
      <Connector from={redToGreen} to={green} />
      <Connector from={greenToYellow} to={green} kind="dashed" />
      <Connector from={greenToYellow} to={yellow} />
      <Connector from={yellowToRed} to={yellow} kind="dashed" />
      <Connector from={yellowToRed} to={red} />
      <StateBox area={red} name="red" lamp="Red" />
      <StateBox area={green} name="green" lamp="Green" />
      <StateBox area={yellow} name="yellow" lamp="Yellow" />
      <ObjectBox area={redToGreen} name="redToGreen" />
      <ObjectBox area={greenToYellow} name="greenToYellow" />
      <ObjectBox area={yellowToRed} name="yellowToRed" />
      <Line className={styles.dashed} x1={150} y1={312} x2={190} y2={312} end="arrow" />
      <Text x={198} y={312} anchor="start" kind="caption" size={13}>from</Text>
      <Line className={styles.arrow} x1={300} y1={312} x2={340} y2={312} end="arrow" />
      <Text x={348} y={312} anchor="start" kind="caption" size={13}>to</Text>
    </Picture>
  );
}

export function Crossing(): React.JSX.Element {
  return (
    <Picture width={420} height={240} label="A pedestrian crossing: a road with a crosswalk, a traffic light for the cars and a pedestrian light that shows stop or walk">
      <rect className={styles.road} x={0} y={90} width={420} height={90} />
      {[0, 1, 2, 3, 4, 5].map((index) => {
        return <rect key={index} className={styles.zebra} x={186} y={96 + index * 14} width={48} height={8} />;
      })}
      <Line className={styles.arrow} x1={20} y1={160} x2={150} y2={160} end="arrow" />
      <Line className={styles.arrow} x1={210} y1={230} x2={210} y2={192} end="arrow" />
      <SignalHead x={130} y={0} lamps={['Red', 'Yellow', 'Green']} label="light" labelSide="left" />
      <SignalHead x={262} y={22} lamps={['Stop', 'Walk']} label="pedestrians" />
    </Picture>
  );
}

function StepChain({ x, title, states }: { x: number; title: string; states: { lamp: Color; via?: string }[] }): React.JSX.Element {
  return (
    <g>
      <Text x={x + 130} y={16} kind="code">{title}</Text>
      {states.map((state, index) => {
        const y = 40 + index * 58;
        const area = { x: x + 70, y, width: 120, height: 32 };
        return (
          <g key={index}>
            {state.via === undefined ? null : (
              <g>
                <Line className={styles.arrow} x1={x + 130} y1={y - 26} x2={x + 130} y2={y - 2} end="arrow" />
                <Text x={x + 142} y={y - 13} anchor="start" kind="caption" size={13}>{state.via}</Text>
              </g>
            )}
            <StateBox area={area} name={state.lamp.toLowerCase()} lamp={state.lamp} />
          </g>
        );
      })}
    </g>
  );
}

export function RuleSteps(): React.JSX.Element {
  const height = 40 + 3 * 58 + 32 + 40;
  return (
    <Picture width={560} height={height} label="One step of the traffic light from red. With inline for choice one transition fires and the light ends green. With inline for every transition runs in order, redToGreen, greenToYellow, yellowToRed, and the light ends the step red">
      <StepChain x={0} title="inline for choice" states={[{ lamp: 'Red' }, { lamp: 'Green', via: 'redToGreen' }]} />
      <StepChain x={280} title="inline for" states={[{ lamp: 'Red' }, { lamp: 'Green', via: 'redToGreen' }, { lamp: 'Yellow', via: 'greenToYellow' }, { lamp: 'Red', via: 'yellowToRed' }]} />
      <line className={styles.rule} x1={280} y1={30} x2={280} y2={height - 30} />
      <Text x={130} y={height - 14} kind="caption" size={13}>end of the step: green</Text>
      <Text x={410} y={height - 14} kind="caption" size={13}>end of the step: red</Text>
    </Picture>
  );
}
