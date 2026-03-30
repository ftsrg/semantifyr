# SysML v2 Semantics

This subproject contains the modelled SysML v2 semantic library (./Library), a variant library (./TopDownLibrary), and the automated verification and benchmarking of the used test models (./TestModels).

Running the benchmark: `./gradlew benchmarkVerificationCases` --- the outputs will be written to `./TestModels/benchmark-results.json`.

Running the tests: `./gradlew :sysmlv2-semantics:testVerificationCases`, which excludes the uncompressed Spacecraft model --- or `./gradlew :sysmlv2-semantics:allTests` for all verification cases.
