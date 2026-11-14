---
title: The OXSTS Language
sidebar_label: Overview
sidebar_position: 0
description: The specification of OXSTS, Semantifyr's language for semantics, with its constructs at a glance, its two evaluation phases and the conventions of these pages.
---

import ServedExamples from '@site/src/components/ServedExamples';

# The OXSTS Language

OXSTS is the **declarative-operational** language of Semantifyr, in which the [semantic libraries](../../start/index.mdx) of the framework and the models verified on them are written. An OXSTS model is a collection of classes, features, transitions, and properties that the compiler unfolds into a flat transition system and hands to a backend, the model checker that verifies it.

These pages specify OXSTS, one construct a page, for lookup in any order; the [OXSTS lessons](../../lessons/index.md) teach it, and [How the Language Works](../../how-it-works/index.mdx) shows what the compiler makes of a model. [Status](status.mdx) lists the constructs the compiler supports, rejects or plans.

## The Language at a Glance

OXSTS borrows familiar object-oriented vocabulary, but every concept has a precise role in the verification pipeline:

- [**Classes**](classes.md) describe parts of the modeled system. They contain [variables](variables.md) (state), [features](features.md) (structure), [transitions](transitions.md) (behavior), and [properties](properties.md) (queries).
- [**Variables**](variables.md) hold *runtime* values tracked by the model checker.
- [**Features**](features.md) describe *structural* relationships. 
- [**Transitions**](transitions.md) describe how state changes over time. 
- [**Properties**](properties.md) are pure functions returning a value. 
- [**Inline operations**](inline-operations.md) are *compile-time* macros that expand library behavior across the structure of a concrete model.
- [**Lexical structure**](lexical-structure.md) gives the names, comments and literals, and [**Types**](types.md) the type of every expression and when a value fits where it is used.
- [**Inlined models**](inlined-models.md) are the form in which the compiler writes a compiled case and a witness.

## Two Evaluation Phases

A pervasive distinction throughout the language is **compile-time vs runtime**. Some expressions are evaluated **once**, while the compiler [unfolds a case](../../how-it-works/index.mdx): feature multiplicities and bindings, the guards and ranges of `inline` constructs, the arguments of annotations, and the initializers of the variables of classes and records, which give each variable its value before `init` runs. The rest, transition bodies, ordinary `if` and `for`, local variables and properties, is evaluated by the **backend** as it explores the state space. Where the compiler needs a value, the validator rejects an expression that reads the state (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`). Knowing which phase a piece of code lives in is essential for reading and writing OXSTS.

## Conventions

- Code samples are tagged with the `oxsts` language and coloured by the grammar Live and the VS Code extension load.
- In syntax descriptions, angle brackets denote placeholders, for example `<expression>`.
- The [`semantifyr` standard library](standard-library.md) (`Anything`, the primitive datatypes, the standard annotations) is implicitly imported into every OXSTS file.
- Language keywords (`tran`, `inline`, `redefine`, and so on) are written in `code` font; conceptual terms (transition, feature, property) are written in plain text.
- Page links use relative paths so they work both from the rendered site and inside an editor.

## What This Reference Does Not Cover

- The **internals** of the compiler, which the [architecture](../../../develop/index.md) sketches and the code holds.
- The **frontends** that translate SysML v2 and Gamma models into OXSTS, each with its part of Learn: [SysML v2](../../sysml-v2/index.mdx) and [Gamma](../../gamma/index.mdx).
- What a **backend** does with a construct, which each [backend's page](/learn/reference/backends) states, and the input languages of the backends.

## Examples in Live

The OXSTS model Live serves beside the lessons' models, opening in Live by its own address:

<ServedExamples />
