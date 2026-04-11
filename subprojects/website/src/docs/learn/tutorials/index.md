---
title: Tutorials
sidebar_position: 0
---

# Tutorials

Guided walk-throughs of Semantifyr. The Traffic Light series builds a small state machine from scratch and progressively introduces the language constructs you will need to read and write real semantic libraries.

If you are new to Semantifyr, start with [Direct Modeling](trafficlight/direct-modeling.md). Each tutorial can be followed directly in the [live editor](https://live.semantifyr.org) with no setup required.

## Traffic Light series

| Tutorial | Description |
|----------|-------------|
| [Direct Modeling](trafficlight/direct-modeling.md) | Build a basic traffic light and intersection directly, with no reusable abstractions. Discover and fix a safety bug. Introduces classes, transitions, properties, `choice`/`assume`, containment, `@VerificationCase`, `AG`/`EF`. |
| [Building a Library](trafficlight/building-a-library.md) | Refactor the traffic light into a small `Statechart` library with first-class `State` and `Transition` classes. Introduces single inheritance, `container`/`opposite`, `subsets`, feature-typed variables, `nothing`, `inline for choice`, and the init-chain pattern. |
| [Advanced Tutorial](trafficlight/advanced-tutorial.md) | *Coming soon.* Extend the small library with patterns from real semantic libraries: hierarchical states, entry actions, events with triggers, and a global emergency override. |
