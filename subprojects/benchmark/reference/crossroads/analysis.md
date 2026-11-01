# Campaign crossroads

- 244 cells, 732 measured runs and 244 warmup runs.
- Run 2026-11-01T01:04:28.963574941Z: Semantifyr 0.1.0, 3 repetitions after 1 warmups, timeout 5m, 8 processors, Linux 7.2.11-arch1-1 amd64, Java Arch Linux 25.0.5.0.3, backends without runexec.
- Host load average at cell start 11.319 to 40.191, median 19.210 on 8 processors. The host was loaded, so these times are not comparable with a quiet host's.
- Times are medians over the measured repetitions, with minimum and maximum in the data files; sums add the medians of cells. A cell without a completed measured repetition has no times and is counted by its outcome.

## Correctness (RQ1)

Every cell's expected verdict against the verdicts of its measured repetitions. A cell is wrong when any repetition gives a decisive verdict other than the expected one, and is counted by its outcome when that is not completed; unjudged cells gave a decisive verdict where none is expected.

Files: `correctness.tex`, `verdicts.tex`.

| Model | Cells | Expected passed | Expected failed | No expectation | As expected | Wrong | Unjudged | Timeout | Inconclusive | Not supported | Errored | Unavailable |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| abstract_redefinition.oxsts | 4 | 4 | 0 | 0 | 4 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| arithmetic.oxsts | 12 | 12 | 0 | 0 | 12 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| arrays.oxsts | 20 | 16 | 4 | 0 | 20 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| boolean_operators.oxsts | 11 | 11 | 0 | 0 | 11 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| cast.oxsts | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| classes_and_inheritance.oxsts | 10 | 10 | 0 | 0 | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| comparison.oxsts | 11 | 11 | 0 | 0 | 11 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| control_flow.oxsts | 10 | 10 | 0 | 0 | 10 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| crossroads.sysml | 34 | 31 | 3 | 0 | 34 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| enums.oxsts | 5 | 5 | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| globals.oxsts | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| if_then_else.oxsts | 5 | 5 | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| initial_values.oxsts | 8 | 4 | 4 | 0 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| inline_calls.oxsts | 7 | 7 | 0 | 0 | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| inline_operations.oxsts | 14 | 13 | 1 | 0 | 14 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| local_instances.oxsts | 13 | 13 | 0 | 0 | 13 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| local_vars.oxsts | 7 | 6 | 1 | 0 | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| navigation.oxsts | 6 | 6 | 0 | 0 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| opposites.oxsts | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| optimizer_soundness.oxsts | 5 | 5 | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| parametric.oxsts | 8 | 8 | 0 | 0 | 8 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| parity.oxsts | 6 | 6 | 0 | 0 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| primitives.oxsts | 6 | 6 | 0 | 0 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| property_calls.oxsts | 5 | 5 | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| records.oxsts | 11 | 7 | 4 | 0 | 11 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| runtime_for.oxsts | 6 | 6 | 0 | 0 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| temporal.oxsts | 7 | 7 | 0 | 0 | 7 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| tracing.oxsts | 2 | 2 | 0 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| variable_dispatch.oxsts | 5 | 5 | 0 | 0 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Total | 244 | 227 | 17 | 0 | 244 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

| Expected | Passed | Failed | Inconclusive | Not supported | Mixed | Timeout | Errored | Unavailable | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Passed | 227 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 227 |
| Failed | 0 | 17 | 0 | 0 | 0 | 0 | 0 | 0 | 17 |
| None | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

- No cell is wrong.

## Unfolding and model checking (RQ2)

Unfolding is the four compiler phases, from the OXSTS model to the flattened model; model checking is the backend tool's run, without translating its input or reading back a witness. The transformation is the SysML frontend's translation to OXSTS, once per model. Sums cover the completed cells; a timed-out cell is listed in the data file with its verdict and no times.

Files: `unfolding.dat`, `unfolding.tex`.

| Model | Backend | Cells | Completed | Timeouts | Transformation (s) | Unfolding (s) | Checking (s) | Unfolding share |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| abstract_redefinition.oxsts | theta-cegar-expl-pred-combined | 4 | 4 | 0 |  | 0.072 | 9.058 | 0.008 |
| arithmetic.oxsts | theta-cegar-expl-pred-combined | 12 | 12 | 0 |  | 0.282 | 39.790 | 0.007 |
| arrays.oxsts | theta-cegar-expl-pred-combined | 20 | 20 | 0 |  | 0.426 | 50.785 | 0.008 |
| boolean_operators.oxsts | theta-cegar-expl-pred-combined | 11 | 11 | 0 |  | 0.226 | 36.581 | 0.006 |
| cast.oxsts | theta-cegar-expl-pred-combined | 2 | 2 | 0 |  | 0.046 | 7.297 | 0.006 |
| classes_and_inheritance.oxsts | theta-cegar-expl-pred-combined | 10 | 10 | 0 |  | 0.339 | 48.975 | 0.007 |
| comparison.oxsts | theta-cegar-expl-pred-combined | 11 | 11 | 0 |  | 0.311 | 40.715 | 0.008 |
| control_flow.oxsts | theta-cegar-expl-pred-combined | 10 | 10 | 0 |  | 0.235 | 34.589 | 0.007 |
| crossroads.sysml | theta-cegar-expl | 34 | 34 | 0 | 58.812 | 30.444 | 178.978 | 0.145 |
| enums.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.085 | 17.048 | 0.005 |
| globals.oxsts | theta-cegar-expl-pred-combined | 2 | 2 | 0 |  | 0.077 | 7.720 | 0.010 |
| if_then_else.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.095 | 19.425 | 0.005 |
| initial_values.oxsts | theta-cegar-expl-pred-combined | 8 | 8 | 0 |  | 0.207 | 31.780 | 0.006 |
| inline_calls.oxsts | theta-cegar-expl-pred-combined | 7 | 7 | 0 |  | 0.233 | 38.208 | 0.006 |
| inline_operations.oxsts | theta-cegar-expl-pred-combined | 14 | 14 | 0 |  | 0.365 | 66.748 | 0.005 |
| local_instances.oxsts | theta-cegar-expl-pred-combined | 13 | 13 | 0 |  | 0.207 | 20.672 | 0.010 |
| local_vars.oxsts | theta-cegar-expl-pred-combined | 7 | 7 | 0 |  | 0.098 | 14.696 | 0.007 |
| navigation.oxsts | theta-cegar-expl-pred-combined | 6 | 6 | 0 |  | 0.044 | 3.787 | 0.012 |
| opposites.oxsts | theta-cegar-expl-pred-combined | 2 | 2 | 0 |  | 0.012 | 1.691 | 0.007 |
| optimizer_soundness.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.025 | 4.424 | 0.006 |
| parametric.oxsts | theta-cegar-expl-pred-combined | 8 | 8 | 0 |  | 0.098 | 10.615 | 0.009 |
| parity.oxsts | theta-cegar-expl-pred-combined | 6 | 6 | 0 |  | 0.112 | 16.595 | 0.007 |
| primitives.oxsts | theta-cegar-expl-pred-combined | 6 | 6 | 0 |  | 0.097 | 17.190 | 0.006 |
| property_calls.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.044 | 6.369 | 0.007 |
| records.oxsts | theta-cegar-expl-pred-combined | 11 | 11 | 0 |  | 0.126 | 21.301 | 0.006 |
| runtime_for.oxsts | theta-cegar-expl-pred-combined | 6 | 6 | 0 |  | 0.053 | 8.287 | 0.006 |
| temporal.oxsts | theta-cegar-expl-pred-combined | 7 | 7 | 0 |  | 0.079 | 17.219 | 0.005 |
| tracing.oxsts | theta-cegar-expl-pred-combined | 2 | 2 | 0 |  | 0.033 | 4.742 | 0.007 |
| variable_dispatch.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.100 | 14.620 | 0.007 |
| Total |  | 244 | 244 | 0 | 58.812 | 34.572 | 789.905 | 0.042 |

## Model sizes (RQ2)

Variables (V), operations (O) and expressions (E) of the OXSTS input model, the inlined model and the flattened model that reaches the backend. The input counts the model's own file without the libraries it imports, so a SysML model's input is its structure only; the inlined and flattened sizes are per case, and the table gives their medians over the cases.

Files: `sizes.dat`, `sizes.tex`.

| Model | Cases | Input V | Input O | Input E | Inlined V | Inlined O | Inlined E | Flattened V | Flattened O | Flattened E |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| abstract_redefinition.oxsts | 4 | 1 | 10 | 47 | 1 | 4 | 19 | 1 | 4 | 12 |
| arithmetic.oxsts | 12 | 12 | 24 | 144 | 1 | 3 | 9.5 | 1 | 3 | 8 |
| arrays.oxsts | 20 | 33 | 93 | 509 | 2 | 5 | 24.5 | 2 | 7.5 | 18.5 |
| boolean_operators.oxsts | 11 | 27 | 37 | 174 | 3 | 5 | 16 | 3 | 5 | 13 |
| cast.oxsts | 2 | 2 | 8 | 41 | 1 | 4 | 16 | 1 | 3 | 10.5 |
| classes_and_inheritance.oxsts | 10 | 5 | 31 | 144 | 1 | 3 | 13 | 1 | 3 | 9 |
| comparison.oxsts | 11 | 23 | 46 | 230 | 3 | 7 | 34 | 3 | 7 | 27 |
| control_flow.oxsts | 10 | 13 | 49 | 141 | 1 | 7 | 15 | 1 | 4 | 12 |
| crossroads.sysml | 34 | 0 | 0 | 429 | 49 | 442 | 3429 | 75 | 430 | 1180 |
| enums.oxsts | 5 | 5 | 40 | 84 | 1 | 9 | 19 | 1 | 9 | 15 |
| globals.oxsts | 2 | 1 | 6 | 34 | 1 | 4 | 15 | 1 | 4 | 12 |
| if_then_else.oxsts | 5 | 8 | 13 | 85 | 2 | 4 | 16 | 1 | 4 | 11 |
| initial_values.oxsts | 8 | 10 | 9 | 56 | 1 | 2 | 5 | 1 | 2 | 4 |
| inline_calls.oxsts | 7 | 5 | 28 | 107 | 1 | 4 | 15 | 1 | 4 | 12 |
| inline_operations.oxsts | 14 | 25 | 66 | 190 | 1 | 5 | 23 | 1 | 5 | 17 |
| local_instances.oxsts | 13 | 8 | 68 | 234 | 3 | 10 | 30 | 1 | 13 | 18 |
| local_vars.oxsts | 7 | 7 | 36 | 136 | 1 | 7 | 23 | 1 | 7 | 19 |
| navigation.oxsts | 6 | 2 | 16 | 78 | 1 | 2.5 | 10 | 0.5 | 2.5 | 5.5 |
| opposites.oxsts | 2 | 1 | 7 | 35 | 1 | 4 | 25 | 1 | 4 | 12 |
| optimizer_soundness.oxsts | 5 | 13 | 25 | 80 | 3 | 6 | 20 | 3 | 6 | 16 |
| parametric.oxsts | 8 | 10 | 32 | 122 | 1 | 4 | 15 | 1 | 4 | 12 |
| parity.oxsts | 6 | 6 | 22 | 72 | 1 | 4 | 13 | 1 | 4 | 10.5 |
| primitives.oxsts | 6 | 6 | 20 | 77 | 1 | 4 | 15 | 1 | 4 | 12 |
| property_calls.oxsts | 5 | 4 | 10 | 57 | 1 | 3 | 8 | 1 | 3 | 5 |
| records.oxsts | 11 | 23 | 81 | 279 | 1 | 9 | 30 | 2 | 9 | 22 |
| runtime_for.oxsts | 6 | 12 | 30 | 102 | 1 | 6 | 18 | 2 | 6 | 15 |
| temporal.oxsts | 7 | 7 | 27 | 104 | 1 | 4 | 15 | 1 | 4 | 12 |
| tracing.oxsts | 2 | 2 | 15 | 53 | 2.5 | 11.5 | 42.5 | 4.5 | 11.5 | 25.5 |
| variable_dispatch.oxsts | 5 | 6 | 34 | 122 | 3 | 14 | 38 | 1 | 12 | 25 |
