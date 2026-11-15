---
title: Architecture
sidebar_position: 0
hide_table_of_contents: true
description: The path of a model through Semantifyr's components, what each of them is for, and the Gradle modules of the build with the dependencies between them, for a contributor about to read the code.
---

import ComponentPath from '@site/src/components/Develop/ComponentPath';
import ModuleGraph from '@site/src/components/Develop/ModuleGraph';
import modules from '@module-graph/modules.json';

# Architecture

<ComponentPath />

## The Path

### Frontends {#frontends}

A frontend takes a model in an engineering language and translates it onto that language's semantic library, OXSTS classes that state what the language's constructs mean. The meaning is then a model of its own, read, varied and verified like any other, and not the code of a translator. A construct the frontend does not translate is reported on the construct, and no model is written for it. At the end of the path the same frontend turns the witness into a trace in the model's language. A model written in OXSTS needs no frontend. [Frontends](../learn/frontends/index.mdx) lists them.

### The Compiler {#compiler}

The compiler reads an OXSTS model on its semantic library and never learns which language the model came from. A verification case is a class of the model that carries its own property. The compiler takes one case through instantiation, inlining and flattening, optimizing between them, to a transition system: variables, an initial transition, a main transition and the property. [How OXSTS Compiles](../learn/oxsts/compilation/index.mdx) shows the phases, and [The OXSTS Language](../learn/oxsts/language/index.md) specifies the language.

### The Portfolio {#portfolio}

A portfolio is a strategy for the backend runs: which backends run under which configurations, side by side, and how the verdict is decided from their answers. Every backend receives the same transition system, refuses up front what its model checker cannot express, translates the rest into the checker's input, runs it, and reads back a verdict and, as evidence, the states it found. A backend is trusted in one direction, and a portfolio takes a verdict only where that direction backs it. A case without variables and transitions is decided with no backend. [Portfolios and Verdicts](../learn/backends/portfolios-and-verdicts.mdx) explains the strategies.

### The Verifier {#verifier}

The verifier drives the path: it finds the cases of a model, has each one compiled and verified by the portfolio, and back-annotates the evidence into a witness, an OXSTS model of the one execution the backend found, from which every trace is made. Validating a witness is a request of its own: the witness is verified again, as any model is. [Inlined Models](../learn/oxsts/language/library-and-output/inlined-models.md#witnesses) says what a witness is, and [Portfolios and Verdicts](../learn/backends/portfolios-and-verdicts.mdx#validation) how it is validated.

## What Runs the Path {#what-runs-the-path}

The launchers run the path on the command line, one for each language, with the same options, output and exit codes. Language servers bring each language into an editor, whose extension runs them and shows a case's witness and trace. Live is a server that hosts the same language servers and verifications for many visitors, with a browser application over it; where a language server runs in the browser, the server receives only the translated OXSTS.

## The Modules

<ModuleGraph graph={modules} />

Each module of the build stands above the modules it depends on, counting what its production code and the artifacts it builds take from other modules, and leaving out what only its tests take. An edge is drawn only where no longer path leads to the same module, and the list names every dependency. A framed group is a folder under `subprojects/` that holds the modules of one frontend, one backend or live. `:logging`, `:utils` and `:guice-common` serve the other modules, and `:website` builds this site. The [API documentation](pathname:///api/) is generated from the code.
