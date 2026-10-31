---
title: Benchmarking
sidebar_position: 1
---

# Benchmarking

The `:benchmark` module (`subprojects/benchmark/`) measures verification. A campaign runs every cell of its axes, repeats each cell, and stores one record per run. Campaign output is build output, so `clean` deletes it; only the reference results are committed.

## Defining a campaign

Campaigns are Kotlin code in `subprojects/benchmark/src/main/kotlin/hu/bme/mit/semantifyr/benchmark/campaigns/Campaigns.kt`. A campaign declares axes with typed values, the framework permutes them into cells, and the campaign's filters drop the cells it does not want:

```kotlin
val sysmlCorpus = CampaignDefinition("sysml-corpus") { sources ->
    axis(VerificationAxes.model, sources.sysml.models)
    axis(VerificationAxes.case) {
        it[VerificationAxes.model].cases
    }
    axis(VerificationAxes.engine, listOf(Engine.Theta))
    axis(VerificationAxes.backendConfig) {
        listOf(defaultPortfolios.getValue(it[VerificationAxes.engine]))
    }
    axis(VerificationAxes.optimization, listOf(OptimizationLevel.All))
    filter {
        !it[VerificationAxes.case].nightly
    }
    timeout = 2.minutes
}
```

- The verification axes are `model`, `case`, `engine`, `backend` (a portfolio) and `optimization`. An axis given a lambda takes its values from the cell built so far, like the cases of a model.
- `filter` keeps a cell when it returns true; every filter must agree. This is how a long model runs on a selected subset, as the `spacecraft` campaign does.
- `repetitions` (default 3) are measured after `warmups` (default 1) discarded runs. `timeout` (default 5 minutes) bounds each run.
- `sources.sysml` reads the SysML corpus through `subprojects/frontends/sysml/models/examples/manifest.json`: the expected verdict (none for `Unknown`), the falsification flag and whether the case is nightly-only. `sources.oxstsSemantic` reads `oxsts-test-models/semantic`, with the expected verdict from the `expect-pass` and `expect-fail` tags.

Add the definition to `Campaigns.all` to make it runnable.

## Running a campaign

```bash
./gradlew :benchmark:campaign -Pcampaign=crossroads
```

The task puts the build's Theta on `PATH`; other engines are found on `PATH`. A cell whose backend is not installed is recorded as unavailable without running.

Running the task again resumes the campaign: completed and timed-out cells are kept, errored, unavailable and incomplete cells run again. A run stops its cell at the first outcome that is not completed. Cells run one after another, each run in its own temporary directory that is deleted afterwards.

- `-Pbenchmark.fresh=true` discards the earlier results of the campaign.
- `-Pbenchmark.baseline=<directory>` compares the median wall time of every cell with the results in another campaign output directory, failing when a cell is slower by more than `-Pbenchmark.timeTolerance` (default `0.2`, that is 20%).

## Resource control with runexec

When BenchExec's `runexec` is on `PATH` and can run a command, every backend process runs under it: runexec measures its CPU time and peak memory through cgroups and enforces a CPU-time limit equal to the campaign's timeout, and a backend stopped at that limit comes back as `Timeout`. `runexec` needs cgroups delegated to the user, which it arranges itself when the Python library `pystemd` is installed. Without a usable runexec the backends run directly and their CPU time comes from the JVM's reaped child processes, which misses a backend killed on a timeout.

- `-Pbenchmark.runexec=false` runs the backends without runexec even when it is installed.
- `-Pbenchmark.runexecOptions="--no-container --memlimit 8GB"` replaces the default options (`--no-container`). Container mode needs the backends' working directories given with `--full-access-dir`, or what they write is discarded.

## Reading the results

The output is in `subprojects/benchmark/build/benchmark/<campaign>/`:

- `results.jsonl` holds one record per run: the cell, the repetition, whether it was a warmup, the outcome (`Completed`, `TimedOut`, `Errored` or `Unavailable`), the verdict, the expected verdict, `asExpected` (false for a wrong decisive verdict, absent when the verdict is not decisive or nothing is expected), the falsification flag, the engine that decided, wall time, CPU time of the JVM and the backend processes together and of the backends alone, the totals runexec measured when it ran (CPU time, wall time, peak memory), the verifier's metrics from `report.json` (phase durations, per-pass optimizer statistics, model sizes at input, inlined and flattened level), and what a frontend adds: `sysml` carries the SysML to OXSTS transformation time and counters of the input model.
- `results.csv` has one row per cell: the median, minimum and maximum of each time over the measured repetitions, the peak memory, the model sizes and the frontend columns.
- `summary.md` aggregates the cells along every axis, and is also printed at the end of the run.
- `engines.json` lists, for every case in the results, the engines that decided it correctly in this campaign. The corpus manifest is not updated from it.

## Reference results

`subprojects/benchmark/reference/<campaign>.jsonl` holds, for every cell of a campaign, its decisive verdict and its model sizes. When the file exists, the campaign compares its results with it strictly and fails on any difference, a missing cell or an extra one. Times are not part of the reference, because they differ between hosts; compare them with `-Pbenchmark.baseline` on the same host.

After a change that is meant to change verdicts or sizes, run the campaign and rewrite the reference from its results:

```bash
./gradlew :benchmark:campaignReference -Pcampaign=crossroads
```
