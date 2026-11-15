---
title: Architecture
sidebar_position: 0
hide_table_of_contents: true
description: The path of a model through Semantifyr's components, the Gradle modules that hold each of them and the dependencies between the modules, for a contributor about to read the code.
---

import ComponentPath from '@site/src/components/Develop/ComponentPath';
import ModuleGraph from '@site/src/components/Develop/ModuleGraph';
import modules from '@module-graph/modules.json';

# Architecture

<ComponentPath />

## The Path

### Model {#model}

A model in SysML v2 or Gamma comes to a frontend; a model written in OXSTS goes to the compiler as it is. Gamma's language is the grammar `Gamma.xtext` in `:gamma.lang`. SysML v2's is the language of [sysml-2ls](https://github.com/arminzavada/sysml-2ls), a fork of SysIDE, which `:sysml-wrapper` checks out at the commit its build file pins and builds.

### Frontend {#frontend}

A frontend translates a model onto its semantic library, OXSTS files that say what the constructs of its language mean, so that the meaning can be read, varied and verified like any model instead of living in the translation. A construct the frontend does not translate is reported on the construct, and no model is written for it. The SysML v2 frontend runs sysml-2ls's translation through `SysMLv2Compiler` in `:sysml-wrapper`, and `SysMLv2Frontend` in `:sysml-frontend` verifies what it wrote; the Gamma frontend translates in `GammaToOxstsSerializer` and refuses in `UnmappedGammaConstructs`, both in `:gamma-frontend`. The libraries are the folders under `subprojects/frontends/sysml/models/libraries/` and `subprojects/frontends/gamma/models/libraries/`.

### OXSTS Model {#oxsts-model}

The compiler reads an OXSTS model on its semantic library and never learns which language the model came from. A verification case is a class of the model marked `@VerificationCase`, with the property it checks, so a model carries its own questions. `:oxsts.model` holds the metamodel, written in the Kotlin DSL of `Oxsts.kt`, from which the build generates the EMF classes; `:oxsts.lang` holds the grammar `Oxsts.xtext`, the `OxstsValidator`, the scoping, the types and the standard library `semantifyr.oxsts`. [The OXSTS Language](../learn/oxsts/language/index.md) specifies it.

### Compiler {#compiler}

`SemantifyrCompiler` in `:compiler` compiles one verification case through `CompilationPipeline`: it wraps the case's class in an `InlinedOxsts`, instantiates its features into an instance tree, inlines the calls and flattens the instances into variables, and optimizes after inlining and after flattening. Each phase is a package of `hu.bme.mit.semantifyr.compiler.pipeline`: `instantiation`, `inlining`, `flattening` and `optimization`. [How OXSTS Compiles](../learn/oxsts/compilation/index.mdx) shows each phase.

### Transition System {#transition-system}

The flattened `InlinedOxsts` of `:oxsts.model`: variables, an initial transition, a main transition and a property. Every backend receives the same one, in a `BackendVerificationRequest`.

### Portfolio of Backends {#portfolio}

A portfolio is a strategy for the backend runs: which backends run under which configurations, side by side, and how the verdict is decided from their answers. `VerificationPortfolio` is in the `portfolio` package of `:verifier`, and the portfolios themselves are `Portfolios` in `:portfolios`. A backend, a `VerificationBackend` of `:backend`, refuses up front what its model checker cannot express (its `BackendCapabilityChecker`), transforms the transition system into the checker's input, runs the checker through its executor and reads the answer and the evidence back: `:<backend>-backend` and `:<backend>-executor` for Theta, Spin, nuXmv and Uppaal, with `:xsts.lang` and `:cex.lang`, the languages of Theta's input and of the evidence it returns. A case without variables and transitions is decided by `TrivialVerifier` in `:verifier`, and no backend runs. [Portfolios and Verdicts](../learn/backends/portfolios-and-verdicts.mdx) explains the strategies.

### Verdict and Evidence {#verdict}

A backend answers with a `BackendVerificationResult` of `:backend`: a `VerificationVerdict`, whether it is trusted, and as evidence an `InlinedWitness`, the states it found over the transition system's variables. A backend's configuration names the direction in which it is sound (`SoundnessDirection`), and a portfolio decides only on a verdict that direction backs.

### Verifier {#verifier}

`SemantifyrVerifierImpl` in `:verifier` drives the path: it compiles a case, has the portfolio verify it, and back-annotates the evidence into a witness, an OXSTS model of the one execution the backend found (`ClassWitnessTransformer`, `ClassWitnessBackAnnotator`), with its states and its call trace beside it (`CallTraceTransformer`). `VerificationArtifactWriter` writes them into the case's folder as `witness.oxsts`, `witness.json` and `trace.json`, beside `report.json`. A witness is validated by verifying it again (`WitnessValidator`), which the language servers and the Gamma frontend do on request. [Witnesses and Traces](../learn/backends/witnesses-and-traces.mdx) explains both.

### Trace Generator {#trace-generator}

A frontend turns the witness's states and call trace into a trace in its language. `GammaTraceGenerator` in `:gamma-frontend` writes it in Gamma's trace language. The SysML v2 frontend runs sysml-2ls's `trace` command through `SysMLv2Compiler` in `:sysml-wrapper`; where sysml-2ls cannot write the trace, the verdict stands and the frontend logs a warning.

### Trace {#trace}

The trace in the model's language, beside the witness: `trace.sysml` for SysML v2, and `trace.get` for Gamma, whose trace language is part of `Gamma.xtext` in `:gamma.lang`. [SysML v2's traces](../learn/frontends/sysml-v2/traces.mdx) and [Gamma's traces](../learn/frontends/gamma/traces.mdx) describe them.

## What Runs the Path

### Launchers {#launchers}

`semantifyr`, `semantifyr-sysml` and `semantifyr-gamma` are `:semantifyr-cli`, `:sysml-cli` and `:gamma-cli`. They share their root command, options, output and exit codes from `:cli-common` (`RootCommand`, the option groups, `ExitCode`, `verifyCase`), and each distribution carries the Theta backend. [The command line's reference](../learn/reference/command-line.mdx) lists the commands.

### Language Servers and VS Code {#editors}

`:oxsts.lang.ide` and `:gamma.lang.ide` are the language servers of OXSTS and Gamma, on the LSP framework and the wire types of `:lang-ide-common`; their commands discover, verify and validate cases (`OxstsCommandService`, `GammaCommandService`). `:semantifyr-vscode` is the VS Code extension that runs them and shows a case's witness and trace, with the wire types of `:semantifyr-editor-common`, which live shares. `:semantifyr-vscode-server` builds a JupyterHub image with a VS Code in the browser, the extensions and Theta.

### Live {#live}

`:semantifyr-live-backend` is the server: it hosts the language servers in a session for each visitor and runs their verifications. `:semantifyr-live-frontend` is the browser application over it. For SysML v2 it runs sysml-2ls's language server in a web worker, which `:sysml-wrapper` builds, and sends the server only the translated OXSTS.

## The Modules

<ModuleGraph graph={modules} />

Each module of the build stands above the modules it depends on, counting what its production code and the artifacts it builds take from other modules; what only its tests take is left out. A framed group is a folder under `subprojects/` that holds the modules of one frontend, one backend or live. `:logging`, `:utils` and `:guice-common` serve the other modules, and `:website` builds this site. The [API documentation](pathname:///api/) is generated from the code.
