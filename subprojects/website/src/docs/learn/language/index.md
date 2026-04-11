---
title: Overview
sidebar_position: 0
---

# Language Reference

Semantifyr is a **declarative-operational** language for writing the [semantic libraries](../index.md) used by the verification framework. A Semantifyr model is a collection of classes, features, transitions, and properties that the compiler unfolds into a flat XSTS (Extended Symbolic Transition System) model and hands to a model checker such as Theta.

This is the **language reference**. Each page covers a single construct and is designed for **quick lookup**. Pages are self-contained and may be read in any order. For a guided walk-through of a complete Semantifyr workflow, see the [Traffic Light tutorials](../tutorials/trafficlight/) instead.

## The Language at a Glance

Semantifyr borrows familiar object-oriented vocabulary, but every concept has a precise role in the verification pipeline:

- [**Classes**](classes.md) describe parts of the modeled system. They contain [variables](variables.md) (state), [features](features.md) (structure), [transitions](transitions.md) (behavior), and [properties](properties.md) (queries).
- [**Variables**](variables.md) hold *runtime* values tracked by the model checker.
- [**Features**](features.md) describe *structural* relationships. 
- [**Transitions**](transitions.md) describe how state changes over time. 
- [**Properties**](properties.md) are pure functions returning a value. 
- [**Inline operations**](inline-operations.md) are *compile-time* macros that expand library behavior across the structure of a concrete model.

## Two Evaluation Phases

A pervasive distinction throughout the language is **compile-time vs runtime**. Some constructs (feature multiplicities, feature bindings, the guards and ranges of `inline` constructs) are evaluated **once**, during model unfolding. Others (variable initializers, transition bodies, ordinary `if` and `for`) are evaluated by the **model checker** as it explores the state space. Knowing which phase a piece of code lives in is essential for reading and writing Semantifyr.

## Conventions

- Code samples are tagged with the `oxsts` language and use a syntax highlighter that mirrors the VS Code Semantifyr extension.
- In syntax descriptions, angle brackets denote placeholders, for example `<expression>`.
- The [`semantifyr` standard library](standard-library.md) (`Anything`, the primitive datatypes, the standard annotations) is implicitly imported into every Semantifyr file.
- Language keywords (`tran`, `inline`, `redefine`, and so on) are written in `code` font; conceptual terms (transition, feature, property) are written in plain text.
- Page links use relative paths so they work both from the rendered site and inside an editor.

## What This Reference Does Not Cover

- The **internals** of the Semantifyr compiler. See the [Developer Guide](../../develop/index.md).
- The **frontends** that produce Semantifyr code from higher-level languages such as Gamma or SysML v2. They live in the [Semantifyr repository](https://github.com/ftsrg/semantifyr).
- The **XSTS formalism** itself or model-checking algorithms.
