<!--
  SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
  
  SPDX-License-Identifier: EPL-2.0
-->

[![Continuous Integration](https://github.com/ftsrg/semantifyr/actions/workflows/build.yml/badge.svg)](https://github.com/ftsrg/semantifyr/actions/workflows/build.yml)

# Semantifyr

A framework to support the declarative definition of engineering model semantics.

## Build

Use Java 21 for building.

Run `gradlew build` to assemble the whole project, and execute all automated test, including regression testing and formal verifications. The required environment (e.g., Theta binaries) is automatically constructed by Gradle. Tests should run in a few minutes.

To execute all tests (even extremely slow, several days long tests!), run `gradlew allTests`.

NOTE: Verification tests use Docker to run Theta. To run the build locally, ensure you have docker installed!

## Contribution

Please, follow the instructions in [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Copyright (c) 2024 [Semantifyr Authors](CONTRIBUTORS.md)

Semantifyr is available under the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0/).

Semantifyr complies with the [REUSE Specification – Version 3.0](https://reuse.software/) to provide copyright and licensing information to each file, including files available under other licenses. For more information, see the comments headers in each file and the license texts in the [LICENSES](LICENSES) directory.

## Support

The research and development of Semantifyr was also supported by (in no particular order):

- [Budapest University of Technology and Economics (BME)](https://www.bme.hu/?language=en), [Department of Measurement and Information Systems (MIT)](https://mit.bme.hu/eng/), [Critical Systems Research Group (FTSRG)](https://ftsrg.mit.bme.hu/en/)
- [New National Excellence Program 2023](https://www.unkp.gov.hu)
- [IncQuery Labs cPlc.](https://incquery.io/)
- Ministry of Culture and Innovation of Hungary, National Research, Development and Innovation Fund, financed under the EKÖP_KDP-24-1-BME-21 funding scheme (2024-2028)
