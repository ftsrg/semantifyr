---
title: 'Traffic Light'
sidebar_position: 0
description: The OXSTS lessons, five levels on a traffic light, each cut by what you verify in it.
---

# Traffic Light

These lessons teach OXSTS, Semantifyr's language for semantics, on one example: a traffic light, then an intersection of two. Each level adds what its cases need, and each step runs in Live in the place of its code block. They are meant to be read in order.

1. [One Light](one-light.md): you verify that a light can turn yellow, and read the trace that shows how.
2. [The Intersection](intersection.md): you verify that two lights are never green together, and find with a second case a requirement the first did not cover.
3. [The Minimum Green Time](green-time.md): you verify with an integer counter and a case that counts on its own that a light stays green for at least three steps.
4. [A Statechart Library](library.md): you verify a light built on a library of states and transitions, and a crossing where pedestrians walk only while the traffic light is red.
5. [Hierarchy, Events and the Environment](hierarchy.md): you verify a light with nested states, entry actions and events sent by its environment, and an emergency that turns an intersection red.
