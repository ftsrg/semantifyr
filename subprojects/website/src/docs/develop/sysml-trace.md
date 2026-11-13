---
title: SysML v2 traces
sidebar_position: 1
---

# SysML v2 traces

The form of `trace.sysml`, what it shows and the name map are on the reference page [SysML v2 traces](../learn/cli/sysml-trace.mdx). This page is how it is generated and tested.

## Generation

`SysMLv2Frontend.verify` runs the `trace` command of the bundled SysIDE fork for every case with a witness, with three files: the case's `witness.json` and `trace.json` and the name map the translation wrote next to the OXSTS (`compiled/<variant>/<model>.names.json`). The command generates the trace, validates it against the model with SysIDE, and writes `trace.sysml` next to the witness only when it validates. The generator replays nothing of the library: it reads the witness state and the traced calls alone.

## Tests

The reference pair is `subprojects/frontends/sysml/models/etc/crossroads-green.*`, Theta's `cegar-pred-cart` witness of `TrafficLightABehaviorNormalGreenUnreachableViolated` of `models/examples/crossroads.sysml`; it is regenerated on that configuration, since a race may return another valid witness. The fork's golden test compares the trace generated from its witness and trace files with the reference text whole. `ReferenceTraceTest` in `:sysml-wrapper` validates the reference trace with the pinned SysIDE and generates it with the bundled command, the nightly `SysMLTraceTests` checks that every witness of the SysML suites yields a trace, and the reference trace validates in the OMG pilot implementation.
