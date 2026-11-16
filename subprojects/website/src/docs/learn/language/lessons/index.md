---
title: 'Traffic Light'
sidebar_position: 0
description: The lessons of the Semantifyr Language, on a traffic light, an intersection of two and a small statechart library, and what you verify in each lesson.
---

# Traffic Light

These lessons teach the Semantifyr Language, Semantifyr's language for semantics, on one example: a traffic light, an intersection of two, and a small statechart library they are written on. In each lesson you build the steps yourself, in Live in the place of a code block, and each step has a solution to open. They are meant to be read in order.

1. [The Intersection](intersection.md): you model an intersection directly, verify that its two lights are never green together, and find with a second requirement what the obvious guard lets through.
2. [A Statechart Library](library.md): you write a pedestrian light as data on a library of states and transitions, and verify a crossing where pedestrians walk only while the traffic light is red.
3. [Changing the Semantics](semantics.md): you write once, in the library, which transition fires in a step, change that rule, and see the verdicts of untouched lights change.
4. [The Minimum Green Time](green-time.md): you verify with an integer counter and a case that counts on its own that a light stays green for at least three steps.
5. [Hierarchy, Events and the Environment](hierarchy.md): you verify a light with nested states, entry actions and events sent by its environment, and an emergency that turns an intersection red.
