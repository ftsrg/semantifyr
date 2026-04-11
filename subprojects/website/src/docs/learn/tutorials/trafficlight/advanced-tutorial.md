---
title: 'Advanced Tutorial'
sidebar_position: 3
---

# Advanced Tutorial

:::info Coming soon
This advanced tutorial is still being written. It will pick up where [Building a Library](building-a-library.md) leaves off and walk through the patterns that real semantic libraries use to encode richer behavior.
:::

## What this tutorial will cover

The advanced tutorial extends the small `Statechart` library from the previous chapter with the patterns you find in the production Gamma and SysML v2 libraries shipped with Semantifyr:

- **Hierarchical states**: composite states with substates, entry into the correct innermost state, and the bookkeeping needed to keep parents and children consistent.
- **Entry and exit actions**: running effects when a state becomes active or is left, including ordering with respect to transition firing.
- **Events and triggers**: declaring discrete events, queuing them, and firing transitions only when their trigger is present.
- **Top-level overrides**: a global emergency mode that pre-empts the local state machine without changing its definition.
