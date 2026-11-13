---
title: Gamma traces
sidebar_position: 2
---

# Gamma traces

The form of `trace.gamma` and what it shows are on the reference page [Gamma traces](../learn/cli/gamma-trace.mdx), and witness validation under [Artifacts](../learn/cli/artifacts.mdx#witness-validation). This page is how the trace is generated and tested.

The reference pair is `subprojects/frontends/gamma/models/etc/crossroads-ACanBeGreen.*`, a witness of the case `ACanBeGreen` of `models/examples/Crossroads.gamma`: `trace.json` and `witness.json` as Theta's witness produced them, and the hand-written `trace.gamma` they generate.

## Generation

A verify run writes, step by step, the traced calls to `trace.json` and the state to `witness.json`, both named by their path from the root instance. `GammaTraceGenerator` resolves each path through the names the Gamma compiler recorded when it emitted the OXSTS model, so it never decodes OXSTS names, and maps the traced calls and the state to the acts and asserts the reference page lists. The Gamma language's serializer and formatter write the trace.

## Validation

- `GammaReferenceTraceTest` generates the trace from the pair's JSON files and checks that it serializes to the hand-written `trace.gamma`.
- `GammaTraceLanguageTest` covers the validator's rules.
- `GammaTraceTests` (integration) verifies every `EF` case of the three examples on Theta, nuXmv, Spin and Uppaal, and checks each generated trace: it parses, links and validates against the model, has one step per witness step, asserts the witness's leaf states and variable values, and every fired transition leaves a state active before its step and enters one active after it, which checks the traced calls against the witness state.
- `GammaTraceLanguageServerTest` opens a trace next to its model in the language server, without errors, and checks that the trace relinks when the model changes.
- `GammaWitnessTests` (nightly) re-verifies every backend's witness on all four backends.
