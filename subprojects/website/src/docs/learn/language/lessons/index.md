---
title: 'Lessons'
sidebar_position: 0
description: The lessons of the Semantifyr Language, on a junction of two traffic lights, a crosswalk and a small statechart library they are written on.
---

# Lessons

These lessons teach the Semantifyr Language on one running example: a junction of two traffic lights and the cars that arrive at it, later a crosswalk with a pedestrian light, and a small statechart library that they are written on. You build the models yourself in Live, and each state of a lesson can be opened from its page. The lessons are meant to be read in order.

1. [Modeling and Verification](modeling-and-verification.md): model the junction directly, write its requirements as verification cases, and find and fix a flaw that the obvious guard leaves.
2. [Semantic Libraries](semantic-libraries.md): write the lights as data on a library that says once how a statechart runs, and reuse it for a crosswalk.
3. [Semantic Variants](semantic-variants.md): move into the library how the parts of a system step, change that rule as statechart languages differ on it, and see the same junction break its requirements.
4. [Extending the Library](extending-the-library.md): give the library time, so that a light says as data that it stays green for at least three steps, and grow it further with nested states and events.
