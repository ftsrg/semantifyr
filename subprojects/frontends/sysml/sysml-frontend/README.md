<!--
  SPDX-FileCopyrightText: 2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# SysML v2 frontend

The semantic library lives in `../models/libraries` (`default`, and the `topdown` variant with top-down transition priority); the example models in `../models/examples`.

Running the tests:

- `./gradlew :sysml-frontend:test` runs the smoke test.
- `./gradlew :sysml-frontend:integrationTest` verifies the example models.
- `./gradlew :sysml-frontend:nightlyTest` runs the long-running cases and the witness re-checks.
