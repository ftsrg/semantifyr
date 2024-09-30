<!--
  SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
  
  SPDX-License-Identifier: EPL-2.0
-->

# Semantifyr

A framework to support the declarative definition of engineering model semantics.

## Build

Use Java 17 for building.

Run `gradlew build` to assemble the whole project, and execute all automated test, including regression testing and formal verifications. The required environment (e.g., Theta binaries) is automatically constructed by Gradle. Tests should run in a few minutes.

To execute all tests (even extremely slow, several days long tests!), run `gradlew allTests`.

NOTE: Verification tests use Docker to run Theta. To run the build locally, ensure you have docker installed!

## Contribution

Please, follow the instructions in [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Copyright (c) 2024 [Semantifyr Authors](CONTRIBUTORS.md)

Semantifyr is available under the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0/).

Semantifyr complies with the [REUSE Specification – Version 3.0](https://reuse.software/) to provide copyright and licensing information to each file, including files available under other licenses. For more information, see the comments headers in each file and the license texts in the [LICENSES](LICENSES) directory.
