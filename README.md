<!--
  SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

[![Continuous Delivery](https://github.com/ftsrg/semantifyr/actions/workflows/cd.yml/badge.svg)](https://github.com/ftsrg/semantifyr/actions/workflows/cd.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=ftsrg_semantifyr&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=ftsrg_semantifyr)
[![License: EPL-2.0](https://img.shields.io/badge/License-EPL_2.0-blue.svg)](https://www.eclipse.org/legal/epl-2.0/)
[![REUSE status](https://api.reuse.software/badge/github.com/ftsrg/semantifyr)](https://api.reuse.software/info/github.com/ftsrg/semantifyr)

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="branding/logo-full-dark.svg">
  <img src="branding/logo-full-light.svg" alt="Semantifyr" width="400">
</picture>

Semantifyr is a framework to declaratively define the semantics of engineering modeling languages. A language's semantics is written once, as a library in the [Semantifyr Language](https://semantifyr.org/learn/language/), and its models are verified through that library. Currently it has frontends for the [SysML v2](https://semantifyr.org/learn/frontends/sysml-v2/) and [Gamma](https://semantifyr.org/learn/frontends/gamma/) languages, and it verifies models with
- [Theta](https://semantifyr.org/learn/backends/theta/), a configurable model checking framework,
- [nuXmv](https://semantifyr.org/learn/backends/nuxmv/), a symbolic model checker,
- [Spin](https://semantifyr.org/learn/backends/spin/), a model checker for concurrent software, or
- [Uppaal](https://semantifyr.org/learn/backends/uppaal/), a model checker for timed automata.

## [Semantifyr Live](https://live.semantifyr.org)

Visit [https://live.semantifyr.org](https://live.semantifyr.org) to verify SysML v2, Gamma, and Semantifyr models in the browser.

## Learn

- [Tutorials](https://semantifyr.org/learn/language/lessons/)
- [Command line](https://semantifyr.org/learn/reference/command-line/)
- [Language specification](https://semantifyr.org/learn/language/specification/)

## Install

### From the latest release

- **VS Code**: install [Semantifyr](https://marketplace.visualstudio.com/items?itemName=ftsrg.semantifyr) from the Visual Studio Marketplace or [Open VSX](https://open-vsx.org/extension/ftsrg/semantifyr) to edit and verify Semantifyr and Gamma models.
- **Command line**: download `semantifyr`, `semantifyr-sysml`, or `semantifyr-gamma` from the [latest release](https://github.com/ftsrg/semantifyr/releases/latest), unzip it, and run `bin/semantifyr verify <model>`.

The [install page](https://semantifyr.org/learn/install/) lists the requirements and the backends.

### Build from source

Requirements: 
- Java 25

Build with `./gradlew build`.

`./gradlew pack` builds the launchers in [`semantifyr-cli`](subprojects/semantifyr-cli), [`sysml-cli`](subprojects/frontends/sysml/sysml-cli), and [`gamma-cli`](subprojects/frontends/gamma/gamma-cli), and the extension in [`semantifyr-vscode`](subprojects/semantifyr-vscode).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Citing

To cite Semantifyr, please cite the following paper.

```bibtex
@inproceedings{zavada2024transpilers,
    author    = {Zavada, {\'A}rmin and Marussy, Krist{\'o}f and Moln{\'a}r, Vince},
    title     = {From Transpilers to Semantic Libraries: Formal Verification With Pluggable Semantics},
    booktitle = {Proceedings of the ACM/IEEE 27th International Conference on Model Driven Engineering Languages and Systems (MODELS Companion '24)},
    publisher = {ACM},
    year      = {2024},
    doi       = {10.1145/3652620.3686251},
}
```

The [publications page](https://semantifyr.org/publications/) lists the papers on Semantifyr.

## License

Copyright (c) 2023-2026 [The Semantifyr Authors](CONTRIBUTORS.md). Semantifyr is available under the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0/). It follows the [REUSE Specification](https://reuse.software/): every file carries its copyright and license in its header, and the license texts are in [LICENSES](LICENSES).

## Support

The research and development of Semantifyr was also supported by (in no particular order):

- [Budapest University of Technology and Economics (BME)](https://www.bme.hu/?language=en), [Department of Measurement and Information Systems (MIT)](https://mit.bme.hu/eng/), [Critical Systems Research Group (FTSRG)](https://ftsrg.mit.bme.hu/en/)
- [New National Excellence Program 2023](https://www.unkp.gov.hu)
- [IncQuery Labs cPlc.](https://incquery.io/)
- Ministry of Culture and Innovation of Hungary, National Research, Development and Innovation Fund, financed under the EKÖP_KDP-24-1-BME-21 funding scheme (2024-2028)
