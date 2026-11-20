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

A frontend takes a model in an engineering language and translates it onto that language's semantic library, classes in the Semantifyr Language that state what the language's constructs mean. The meaning is then a model of its own, read, varied and verified like any other, and not the code of a translator. A construct the frontend does not translate is reported on the construct, and no model is written for it. At the end of the path the same frontend turns the witness into a trace in the model's language. A Semantifyr model written by hand needs no frontend. [Frontends](../learn/frontends/index.mdx) lists them.

### The Compiler {#compiler}

The compiler reads a Semantifyr model on its semantic library and never learns which language the model came from. A verification case is a class of the model that carries its own property. The compiler takes one case through instantiation, inlining and flattening, optimizing between them, to a transition system: variables, an initial transition, a main transition and the property. [How a Semantifyr Model Compiles](../learn/language/compilation/index.mdx) shows the phases, and the [Semantifyr Language Specification](../learn/language/specification/index.md) specifies the language.

### The Backends {#backends}

A backend connects Semantifyr to one model checker, a tool outside it. Every backend receives the same transition system and refuses up front, naming each construct, what its model checker cannot express; the rest it translates into the checker's input, runs the checker and reads back a verdict and, as evidence, the states it found. A backend is trusted in one direction, the one its configuration backs, and in both when the configuration is exact. [Backends](../learn/backends/index.mdx) says what each of them refuses.

### The Portfolio {#portfolio}

A portfolio is a strategy for the backend runs: which backends run under which configurations, side by side, and how the verdict is decided from their answers, each taken only in the direction its backend is trusted. A case without variables and transitions is decided with no backend. [Portfolios and Verdicts](../learn/backends/portfolios-and-verdicts.mdx) explains the strategies.

### The Verifier {#verifier}

The verifier drives the path: it finds the cases of a model, has each one compiled and verified by the portfolio, and back-annotates the evidence into a report of the one execution the backend found, from which every trace is made and the witness, a Semantifyr model of that execution, is derived. Validating a witness is a request of its own: the witness is verified again, as any model is. [Inlined Models](../learn/language/specification/library-and-output/inlined-models.md#witnesses) says what a witness is, and [Portfolios and Verdicts](../learn/backends/portfolios-and-verdicts.mdx#validation) how it is validated.

## What Runs the Path {#what-runs-the-path}

The launchers run the path on the command line, one for each language, with the same options, output and exit codes. Language servers bring each language into an editor, whose extension runs them and shows a case's witness and trace. Live is a server that hosts the same language servers and verifications for many visitors, with a browser application over it; where a language server runs in the browser, the server receives only the translated Semantifyr model.

The [API documentation](pathname:///api/) is generated from the code.
