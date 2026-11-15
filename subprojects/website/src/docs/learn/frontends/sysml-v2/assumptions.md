---
title: Semantic Assumptions
sidebar_position: 2
description: What the SysML v2 frontend assumes of a model's meaning, the step, the assumptions, the variants of its library and what a verdict covers.
---

import { SysmlConstructsOf } from '@site/src/components/SysmlConstructs';
import constructs from '@sysml-constructs/constructs.tsv';

# SysML v2's Semantic Assumptions

Semantifyr verifies a SysML v2 model against its SysML v2 semantic library, an abstraction of the standard at the level of states: parts and the regions of a parallel state run concurrently, actions are atomic and instantaneous, expressions are instant and side-effect free, items are signals and time is discrete. Where SysML v2 and KerML decide, the library follows them, except in the constructs the table of [What Is Supported](supported.mdx) calls deviating and in its `fifo` variant. Where an assumption below names an outcome, it is the outcome of the table's rows, in the sense the table's legend gives the six; where it names its enforcement today, that is a diagnostic of the translator, the library itself, or a convention the model has to keep.

## The Step

A verification case runs the verified part in steps. In each step the environment may put an item on every port of the verified part, every part and every region of a parallel state with an enabled transition fires one (maximal progress), the flows move the items sent in the step to their receiving ports, and time advances by one tick. A reaction takes no time.

Initialization builds the part tree, sets the initial values of the attributes and enters every exhibited state: its entry action, then its initial descent. A guarded initial transition is chosen among those whose guard is open. No transition fires and no input arrives during initialization, and the items sent by its entry actions arrive in the first step.

## Assumptions

### Parts and Composition

- The part tree is fixed and finite, built at initialization and alive for the whole run; parts react concurrently in the step, the sub-parts of a part in textual order. Abstraction for the lifetimes. A usage with multiplicity `[0]` is absent and a literal `[n]` on a part or port is n instances, both conformant; `[0..1]` and a literal `[n]` on an attribute are planned; creation and destruction and every other multiplicity are unsupported. A planned or an unsupported multiplicity is a diagnostic. The translator does not read `individual`, `snapshot` or `timeslice` on a part usage and translates such a part as any other.
- Parts share nothing: a part's guards, effects, entry and exit actions read and write only its own attributes and the items on its own ports; a verification case, a requirement or a constraint reads across the whole subject. A reference to and an assignment to the part's own attribute are conformant; a reference to another part's attribute outside a verification case or a requirement is unsupported, a diagnostic.

### Exhibited States

- A part exhibits at most one state, owned by the part. It is performed from the part's initialization until it reaches `done`, which ends its performance and leaves the part idle. Planned for several exhibited states, abstraction for the start of the performance. An owned, a referenced (`exhibit s;`) or a definition-typed exhibit state is its exhibited state, and so is a part's plain `state`, a deviation, since SysML v2 performs only exhibited states; a second one is a diagnostic.

### States, Regions and Parallelism

- The active configuration is a tree. A transition leaves and enters the states below the closest common owning state of its source and target, whichever state declares it. Exit actions run bottom-up from the source, then the sibling regions of a parallel state being left, then the parallel state's own exit action, then the effect; entry actions run top-down, a parallel state's entry action first, then each sibling region's entry action and initial descent, then the target's region down to the target. The standard orders only the source's exit before the effect and the target's entry after it. Abstraction. Library.
- A region's entry action runs when the region is activated and its exit action when it is left. A parallel state that is not active runs no exit action when its parent is left. Library.
- Every region of a parallel state reacts in the same step with at most one transition. The regions of one part read and write the part's attributes in textual order, each seeing the writes of the previous one, and two regions assigning one attribute draw a warning. Abstraction. Library.
- Among the enabled transitions of one state the choice is nondeterministic. A transition declared in a substate fires before one declared in an enclosing state; the `topdown` variant reverses this. The standard does not fix the priority between nesting levels. Abstraction. Library.
- A region reaching `done` stays in it with no active substate. A composite state without an initial transition is a diagnostic.

### Transitions

- A firing is atomic within the step: trigger and guard are read, then the exits, the effect, the entries and the initial descent run at zero duration. The target's transitions are enabled from the next step, so a chain of untriggered transitions takes one step per transition. Abstraction for a transition observed in progress and compound transitions; unsupported for decision, terminate and action targets. A transition to `done` is conformant; a source or a target other than a state of the model or `done` is a diagnostic.
- A trigger on an initial transition is unsupported, a diagnostic, and an initial transition whose guard is the literal `false` is a diagnostic, since it can never be taken. A guard closed only at runtime on every initial transition blocks initialization, which makes every verdict vacuous, by convention.

### Entry, Exit and Do Actions

- Actions are atomic and instantaneous and complete inside the step that starts them; the children of a composite action run one after another in an order consistent with its successions, in textual order where they set none. Abstraction. Do actions, accept actions outside triggers and control nodes are planned, loops unsupported, each a diagnostic.

### Items and Ports

- Items are signals. A send puts the item on the sending port's outgoing slot, the flows move it at the end of the step, and the receiving port holds it for the next step only: an item not accepted in that step is dropped, and the last of several transfers into one port in a step is kept. Abstraction (see [below](#the-dropped-item)). Library.
- An item fires at most one transition per part per step. The regions of a parallel state compete for it nondeterministically, and a step never blocks because another region consumed the item. Abstraction. Library.
- Ports and flows are untyped: a port has one incoming and one outgoing slot, its `in` and `out` item usages merged, an abstraction, and a port's item types, its conjugation and a flow's item type are not read, three deviations stated [below](#untyped-ports-and-flows). Every send and accept names its port with `via`, which is a diagnostic otherwise; a send's `to` is a diagnostic. Item payloads are planned.
- An item has no identity across hops and no payload: every item of one item definition is the same value.

### Attributes and Expressions

- An attribute is a variable of its part, changed only by assignments within the step, typed Boolean or unbounded Integer. `:=` and `default :=` give its initial value; `=` and `default` bind it, by KerML's feature values, so it is derived and never assigned, an assignment to it a diagnostic; an attribute without a value starts at any value. Conformant. A literal `[n]` on an attribute is a diagnostic. Real and enumerations are planned, String and quantities unsupported, each a diagnostic.
- An expression is a pure, instantaneous evaluation on the part's state in the step. Integer and Boolean literals, the operators the table lists as conformant and a reference to the part's own attribute are conformant; `isStateActive`, `eventually` and `mustAlways` are Semantifyr's extensions; the division of two integers is an integer, where KerML's is a rational, a deviation; user calculations are unsupported.

### Time

- Time is discrete, one tick per step. `after d` fires `d` after its source state was entered: the tick is the largest duration in seconds, minutes, hours or days that divides every `after` duration of the model, one second when it has none, and `d` a count of ticks. `at` and non-literal durations are unsupported. `when c` is enabled in every step its condition holds. The standard sends its change signal when `c` turns from false to true, or at once when `c` holds at its first evaluation, and the signal stays acceptable until the source state is left, so the library fires only where a signal is pending and drops the runs that fire after `c` has turned false again. Abstraction. Library.

### Verification

- The verified part is closed: the environment may put any item on any of its ports in every step. `mustAlways` holds when the condition holds in every reachable state between steps; `eventually` passes when one run reaches the condition, under some choice of the environment. The closed subject is an abstraction, `mustAlways` and `eventually` are extensions.
- A requirement holds one `require constraint` and a verification case verifies one requirement; a case without `verify` checks its objective's own `require constraint`. `assume` and `assert constraint` in a part are unsupported and a second `require` or `verify` planned; `assume`, a second `require` or `verify`, an objective with both its own `require` and a `verify`, and a temporal operator below the root of a constraint, negations aside, are diagnostics; a constraint without a temporal operator is read as `mustAlways`, an extension.

## Untyped Ports and Flows

The library admits runs the standard excludes by three deviations, since ports and flows are untyped: an item of another type crosses a `flow of X`, where in SysML v2 the flow carries `X` and its specializations only; a port admits an item its definition does not declare; and a send against a conjugated port's direction is admitted.

## The Dropped Item

KerML's state performances never fix which arrived transfers are acceptable, so dropping an item that is not accepted in its arrival step, and keeping the last of several transfers into one port, are abstractions. The drop contradicts only the SysML prose that reads a transition as triggered whenever its accept action can accept an incoming transfer (SysML 7.18.3, 8.4.13.6), for an item still eligible within the same source state.

## Item Specialization

An item definition may specialize another with `:>`, and the translator accepts it with a warning, but an accept matches the exact item definition: an accept of a supertype drops an item of a subtype. A deviation.

## Library Variants

- `default`, the library of record.
- `topdown`: a transition declared in an enclosing state fires before one declared in a substate; everything else is as in `default`.
- `fifo`: every port queues its arrived items in arrival order across steps and state changes until a transition accepts the oldest one, up to two items per port; an item arriving at a full port is dropped and the port keeps two. This departs from the standard under its defaults, `isTriggerDuring` and `isDispatch` true, which admit it only when a model sets them false; it follows Gamma's queue semantics.

The frontend selects a variant for a whole model; the `semantifyr-sysml` command line verifies with `default`.

## What a Verdict Covers

An abstraction narrows the runs a verdict ranges over. A passed `mustAlways` and a failed `eventually` hold over the runs the library keeps, and a run the standard admits outside them may break them. A passed `eventually` and a failed `mustAlways` rest on one run the library keeps, which the standard admits too unless a deviation is involved in it: then the run may be one the standard excludes. The table's deviating constructs are these:

<SysmlConstructsOf table={constructs} outcome="deviating" />

The `fifo` variant departs from the standard as well, which the table does not show: a verdict under it may rest on a run the standard excludes under its defaults. A trace shows the transitions that fired with their triggers and guards, the items they sent and the states the parts were in, so a reader sees on it whether the run sent an item across a typed flow or port, sent against a conjugated port or ran a plain `state`; it does not show whether a division was evaluated, nor an item that an accept of its supertype left untaken. For example, `mustAlways(not isStateActive(S) or entered)`, where the entry action of `S` sets `entered`, passes because the entry action completes in the step that activates `S`; the standard has `S` active while its entry action runs, and that run breaks the invariant.
