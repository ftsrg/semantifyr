---
title: 'Traffic Light'
sidebar_position: 0
---

# Traffic Light

This tutorial series models the same example system, a small traffic-light intersection, with progressively more abstract Semantifyr features. The first two tutorials build the same model with the same verification cases, once directly and once on top of a small library, to show how Semantifyr supports a smooth migration path from a directly-encoded model to a library-based one. The third extends that library with the patterns real semantic libraries use.

The tutorials share the same example so you can compare the encodings side by side. They are designed to be read in order, but each one is self-contained and starts from a fresh file.

:::tip Try it live
Every step of a tutorial ends in a complete model that you can open in a live editor embedded in the page: a lightweight in-browser editor with syntax highlighting, diagnostics, content assist, and the verification cases with their verdicts and traces beside it. Each tutorial's **Snapshot** section holds the finished model.
:::

## Tutorials

### 1. [Direct Modeling](direct-modeling.md)

The first tutorial encodes the traffic light directly: a single class with an enum, a variable, and a hand-written `redefine tran` body. There are no reusable abstractions; the entire state machine lives inside one class. This tutorial introduces the basic Semantifyr language constructs: packages, enums, classes, variables, transitions, properties, features, and verification cases.

### 2. [Building a Library](building-a-library.md)

The second tutorial refactors the traffic light into a small reusable library: a `Statechart` base class with first-class `State` and `Transition` instances. The concrete traffic light becomes a thin specialization of `Statechart`. This tutorial introduces inheritance, the container-opposite pattern, the `subsets` modifier, feature-typed variables, the `nothing` literal, `inline for choice`, and the init-chain pattern.

### 3. [Advanced Tutorial](advanced-tutorial.md)

The third tutorial extends the small library with patterns that real semantic libraries use: hierarchical states, entry actions, and events with triggers, followed by a global emergency override. Hierarchy and entry actions extend `State`, triggers extend `Transition`, and the emergency override lives in the intersection, which pre-empts the traffic lights without changing their definition. This tutorial introduces composite states, `inline if`, `State` subclasses for per-state behavior, `redefine contains`, `havoc` for modeling the environment, and global features.
