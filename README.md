<!--
  SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

[![Continuous Delivery](https://github.com/ftsrg/semantifyr/actions/workflows/cd.yml/badge.svg)](https://github.com/ftsrg/semantifyr/actions/workflows/cd.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=ftsrg_semantifyr&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ftsrg_semantifyr)
[![License: EPL-2.0](https://img.shields.io/badge/License-EPL_2.0-blue.svg)](https://www.eclipse.org/legal/epl-2.0/)
[![REUSE status](https://api.reuse.software/badge/github.com/ftsrg/semantifyr)](https://api.reuse.software/info/github.com/ftsrg/semantifyr)

# Semantifyr

A framework to support the declarative definition of engineering model semantics.

Semantifyr lets you define what a modeling language means once, as a semantic library written in its own language, OXSTS, and then verify the models of that language with established model checkers. SysML v2 and Gamma are its frontends: a model of either is compiled through its semantic library to a transition system, checked by Theta, nuXmv, Spin or Uppaal, and the trace of a case comes back in the language of the model.

## Try it

[Semantifyr Live](https://live.semantifyr.org) verifies SysML v2, Gamma and OXSTS models in the browser, with nothing to install.

## Install

Semantifyr has three launchers, `semantifyr` for OXSTS, `semantifyr-sysml` for SysML v2 and `semantifyr-gamma` for Gamma, each with the Theta backend inside, which runs on Linux x64, and a VS Code extension for OXSTS and Gamma. They need Java 25; `semantifyr-sysml` also needs Node.js 22. nuXmv, Spin and Uppaal are used when they are on `PATH`.

No release is published yet. Until the first one, build a launcher with `./gradlew :semantifyr-cli:installDist`, `:sysml-cli:installDist` or `:gamma-cli:installDist`, and the extension with `./gradlew :semantifyr-vscode:bundleExtension`; the [website](https://semantifyr.org/learn/start/install/) says where each lands.

## Use

    semantifyr-sysml verify model.sysml
    semantifyr-gamma verify model.gamma
    semantifyr verify model.oxsts

Each case gets a verdict and a report; `--help` lists the options, and `portfolios` the strategies for the backend runs.

## Learn

The [website](https://semantifyr.org/) has the tutorials, the command line, the backends and the reference of OXSTS.

## Status

Semantifyr is a research prototype of a PhD project at the Budapest University of Technology and Economics. Its version is 0.1.0: commands, options and the language may still change between releases.

## Build from source

Use Java 25. `./gradlew build` assembles and tests the project, including the verification smoke tests; Gradle provides Theta, and a test that needs nuXmv, Spin or Uppaal is skipped when that backend is not on `PATH`. `./gradlew integrationTest` runs the conformance and frontend suites, `./gradlew nightlyTest` the long-running cases.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Citing

To cite Semantifyr, cite the paper that introduced its approach, as [CITATION.cff](CITATION.cff) gives it:

Ármin Zavada, Kristóf Marussy and Vince Molnár. From Transpilers to Semantic Libraries: Formal Verification With Pluggable Semantics. In MODELS Companion '24, ACM, 2024. [doi:10.1145/3652620.3686251](https://doi.org/10.1145/3652620.3686251)

## License

Copyright (c) 2023-2026 [The Semantifyr Authors](CONTRIBUTORS.md). Semantifyr is available under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/). It follows the [REUSE Specification](https://reuse.software/): every file carries its copyright and license in its header, and the license texts are in [LICENSES](LICENSES).

## Support

The research and development of Semantifyr was also supported by (in no particular order):

- [Budapest University of Technology and Economics (BME)](https://www.bme.hu/?language=en), [Department of Measurement and Information Systems (MIT)](https://mit.bme.hu/eng/), [Critical Systems Research Group (FTSRG)](https://ftsrg.mit.bme.hu/en/)
- [New National Excellence Program 2023](https://www.unkp.gov.hu)
- [IncQuery Labs cPlc.](https://incquery.io/)
- Ministry of Culture and Innovation of Hungary, National Research, Development and Innovation Fund, financed under the EKÖP_KDP-24-1-BME-21 funding scheme (2024-2028)
