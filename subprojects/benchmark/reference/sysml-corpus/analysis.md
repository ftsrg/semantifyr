# Campaign sysml-corpus

- 108 cells, 324 measured runs and 108 warmup runs.
- Run 2026-11-01T02:37:04.901633345Z: Semantifyr 0.1.0, 3 repetitions after 1 warmups, timeout 2m, 8 processors, Linux 7.2.11-arch1-1 amd64, Java Arch Linux 25.0.5.0.3, backends without runexec.
- Host load average at cell start 9.724 to 21.143, median 14.135 on 8 processors. The host was loaded, so these times are not comparable with a quiet host's.
- Times are medians over the measured repetitions, with minimum and maximum in the data files; sums add the medians of cells. A cell without a completed measured repetition has no times and is counted by its outcome.

## Correctness (RQ1)

Every cell's expected verdict against the verdicts of its measured repetitions. A cell is wrong when any repetition gives a decisive verdict other than the expected one, and is counted by its outcome when that is not completed; unjudged cells gave a decisive verdict where none is expected.

Files: `correctness.tex`, `verdicts.tex`.

| Model | Cells | Expected passed | Expected failed | No expectation | As expected | Wrong | Unjudged | Timeout | Inconclusive | Not supported | Errored | Unavailable |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| aircraft_engine.sysml | 9 | 6 | 3 | 0 | 9 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| autonomous_driving.sysml | 10 | 8 | 2 | 0 | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| crossroads.sysml | 3 | 0 | 3 | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| door_access.sysml | 9 | 6 | 3 | 0 | 9 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| orion_protocol.sysml | 11 | 8 | 3 | 0 | 11 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| power_subsystems.sysml | 9 | 6 | 3 | 0 | 9 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| semanticstest.sysml | 53 | 53 | 0 | 0 | 53 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| topdowntransition.sysml | 4 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Total | 108 | 91 | 17 | 0 | 108 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

| Expected | Passed | Failed | Inconclusive | Not supported | Mixed | Timeout | Errored | Unavailable | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Passed | 91 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 91 |
| Failed | 0 | 17 | 0 | 0 | 0 | 0 | 0 | 0 | 17 |
| None | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

- No cell is wrong.

## Unfolding and model checking (RQ2)

Unfolding is the four compiler phases, from the OXSTS model to the flattened model; model checking is the backend tool's run, without translating its input or reading back a witness. The transformation is the SysML frontend's translation to OXSTS, once per model. Sums cover the completed cells; a timed-out cell is listed in the data file with its verdict and no times.

Files: `unfolding.dat`, `unfolding.tex`.

| Model | Cells | Completed | Timeouts | Transformation (s) | Unfolding (s) | Checking (s) | Unfolding share |
| --- | --- | --- | --- | --- | --- | --- | --- |
| aircraft_engine.sysml | 9 | 9 | 0 | 24.936 | 2.125 | 13.389 | 0.137 |
| autonomous_driving.sysml | 10 | 10 | 0 | 47.774 | 11.369 | 23.327 | 0.328 |
| crossroads.sysml | 3 | 3 | 0 | 41.111 | 1.674 | 10.478 | 0.138 |
| door_access.sysml | 9 | 9 | 0 | 53.631 | 1.151 | 23.802 | 0.046 |
| orion_protocol.sysml | 11 | 11 | 0 | 38.803 | 10.018 | 33.249 | 0.232 |
| power_subsystems.sysml | 9 | 9 | 0 | 51.961 | 1.337 | 28.548 | 0.045 |
| semanticstest.sysml | 53 | 53 | 0 | 60.383 | 5.973 | 115.181 | 0.049 |
| topdowntransition.sysml | 4 | 4 | 0 | 48.886 | 1.036 | 7.800 | 0.117 |
| Total | 108 | 108 | 0 | 367.485 | 34.683 | 255.774 | 0.119 |

## Model sizes (RQ2)

Variables (V), operations (O) and expressions (E) of the OXSTS input model, the inlined model and the flattened model that reaches the backend. The input counts the model's own file without the libraries it imports, so a SysML model's input is its structure only; the inlined and flattened sizes are per case, and the table gives their medians over the cases.

Files: `sizes.dat`, `sizes.tex`.

| Model | Cases | Input V | Input O | Input E | Inlined V | Inlined O | Inlined E | Flattened V | Flattened O | Flattened E |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| aircraft_engine.sysml | 9 | 0 | 0 | 164 | 17 | 190 | 1231 | 23 | 228 | 596 |
| autonomous_driving.sysml | 10 | 0 | 0 | 255 | 32 | 720 | 7478 | 47 | 751 | 2240 |
| crossroads.sysml | 3 | 0 | 0 | 429 | 49 | 442 | 3429 | 75 | 430 | 1180 |
| door_access.sysml | 9 | 0 | 0 | 94 | 8 | 50 | 293 | 7 | 54 | 134 |
| orion_protocol.sysml | 11 | 0 | 0 | 325 | 48 | 665 | 5224 | 79 | 572 | 1525 |
| power_subsystems.sysml | 9 | 0 | 0 | 100 | 7 | 50 | 369 | 8 | 53 | 177 |
| semanticstest.sysml | 53 | 0 | 0 | 657 | 8 | 30 | 187 | 5 | 31 | 74 |
| topdowntransition.sysml | 4 | 0 | 0 | 44 | 9 | 39 | 237.5 | 6.5 | 41 | 100.5 |
