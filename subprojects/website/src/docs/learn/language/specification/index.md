---
title: Semantifyr Language Specification
sidebar_label: Overview
sidebar_position: 0
description: The specification of the Semantifyr Language, the language Semantifyr's semantics are written in, with its constructs at a glance, its two evaluation phases and the conventions of these pages.
---

import ServedExamples from '@site/src/components/ServedExamples';

# Semantifyr Language Specification

The Semantifyr Language is the **declarative-operational** language of Semantifyr, in which the [semantic libraries](../../index.mdx) of the framework and the models verified on them are written. A Semantifyr model is a collection of classes, features, transitions, and properties that the compiler unfolds into a flat transition system and hands to a backend, the model checker that verifies it.

These pages specify the language, one construct a page, for lookup in any order; the [lessons](../lessons/index.md) teach it, and [How a Semantifyr Model Compiles](../compilation/index.mdx) shows what the compiler makes of a model. [What Is Supported](library-and-output/supported.mdx) lists the constructs the compiler supports, rejects or plans.

## The Pages

- Foundations: [Lexical Structure](foundations/lexical-structure.md), [Packages and Imports](foundations/packages-and-imports.md), [Datatypes](foundations/datatypes.md), [Types](foundations/types.md), [Expressions](foundations/expressions.md).
- Classes and Behaviour: [Classes](classes-and-behaviour/classes.md), [Variables](classes-and-behaviour/variables.md), [Features](classes-and-behaviour/features.md), [Properties](classes-and-behaviour/properties.md), [Transitions](classes-and-behaviour/transitions.md), [Operations](classes-and-behaviour/operations.md), [Time](classes-and-behaviour/time.md), [Inline Operations](classes-and-behaviour/inline-operations.md).
- Library and Output: [Annotations](library-and-output/annotations.md), [Standard Library](library-and-output/standard-library.md), [Inlined Models](library-and-output/inlined-models.md), [What Is Supported](library-and-output/supported.mdx).

## The Language at a Glance

The language borrows familiar object-oriented vocabulary, but every concept has a precise role in the verification pipeline:

- [**Classes**](classes-and-behaviour/classes.md) describe parts of the modeled system. They contain [variables](classes-and-behaviour/variables.md) (state), [features](classes-and-behaviour/features.md) (structure), [transitions](classes-and-behaviour/transitions.md) (behavior), and [properties](classes-and-behaviour/properties.md) (queries).
- [**Variables**](classes-and-behaviour/variables.md) hold *runtime* values tracked by the model checker.
- [**Features**](classes-and-behaviour/features.md) describe *structural* relationships. 
- [**Transitions**](classes-and-behaviour/transitions.md) describe how state changes from step to step. 
- [**Time**](classes-and-behaviour/time.md) passes before every main step and is read through clocks.
- [**Properties**](classes-and-behaviour/properties.md) are pure functions returning a value. 
- [**Inline operations**](classes-and-behaviour/inline-operations.md) are *compile-time* macros that expand library behavior across the structure of a concrete model.
- [**Lexical structure**](foundations/lexical-structure.md) gives the names, comments and literals, and [**Types**](foundations/types.md) the type of every expression and when a value fits where it is used.
- [**Inlined models**](library-and-output/inlined-models.md) are the form in which the compiler writes a compiled case and a witness.

## Two Evaluation Phases

A pervasive distinction throughout the language is **compile-time vs runtime**. Some expressions are evaluated **once**, while the compiler [unfolds a case](../compilation/index.mdx): feature multiplicities and bindings, the guards and ranges of `inline` constructs, the arguments of annotations, and the initializers of the variables of classes and records, which give each variable its value before `init` runs. The rest, transition bodies, ordinary `if` and `for`, local variables and properties, is evaluated by the **backend** as it explores the state space. Where the compiler needs a value, the validator rejects an expression that reads the state. Knowing which phase a piece of code lives in is essential for reading and writing Semantifyr models.

## Conventions

- Code samples are tagged with the `oxsts` language and coloured by the grammar Live and the VS Code extension load.
- In syntax descriptions, angle brackets denote placeholders, for example `<expression>`.
- The [`semantifyr` standard library](library-and-output/standard-library.md) (`Anything`, the primitive datatypes, the standard annotations) is implicitly imported into every file of the language.
- Language keywords (`tran`, `inline`, `redefine`, and so on) are written in `code` font; conceptual terms (transition, feature, property) are written in plain text.
- Page links use relative paths so they work both from the rendered site and inside an editor.

## What This Reference Does Not Cover

- The **internals** of the compiler, which the [architecture](../../../develop/index.md) sketches and the code holds.
- The **frontends** that translate SysML v2 and Gamma models into the language, each with its part of Learn: [SysML v2](../../frontends/sysml-v2/index.mdx) and [Gamma](../../frontends/gamma/index.mdx).
- What a **backend** does with a construct, which each [backend's page](../../backends/index.mdx) states, and the input languages of the backends.

## Examples in Live

The Semantifyr model Live serves beside the lessons' models, opening in Live by its own address:

<ServedExamples />
