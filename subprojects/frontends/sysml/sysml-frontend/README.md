<!--
  SPDX-FileCopyrightText: 2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# SysML v2 frontend

The semantic library lives in `../models/libraries` (`default`, and the `topdown` variant with top-down transition priority); the example models in `../models/examples`.

Running the tests:

- `./gradlew :sysml-frontend:test` runs the unit tests.
- `./gradlew :sysml-frontend:semanticTest` verifies the library's semantic cases.
- `./gradlew :sysml-frontend:integrationTest` verifies the example models; a case the manifest marks as nightly runs only with `-PheavyCases=true`.
- `./gradlew :sysml-frontend:nightlyTest` runs the witness re-checks and the trace tests.
