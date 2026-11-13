---
title: Overview
sidebar_position: 0
---

# Language Reference

Semantifyr is a **declarative-operational** language for writing the [semantic libraries](../index.md) used by the verification framework. A Semantifyr model is a collection of classes, features, transitions, and properties that the compiler unfolds into a flat transition system and hands to a backend, the model checker that verifies it.

This is the **language reference**. Each page covers a single construct and is designed for **quick lookup**. Pages are self-contained and may be read in any order. For a guided walk-through of a complete Semantifyr workflow, see the [Traffic Light tutorials](../tutorials/trafficlight/) instead. [Status](status.mdx) lists the constructs the compiler supports, rejects or plans.

## The Language at a Glance

Semantifyr borrows familiar object-oriented vocabulary, but every concept has a precise role in the verification pipeline:

- [**Classes**](classes.md) describe parts of the modeled system. They contain [variables](variables.md) (state), [features](features.md) (structure), [transitions](transitions.md) (behavior), and [properties](properties.md) (queries).
- [**Variables**](variables.md) hold *runtime* values tracked by the model checker.
- [**Features**](features.md) describe *structural* relationships. 
- [**Transitions**](transitions.md) describe how state changes over time. 
- [**Properties**](properties.md) are pure functions returning a value. 
- [**Inline operations**](inline-operations.md) are *compile-time* macros that expand library behavior across the structure of a concrete model.
- [**Lexical structure**](lexical-structure.md) gives the names, comments and literals, and [**Types**](types.md) the type of every expression and when a value fits where it is used.
- [**Inlined models**](inlined-models.md) are the form in which the compiler writes a compiled case and a witness.

## Two Evaluation Phases

A pervasive distinction throughout the language is **compile-time vs runtime**. Some expressions are evaluated **once**, while the compiler unfolds a case: feature multiplicities and bindings, the guards and ranges of `inline` constructs, the arguments of annotations, and the initializers of the variables of classes and records, which give each variable its value before `init` runs. The rest, transition bodies, ordinary `if` and `for`, local variables and properties, is evaluated by the **backend** as it explores the state space. Where the compiler needs a value, the validator rejects an expression that reads the state (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`). Knowing which phase a piece of code lives in is essential for reading and writing Semantifyr.

## Conventions

- Code samples are tagged with the `oxsts` language and coloured by the grammar Live and the VS Code extension load.
- In syntax descriptions, angle brackets denote placeholders, for example `<expression>`.
- The [`semantifyr` standard library](standard-library.md) (`Anything`, the primitive datatypes, the standard annotations) is implicitly imported into every Semantifyr file.
- Language keywords (`tran`, `inline`, `redefine`, and so on) are written in `code` font; conceptual terms (transition, feature, property) are written in plain text.
- Page links use relative paths so they work both from the rendered site and inside an editor.

## What This Reference Does Not Cover

- The **internals** of the Semantifyr compiler. See the [Developer Guide](../../develop/index.md).
- The **frontends** that produce Semantifyr code from higher-level languages such as Gamma or SysML v2. They live in the [Semantifyr repository](https://github.com/ftsrg/semantifyr).
- The input languages of the model checkers or model-checking algorithms.
