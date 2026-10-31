<!--
  SPDX-FileCopyrightText: 2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# SysML v2 frontend

The semantic library lives in `../models/libraries/default`, the example models in `../models/examples`. A variant is a package of the library that imports the base packages and subclasses the classes it changes: `semantifyr::sysml::topdown` (`TopDown.oxsts`) gives `State` top-down transition priority. `SysMLv2Variant.TopDown` puts its import before the base imports of the compiled model, and the first import that declares a name wins.

Running the tests:

- `./gradlew :sysml-frontend:test` runs the smoke test.
- `./gradlew :sysml-frontend:integrationTest` verifies the example models.
- `./gradlew :sysml-frontend:nightlyTest` runs the long-running cases and the witness re-checks.
