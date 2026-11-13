---
title: Standard Library
sidebar_position: 14
---

import LibraryDeclaration from '@site/src/components/LibraryDeclaration';
import library from '@oxsts-library/semantifyr.oxsts';

# Standard Library

The `semantifyr` package is implicitly available in every Semantifyr file. It defines the built-in datatypes, the `Anything` base class, and the standard annotations.

## Extern Datatypes

| Type | Description |
|------|-------------|
| `any` | Universal supertype of all types |
| `int` | Integer numbers |
| `real` | Real numbers |
| `bool` | Boolean values |
| `string` | Text values |

## The `Anything` Class

Implicit supertype of all classes. Provides empty base transitions:

<LibraryDeclaration library={library} name="Anything" />

Every class you write inherits these. Override them with `redefine`.

## Annotations

### `@VerificationCase`

<LibraryDeclaration library={library} name="VerificationCase" />

Marks a class as a verification target. The optional `summary` parameter describes what the case checks.

### `@Tag`

<LibraryDeclaration library={library} name="Tag" />

Categorizes verification cases. For example, `@Tag(category = "slow")` marks long-running cases. The repository's conformance suites read `@Tag("expect-pass")` and `@Tag("expect-fail")` on a case as its expected verdict; which backends run a case is the suites' choice, not the model's.

### `@Control`

<LibraryDeclaration library={library} name="Control" />

Signals that a variable is important to track during verification. May affect how the model checker reports counterexamples.

### `@Shared`

<LibraryDeclaration library={library} name="Shared" />

Instances contained in a `@Shared` containment feature are shared across all instances of the containing class, rather than duplicated per instance.

### `@Trace`

<LibraryDeclaration library={library} name="Trace" />

Calls to `@Trace`-annotated transitions are recorded in the verification witness, including parameter bindings at each call site. The bindings are evaluated at compile time, so every argument of a traced call must be evaluable at compile time (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`) and none may be a record (`OXSTS.UNSUPPORTED_FEATURE`). Only the calls that fired are listed, each with the receiver's feature path from the root instance (`root.kitchen.lamp`). The witness files `trace.json` and `witness.json` hold `initializedStep` (the initial state, the one `init` produces, with the calls traced during it) and `steps`, one per step of the main transition. The SysML v2 library traces a minimal set: transition firing, item sending, and the time advance at the root. The Gamma library traces transition firing, events raised on ports in either direction, and the time advance at the root, and the Gamma frontend writes the trace back as a Gamma execution trace, `trace.gamma` next to the witness: one `step` per witness step whose `act` block raises the input events, schedules the component and lets one second elapse, and whose `assert` block lists the fired transitions (by source and target state, with the trigger when that is ambiguous), the raised events, the active leaf states and the variable values, each referenced by its path from the verification case's component instance (`system.controller.Main.Operating`).

### `@Clock`

<LibraryDeclaration library={library} name="Clock" />

Marks a variable as a clock. Like every variable, a clock carries an initializer.

### `@NonOptimizable`

<LibraryDeclaration library={library} name="NonOptimizable" />

Excludes a variable from optimizer passes that could remove or fold it.
