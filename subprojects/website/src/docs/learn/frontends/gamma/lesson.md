---
title: 'Lesson: A Level Crossing'
sidebar_label: 'Lesson: A Level Crossing'
sidebar_position: 2
description: Read the Gamma level crossing, verify its two cases, and read the trace of the one that fails.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import levelCrossing from '@served-examples/gamma/level-crossing';

# Lesson: A Level Crossing

This lesson reads the level crossing that Live serves, verifies its two cases, and reads the trace of the one that fails.

## The Model

Two tracks cross a road at one barrier. Each track reports its trains to a controller, and the controller tells the barrier to go down and up. The events they exchange are declared by three interfaces:

<LiveEmbed example={levelCrossing} show={['Sensors', 'TrackReports', 'BarrierCommands']} />

A track follows a train from `Free` through `Approaching` and `Near` to `InCrossing` on its sensors' events. It reports `trainApproaching` when a train approaches and `trainLeft` when it leaves:

<LiveEmbed example={levelCrossing} show={['Track']} />

The controller lowers the barrier on the first report of an approach, from either track, and lifts it on the first report that a train left:

<LiveEmbed example={levelCrossing} show={['Controller', 'Barrier']} />

The synchronous composite `LevelCrossing` holds a statechart of each kind, two tracks among them, binds the tracks' sensors to its own ports and connects the reports and the commands by channels:

<LiveEmbed example={levelCrossing} show={['LevelCrossing']} />

The model runs in steps. In each step the environment may raise the sensors' events, every statechart runs once, and the events sent over channels arrive in the next step. [Semantic Decisions](semantic-decisions.mdx) states the full semantics.

## Verification Cases

A verification case names its component and holds its property. The first case claims that no train is ever in the crossing while the barrier is up; `AG` states that every reachable state satisfies the condition:

<LiveEmbed example={levelCrossing} show={['BarrierDownWhileTrainInCrossing']} />

The second asks whether trains on both tracks can be in the crossing at once; `EF` states that some run reaches the condition:

<LiveEmbed example={levelCrossing} show={['TwoTrainsCanBeInTheCrossing']} />

Before you run the first, think about whether it holds.

## Verifying

Press **Verify all** in any of the editors above; each verifies the whole model.

To verify it on your machine, save the model as `LevelCrossing.gamma` and run `semantifyr-gamma verify LevelCrossing.gamma`.

## Reading the Trace

A case has a trace when a single run proves its verdict: a failed `AG` or a passed `EF` case. Which run a trace shows depends on the backend that answered. In most steps of the run below the environment raises several sensor events at once, and a track takes the one its state has a transition for. The trace of `BarrierDownWhileTrainInCrossing` has seven steps:

1. Initialization: both tracks are `Free`, the controller is `Idle`, and the barrier is `Up`.
2. Trains approach on both tracks, and both tracks report `trainApproaching`.
3. Both trains are `Near`. The controller takes track A's report, moves to `TrainPresent`, and raises `lower`. Track B's report is lost, since a statechart's input events are reset after it runs.
4. Both trains enter the crossing, and the barrier goes `Down`.
5. Track A's train leaves, and track A reports `trainLeft`.
6. The controller takes track A's report, moves to `Idle`, and raises `lift`. A new train approaches on track A.
7. The barrier takes the `lift` and goes `Up` while track B's train is still in the crossing. The controller takes track A's new report and raises `lower`; the trace ends with the barrier `Up`.

The case fails because the controller follows one train at a time: it loses track B's report and lifts the barrier when track A's train leaves, with track B's train still in the crossing. Whether that is a defect of the design or of the requirement is for the engineer to decide.

The *Gamma trace* view shows the same run as a Gamma execution trace ([Gamma traces](traces.mdx)); on your machine it is `trace.get`, next to the case's report.
