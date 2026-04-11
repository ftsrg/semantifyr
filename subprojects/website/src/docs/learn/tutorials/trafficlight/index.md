---
title: 'Traffic Light'
sidebar_position: 0
---

# Traffic Light

This tutorial series explores how to model the same example system, a small traffic-light intersection, in three different ways. Each tutorial builds the same model with the same verification cases, but uses progressively more abstract Semantifyr features to do so. The goal is to show how Semantifyr supports a smooth migration path from a directly-encoded model to a library-based one.

The tutorials share the same example so you can compare the encodings side by side. They are designed to be read in order, but each one is self-contained and starts from a fresh file.

:::tip Try it live
You can follow along in the [live editor](https://live.semantifyr.org) : a lightweight in-browser editor with syntax highlighting, diagnostics, content assist, and an integrated verification panel. Each tutorial's **Snapshot** section links directly to a pre-loaded version you can edit and verify immediately.
:::

## Tutorials

### 1. [Direct Modeling](direct-modeling.md)

The first tutorial encodes the traffic light directly: a single class with an enum, a variable, and a hand-written `redefine tran` body. There are no reusable abstractions; the entire state machine lives inside one class. This tutorial introduces the basic Semantifyr language constructs: packages, enums, classes, variables, transitions, properties, features, and verification cases.

### 2. [Building a Library](building-a-library.md)

The second tutorial refactors the traffic light into a small reusable library: a `Statechart` base class with first-class `State` and `Transition` instances. The concrete traffic light becomes a thin specialization of `Statechart`. This tutorial introduces single inheritance, the container-opposite pattern, the `subsets` modifier, feature-typed variables, the `nothing` literal, `inline for choice`, and the init-chain pattern.

### 3. [Advanced Tutorial](advanced-tutorial.md) *(coming soon)*

The third tutorial extends the small library with patterns that real semantic libraries use: hierarchical states, entry actions, events with triggers, and a global emergency override. Each pattern adds one capability to the existing `State`, `Transition`, or `TrafficLight` class without disturbing the others. The page is currently a placeholder while the content is being written.
