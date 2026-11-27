---
title: Architecture
sidebar_position: 0
hide_table_of_contents: true
description: The path of a model through Semantifyr's components and what each of them is for, for a contributor about to read the code.
---

import ComponentPath from '@site/src/components/Develop/ComponentPath';

# Architecture

<ComponentPath />

## The Path

### Frontends {#frontends}

A frontend translates a model from an engineering language into the Semantifyr Language, onto that language's semantic library. The library is a set of Semantifyr classes that say what the language's constructs mean, so the meaning is a model you can read, vary, and verify, not code inside a translator. A construct the frontend cannot translate is reported, and no model is written for it. At the end of the path, the same frontend turns the witness back into a trace in the original language. A model written by hand in the Semantifyr Language needs no frontend. [Frontends](../learn/frontends/index.mdx) lists them.

### The Compiler {#compiler}

The compiler reads a Semantifyr model and never learns which language it came from. It compiles one class, usually a verification case, through instantiation, inlining, and flattening into a flat model: variables, an `init` transition, a main transition, and a property. [How a Semantifyr Model Compiles](../learn/language/compilation/index.mdx) shows the phases, and the [Semantifyr Language Reference](../learn/language/reference/index.md) describes the language.

### The Backends {#backends}

A backend connects Semantifyr to one model checker outside it. Every backend receives the same flat model. First it refuses, construct by construct, what its model checker cannot express. Then it translates the rest into the checker's input, runs the checker, and reads back a verdict and, as evidence, the states the checker found. A backend is trusted in one direction, the one its configuration backs, or in both when the configuration is exact. [Backends](../learn/backends/index.mdx) says what each one refuses.

### The Portfolio {#portfolio}

A portfolio is a strategy for running backends: which backends run, under which configurations, side by side, and how their answers decide the verdict. Each answer counts only in the direction its backend is trusted. A case without variables and transitions is decided without a backend. [Portfolios and Verdicts](../learn/backends/portfolios-and-verdicts.mdx) explains the strategies.

### The Verifier {#verifier}

The verifier drives the path. It finds the verification cases of a model, has each one compiled and then verified by the portfolio, and back-annotates the evidence into a report of the one execution the backend found. Every trace is made from that report, and so is the witness, a Semantifyr model of the execution. Validating a witness is a request of its own: the witness is verified like any other model. [Inlined Models](../learn/language/reference/library-and-output/inlined-models.md#witnesses) says what a witness is, and [Portfolios and Verdicts](../learn/backends/portfolios-and-verdicts.mdx#validation) how it is validated.

## What Runs the Path {#what-runs-the-path}

The launchers run the path on the command line, one for each language, with the same options, output, and exit codes. Language servers bring each language into an editor, and the editor's extension runs them and shows a case's witness and trace. Live is a server that hosts the same language servers and verifications for many visitors, with a browser application over it. Where a language server runs in the browser, the server receives only the translated Semantifyr model.

The [API documentation](pathname:///api/) is generated from the code.
