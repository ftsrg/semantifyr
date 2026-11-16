---
title: 'Traffic Light'
sidebar_position: 0
description: The lessons of the Semantifyr Language, on a junction of two traffic lights, a pedestrian crossing and a small statechart library.
---

# Traffic Light

These lessons teach the Semantifyr Language on one running example: a junction of two traffic lights, later with a pedestrian crossing and a small statechart library they are written on. You build the models yourself in Live, and each state of a lesson can be opened by its link. The lessons are meant to be read in order.

1. [The Intersection](intersection.md): model the junction directly, write its requirements as verification cases, and find and fix a flaw that the obvious guard leaves.
2. [A Statechart Library](library.md): write the traffic lights and a pedestrian light as data on a library of states and transitions, and check the crossing.
3. [Changing the Semantics](semantics.md): write once, in the library, which transition fires in a step, change that rule, and see the verdicts of the same lights change.
4. [The Minimum Green Time](green-time.md): keep a light green for at least three steps with an integer counter, checked by a case that counts on its own.
5. [Hierarchy, Events and the Environment](hierarchy.md): nested states, entry actions, events sent by the environment, and an emergency that turns a junction red.
