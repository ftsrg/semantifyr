# Semantifyr benchmark @VERSION@

The campaigns of the Semantifyr evaluation with their runner and analysis, the SysML v2 corpus (`models/sysml`, cases and expected verdicts in `manifest.json`), the OXSTS semantic and smoke models (`models/semantic`, `models/smoke`), the campaign definitions (`campaigns/Campaigns.kt`) and the reference results of this version (`reference/`).

Live editor image of this release: `@LIVE_IMAGE@`

## Setup

- Linux or macOS with a Java 25 runtime on `PATH` or in `JAVA_HOME`.
- Node.js 22 or newer on `PATH` for the SysML campaigns.
- Theta from the CLI distribution of the same version, `semantifyr-@VERSION@.zip` on the same release, unpacked next to this directory: `export PATH="$PWD/../semantifyr-@VERSION@/theta-xsts-cli:$PATH"`. Without Theta on `PATH` the campaigns run it from its Docker image.
- nuXmv (`nuXmv`), Spin (`spin` and a C compiler) and Uppaal (`verifyta`) on `PATH`; a cell whose backend is missing is recorded as unavailable.
- Optional: BenchExec's `runexec` on `PATH` with the Python library `pystemd`, so backends run under cgroups with measured CPU time and peak memory. Without it they run directly.

## Commands

Run from this directory.

```sh
bin/semantifyr-benchmark run smoke
bin/semantifyr-benchmark run crossroads
bin/semantifyr-benchmark analyze results/crossroads results/crossroads/analysis
```

- Campaigns: `smoke`, `crossroads`, `optimizer-passes`, `sysml-corpus`, `backends`, `spacecraft`.
- `run` resumes an interrupted campaign; `--fresh` discards its earlier results, `--no-runexec` runs the backends directly, `--runexec-options "<options>"` replaces runexec's default `--no-container`, `--baseline <results directory>` fails on cells slower than that run's by more than `--time-tolerance` (default `0.2`).
- `run` exits with 1 when the verdicts or model sizes differ from `reference/<campaign>.jsonl`, and with 2 on a usage error.

## Results

- `results/<campaign>/results.jsonl`: one record per run of a cell; `run.json`: the host and the campaign settings; `results.csv`, `summary.md`, `engines.json`: per cell and per axis.
- `results/<campaign>/analysis/`: pgfplots data (`.dat`), LaTeX tables (`.tex`) and `analysis.md`, written after every run.
- `reference/<campaign>/`: the same analysis of the run this version's reference was taken from.

## One cell with the CLI

The CLI distribution verifies one OXSTS cell of a campaign, for example of `crossroads` on `models/semantic/arithmetic.oxsts`, and writes its `report.json` under `results/cli`:

```sh
../semantifyr-@VERSION@/bin/semantifyr verify --json -d results/cli --portfolio theta-cegar-expl-pred-combined --optimization all --timeout-seconds 300 --case-name semantifyr::verification::lang::arithmetic::AddReach models/semantic/arithmetic.oxsts
```

A level of the `optimizer-passes` campaign is `--disable-optimization <pass>`, `none` is `--optimization none`.
