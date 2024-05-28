<!--
  SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
  
  SPDX-License-Identifier: EPL-2.0
-->

# Semantifyr

## Build

Use Java 17 for building.

Run `gradle build` to assemble the whole project, and execute all automated test, including regression testing and formal verifications. The required environment (e.g., Theta binaries) is automatically constructed by Gradle. Tests should run in a few minutes.

To execute all tests (even extremely slow, several hours long tests!), run `gradle allTests`.

NOTE: on windows machines, set script execution policy to unrestricted, because the build uses ps1 scripts.
`Set-ExecutionPolicy Unrestricted`

## Contribution

Please, follow the instructions in [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Copyright (c) 2024 [Semantifyr Authors](CONTRIBUTORS.md)

Semantifyr is available under the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0/).

Semantifyr complies with the [REUSE Specification – Version 3.0](https://reuse.software/) to provide copyright and licensing information to each file, including files available under other licenses. For more information, see the comments headers in each file and the license texts in the [LICENSES](LICENSES) directory.
