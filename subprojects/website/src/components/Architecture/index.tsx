/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import { usePluginData } from '@docusaurus/useGlobalData';
import clsx from 'clsx';

import styles from './styles.module.css';

const STACK_OFFSET = 6;

export interface Backend {
  name: string;
  page: string;
}

// the backends the reference has a page for, read from those pages when the site is built
function useBackends(): Backend[] {
  return (usePluginData('backends') as { backends: Backend[] }).backends;
}

interface Rectangle {
  x: number;
  y: number;
  width: number;
  height: number;
}

interface Layout {
  name: string;
  width: number;
  height: number;
  fontSize: number;
  model: Rectangle;
  semantifyrModel: Rectangle;
  formalModel: Rectangle;
  trace: Rectangle;
  semantifyrTrace: Rectangle;
  formalTrace: Rectangle;
  library: Rectangle;
  frontend: Rectangle;
  semantifyr: Rectangle;
  backend: Rectangle;
  vertical: boolean;
  forward: [number, number, number, number][];
  backward: [number, number, number, number][];
  libraryPath: string;
  refines: [number, number, number, number][];
  backendsCaption: [number, number];
}

const DESKTOP: Layout = {
  name: 'desktop',
  width: 1130,
  height: 440,
  fontSize: 21,
  model: { x: 10, y: 128, width: 180, height: 60 },
  semantifyrModel: { x: 440, y: 128, width: 200, height: 60 },
  formalModel: { x: 792, y: 128, width: 170, height: 60 },
  trace: { x: 10, y: 300, width: 180, height: 60 },
  semantifyrTrace: { x: 440, y: 300, width: 200, height: 60 },
  formalTrace: { x: 792, y: 300, width: 170, height: 60 },
  library: { x: 440, y: 6, width: 200, height: 54 },
  frontend: { x: 250, y: 96, width: 60, height: 308 },
  semantifyr: { x: 680, y: 96, width: 72, height: 308 },
  backend: { x: 1002, y: 116, width: 60, height: 240 },
  vertical: true,
  forward: [
    [190, 158, 250, 158],
    [310, 158, 440, 158],
    [640, 158, 680, 158],
    [752, 158, 792, 158],
    [962, 158, 1002, 158],
  ],
  backward: [
    [1002, 330, 962, 330],
    [792, 330, 752, 330],
    [680, 330, 640, 330],
    [440, 330, 310, 330],
    [250, 330, 190, 330],
  ],
  libraryPath: 'M 652 45 H 716 V 96',
  refines: [
    [540, 128, 540, 72],
    [100, 300, 100, 188],
    [540, 300, 540, 188],
    [877, 300, 877, 188],
  ],
  backendsCaption: [1038, 392],
};

const PHONE: Layout = {
  name: 'phone',
  width: 360,
  height: 470,
  fontSize: 13,
  model: { x: 124, y: 0, width: 100, height: 48 },
  semantifyrModel: { x: 124, y: 142, width: 100, height: 48 },
  formalModel: { x: 124, y: 292, width: 100, height: 48 },
  trace: { x: 268, y: 0, width: 92, height: 48 },
  semantifyrTrace: { x: 268, y: 142, width: 92, height: 48 },
  formalTrace: { x: 268, y: 292, width: 92, height: 48 },
  library: { x: 0, y: 136, width: 72, height: 48 },
  frontend: { x: 124, y: 78, width: 236, height: 34 },
  semantifyr: { x: 124, y: 220, width: 236, height: 42 },
  backend: { x: 124, y: 370, width: 224, height: 34 },
  vertical: false,
  forward: [
    [174, 48, 174, 78],
    [174, 112, 174, 142],
    [174, 190, 174, 220],
    [174, 262, 174, 292],
    [174, 340, 174, 370],
  ],
  backward: [
    [314, 370, 314, 340],
    [314, 292, 314, 262],
    [314, 220, 314, 190],
    [314, 142, 314, 112],
    [314, 78, 314, 48],
  ],
  libraryPath: 'M 42 196 V 241 H 124',
  refines: [
    [124, 166, 86, 166],
    [268, 24, 224, 24],
    [268, 166, 224, 166],
    [268, 316, 224, 316],
  ],
  backendsCaption: [242, 436],
};

function Label({ area, text, vertical, fontSize }: { area: Rectangle; text: string; vertical: boolean; fontSize: number }): React.ReactNode {
  const middleX = area.x + area.width / 2;
  const middleY = area.y + area.height / 2;
  const words = !vertical && area.width < 8 * text.length * fontSize / 14 ? text.split(' ') : [text];
  return (
    <text className={styles.label} x={middleX} y={middleY} fontSize={fontSize} textAnchor="middle" dominantBaseline="middle" transform={vertical ? `rotate(-90 ${String(middleX)} ${String(middleY)})` : undefined}>
      {words.map((word, index) => {
        return (
          <tspan key={word} x={middleX} dy={index === 0 ? `${String(-(words.length - 1) * 0.6)}em` : '1.2em'}>
            {word}
          </tspan>
        );
      })}
    </text>
  );
}

function Box({ area, text, kind, vertical, fontSize }: { area: Rectangle; text: string; kind: 'artifact' | 'tool'; vertical: boolean; fontSize: number }): React.ReactNode {
  return (
    <g>
      <rect className={styles[kind]} x={area.x} y={area.y} width={area.width} height={area.height} rx={8} />
      <Label area={area} text={text} vertical={vertical} fontSize={fontSize} />
    </g>
  );
}

function Stack({ area, text, kind, vertical, fontSize }: { area: Rectangle; text: string; kind: 'artifact' | 'tool'; vertical: boolean; fontSize: number }): React.ReactNode {
  const top = { ...area, x: area.x + 2 * STACK_OFFSET, y: area.y + 2 * STACK_OFFSET };
  return (
    <g>
      <rect className={styles[kind]} x={area.x} y={area.y} width={area.width} height={area.height} rx={8} />
      <rect className={styles[kind]} x={area.x + STACK_OFFSET} y={area.y + STACK_OFFSET} width={area.width} height={area.height} rx={8} />
      <Box area={top} text={text} kind={kind} vertical={vertical} fontSize={fontSize} />
    </g>
  );
}

function Logo({ area, vertical }: { area: Rectangle; vertical: boolean }): React.ReactNode {
  const light = useBaseUrl('/img/logo-full-light.svg');
  const dark = useBaseUrl('/img/logo-full-dark.svg');
  const middleX = area.x + area.width / 2;
  const middleY = area.y + area.height / 2;
  const length = vertical ? area.height * 0.84 : area.width * 0.62;
  const thickness = length * 0.205;
  const placement = {
    x: middleX - length / 2,
    y: middleY - thickness / 2,
    width: length,
    height: thickness,
    transform: vertical ? `rotate(-90 ${String(middleX)} ${String(middleY)})` : undefined,
  };
  return (
    <g>
      <rect className={styles.tool} x={area.x} y={area.y} width={area.width} height={area.height} rx={8} />
      <image className={styles.logoLight} href={light} {...placement} />
      <image className={styles.logoDark} href={dark} {...placement} />
    </g>
  );
}

// the backends under their box, two lines beside the vertical figure and one under the turned one
function BackendNames({ layout, backends }: { layout: Layout; backends: Backend[] }): React.ReactNode {
  const [x, y] = layout.backendsCaption;
  const half = Math.ceil(backends.length / 2);
  const lines = layout.vertical ? [backends.slice(0, half), backends.slice(half)] : [backends];
  return (
    <text className={styles.caption} x={x} y={y} fontSize={layout.fontSize * 0.86} textAnchor="middle">
      {lines.map((line, lineIndex) => {
        return (
          <tspan key={line[0]?.name} x={x} dy={lineIndex === 0 ? 0 : '1.2em'}>
            {`${line.map((backend) => {
              return backend.name;
            }).join(', ')}${lineIndex < lines.length - 1 ? ',' : ''}`}
          </tspan>
        );
      })}
    </text>
  );
}

function Figure({ layout, backends }: { layout: Layout; backends: Backend[] }): React.ReactNode {
  const names = backends.map((backend) => {
    return backend.name;
  });
  const { vertical, fontSize } = layout;
  const marker = `architecture-arrow-${layout.name}`;
  const refinesMarker = `architecture-refines-${layout.name}`;
  return (
    <svg className={clsx(styles.figure, styles[layout.name])} viewBox={`0 0 ${String(layout.width)} ${String(layout.height)}`} role="img" aria-label={`A model goes through a frontend to a Semantifyr model, which Semantifyr compiles with the semantic library to a formal model for a backend, ${names.join(', ')}; the traces come back the same way, each refining its model`}>
      <defs>
        <marker id={marker} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path className={styles.head} d="M 0 0 L 10 5 L 0 10 z" />
        </marker>
        <marker id={refinesMarker} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
          <path className={styles.refinesHead} d="M 0 0 L 10 5 L 0 10 z" />
        </marker>
      </defs>
      <Box area={layout.model} text="Model" kind="artifact" vertical={false} fontSize={fontSize} />
      <Box area={layout.frontend} text="Frontend" kind="tool" vertical={vertical} fontSize={fontSize} />
      <Box area={layout.semantifyrModel} text="Semantifyr model" kind="artifact" vertical={false} fontSize={fontSize} />
      <Stack area={layout.library} text="Semantic library" kind="artifact" vertical={false} fontSize={fontSize} />
      <Logo area={layout.semantifyr} vertical={vertical} />
      <Box area={layout.formalModel} text="Formal model" kind="artifact" vertical={false} fontSize={fontSize} />
      <Stack area={layout.backend} text="Backend" kind="tool" vertical={vertical} fontSize={fontSize} />
      <BackendNames layout={layout} backends={backends} />
      <Box area={layout.formalTrace} text="Formal trace" kind="artifact" vertical={false} fontSize={fontSize} />
      <Box area={layout.semantifyrTrace} text="Semantifyr trace" kind="artifact" vertical={false} fontSize={fontSize} />
      <Box area={layout.trace} text="Trace" kind="artifact" vertical={false} fontSize={fontSize} />
      {[...layout.forward, ...layout.backward].map(([x1, y1, x2, y2]) => {
        return <line key={`${String(x1)} ${String(y1)} ${String(x2)} ${String(y2)}`} className={styles.arrow} x1={x1} y1={y1} x2={x2} y2={y2} markerEnd={`url(#${marker})`} />;
      })}
      <path className={styles.arrow} d={layout.libraryPath} fill="none" markerEnd={`url(#${marker})`} />
      {layout.refines.map(([x1, y1, x2, y2]) => {
        const horizontal = y1 === y2;
        return (
          <g key={`${String(x1)} ${String(y1)}`}>
            <line className={styles.refines} x1={x1} y1={y1} x2={x2} y2={y2} markerEnd={`url(#${refinesMarker})`} />
            <text className={styles.refinesLabel} x={horizontal ? (x1 + x2) / 2 : x1 + 10} y={horizontal ? y1 - 9 : (y1 + y2) / 2} fontSize={fontSize * (horizontal ? 0.7 : 0.86)} textAnchor={horizontal ? 'middle' : 'start'} dominantBaseline={horizontal ? 'auto' : 'middle'}>
              refines
            </text>
          </g>
        );
      })}
    </svg>
  );
}

export default function Architecture(): React.ReactNode {
  const backends = useBackends();
  return (
    <figure className={styles.picture}>
      <Figure layout={DESKTOP} backends={backends} />
      <Figure layout={PHONE} backends={backends} />
      <figcaption className={styles.backends}>
        {'The backends: '}
        {backends.map((backend, index) => {
          return (
            <span key={backend.name}>
              {index === 0 ? '' : ', '}
              <Link to={backend.page}>{backend.name}</Link>
            </span>
          );
        })}
        .
      </figcaption>
    </figure>
  );
}
