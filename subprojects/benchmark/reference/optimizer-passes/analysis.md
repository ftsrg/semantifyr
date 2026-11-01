# Campaign optimizer-passes

- 2684 cells, 8052 measured runs and 2684 warmup runs.
- Run 2026-11-01T05:27:45.156487824Z: Semantifyr 0.1.0, 3 repetitions after 1 warmups, timeout 2m, 8 processors, Linux 7.2.11-arch1-1 amd64, Java Arch Linux 25.0.5.0.3, backends without runexec.
- Host load average at cell start 1.634 to 29.307, median 3.462 on 8 processors. The host was loaded, so these times are not comparable with a quiet host's.
- Times are medians over the measured repetitions, with minimum and maximum in the data files; sums add the medians of cells. A cell without a completed measured repetition has no times and is counted by its outcome.

## Correctness (RQ1)

Every cell's expected verdict against the verdicts of its measured repetitions. A cell is wrong when any repetition gives a decisive verdict other than the expected one, and is counted by its outcome when that is not completed; unjudged cells gave a decisive verdict where none is expected.

Files: `correctness.tex`, `verdicts.tex`.

| Model | Cells | Expected passed | Expected failed | No expectation | As expected | Wrong | Unjudged | Timeout | Inconclusive | Not supported | Errored | Unavailable |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| abstract_redefinition.oxsts | 44 | 44 | 0 | 0 | 44 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| arithmetic.oxsts | 132 | 132 | 0 | 0 | 132 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| arrays.oxsts | 220 | 176 | 44 | 0 | 220 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| boolean_operators.oxsts | 121 | 121 | 0 | 0 | 121 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| cast.oxsts | 22 | 22 | 0 | 0 | 22 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| classes_and_inheritance.oxsts | 110 | 110 | 0 | 0 | 110 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| comparison.oxsts | 121 | 121 | 0 | 0 | 121 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| control_flow.oxsts | 110 | 110 | 0 | 0 | 110 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| crossroads.sysml | 374 | 341 | 33 | 0 | 374 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| enums.oxsts | 55 | 55 | 0 | 0 | 55 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| globals.oxsts | 22 | 22 | 0 | 0 | 22 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| if_then_else.oxsts | 55 | 55 | 0 | 0 | 55 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| initial_values.oxsts | 88 | 44 | 44 | 0 | 88 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| inline_calls.oxsts | 77 | 77 | 0 | 0 | 77 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| inline_operations.oxsts | 154 | 143 | 11 | 0 | 154 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| local_instances.oxsts | 143 | 143 | 0 | 0 | 143 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| local_vars.oxsts | 77 | 66 | 11 | 0 | 77 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| navigation.oxsts | 66 | 66 | 0 | 0 | 66 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| opposites.oxsts | 22 | 22 | 0 | 0 | 22 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| optimizer_soundness.oxsts | 55 | 55 | 0 | 0 | 55 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| parametric.oxsts | 88 | 88 | 0 | 0 | 88 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| parity.oxsts | 66 | 66 | 0 | 0 | 66 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| primitives.oxsts | 66 | 66 | 0 | 0 | 66 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| property_calls.oxsts | 55 | 55 | 0 | 0 | 55 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| records.oxsts | 121 | 77 | 44 | 0 | 121 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| runtime_for.oxsts | 66 | 66 | 0 | 0 | 66 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| temporal.oxsts | 77 | 77 | 0 | 0 | 77 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| tracing.oxsts | 22 | 22 | 0 | 0 | 22 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| variable_dispatch.oxsts | 55 | 55 | 0 | 0 | 55 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| Total | 2684 | 2497 | 187 | 0 | 2684 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

| Expected | Passed | Failed | Inconclusive | Not supported | Mixed | Timeout | Errored | Unavailable | Total |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Passed | 2497 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 2497 |
| Failed | 0 | 187 | 0 | 0 | 0 | 0 | 0 | 0 | 187 |
| None | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

- No cell is wrong.

## Unfolding and model checking (RQ2)

Unfolding is the four compiler phases, from the OXSTS model to the flattened model; model checking is the backend tool's run, without translating its input or reading back a witness. The transformation is the SysML frontend's translation to OXSTS, once per model. Sums cover the completed cells; a timed-out cell is listed in the data file with its verdict and no times.

Files: `unfolding.dat`, `unfolding.tex`.

| Model | Backend | Cells | Completed | Timeouts | Transformation (s) | Unfolding (s) | Checking (s) | Unfolding share |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| abstract_redefinition.oxsts | theta-cegar-expl-pred-combined | 4 | 4 | 0 |  | 0.015 | 2.109 | 0.007 |
| arithmetic.oxsts | theta-cegar-expl-pred-combined | 12 | 12 | 0 |  | 0.060 | 12.004 | 0.005 |
| arrays.oxsts | theta-cegar-expl-pred-combined | 20 | 20 | 0 |  | 0.110 | 15.856 | 0.007 |
| boolean_operators.oxsts | theta-cegar-expl-pred-combined | 11 | 11 | 0 |  | 0.049 | 8.927 | 0.005 |
| cast.oxsts | theta-cegar-expl-pred-combined | 2 | 2 | 0 |  | 0.015 | 2.458 | 0.006 |
| classes_and_inheritance.oxsts | theta-cegar-expl-pred-combined | 10 | 10 | 0 |  | 0.061 | 9.757 | 0.006 |
| comparison.oxsts | theta-cegar-expl-pred-combined | 11 | 11 | 0 |  | 0.062 | 10.962 | 0.006 |
| control_flow.oxsts | theta-cegar-expl-pred-combined | 10 | 10 | 0 |  | 0.043 | 8.541 | 0.005 |
| crossroads.sysml | theta-cegar-expl | 34 | 34 | 0 | 58.046 | 26.634 | 123.374 | 0.178 |
| enums.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.018 | 3.890 | 0.005 |
| globals.oxsts | theta-cegar-expl-pred-combined | 2 | 2 | 0 |  | 0.006 | 1.303 | 0.005 |
| if_then_else.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.023 | 4.480 | 0.005 |
| initial_values.oxsts | theta-cegar-expl-pred-combined | 8 | 8 | 0 |  | 0.033 | 5.624 | 0.006 |
| inline_calls.oxsts | theta-cegar-expl-pred-combined | 7 | 7 | 0 |  | 0.030 | 3.912 | 0.008 |
| inline_operations.oxsts | theta-cegar-expl-pred-combined | 14 | 14 | 0 |  | 0.056 | 7.486 | 0.007 |
| local_instances.oxsts | theta-cegar-expl-pred-combined | 13 | 13 | 0 |  | 0.067 | 5.548 | 0.012 |
| local_vars.oxsts | theta-cegar-expl-pred-combined | 7 | 7 | 0 |  | 0.026 | 3.760 | 0.007 |
| navigation.oxsts | theta-cegar-expl-pred-combined | 6 | 6 | 0 |  | 0.018 | 1.639 | 0.011 |
| opposites.oxsts | theta-cegar-expl-pred-combined | 2 | 2 | 0 |  | 0.007 | 1.130 | 0.006 |
| optimizer_soundness.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.017 | 2.711 | 0.006 |
| parametric.oxsts | theta-cegar-expl-pred-combined | 8 | 8 | 0 |  | 0.027 | 3.891 | 0.007 |
| parity.oxsts | theta-cegar-expl-pred-combined | 6 | 6 | 0 |  | 0.019 | 3.197 | 0.006 |
| primitives.oxsts | theta-cegar-expl-pred-combined | 6 | 6 | 0 |  | 0.019 | 3.260 | 0.006 |
| property_calls.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.015 | 1.600 | 0.009 |
| records.oxsts | theta-cegar-expl-pred-combined | 11 | 11 | 0 |  | 0.046 | 6.373 | 0.007 |
| runtime_for.oxsts | theta-cegar-expl-pred-combined | 6 | 6 | 0 |  | 0.019 | 3.188 | 0.006 |
| temporal.oxsts | theta-cegar-expl-pred-combined | 7 | 7 | 0 |  | 0.023 | 3.851 | 0.006 |
| tracing.oxsts | theta-cegar-expl-pred-combined | 2 | 2 | 0 |  | 0.008 | 1.119 | 0.007 |
| variable_dispatch.oxsts | theta-cegar-expl-pred-combined | 5 | 5 | 0 |  | 0.022 | 2.692 | 0.008 |
| Total |  | 244 | 244 | 0 | 58.046 | 27.548 | 264.646 | 0.094 |

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

## Optimizer passes (RQ2)

Each level disables the optional passes it names (`none` disables all of them) and is compared with `all`. Sizes are the flattened model's, summed over the cases sized at every level; times are sums of medians over the cases completed at every level, so a case that timed out at any level is left out of every level's sum and counted in that level's timeouts. Pass time and firings are those of the disabled passes, measured under `all`. The table sums every group; the data file also has each group. The two matrices give, per group and level, the flattened expressions and the model checking time relative to `all`.

Files: `optimizer-passes.dat`, `optimizer-passes.tex`.

| Level | Variables | Operations | Expressions | Var. ratio | Expr. ratio | Unfolding (s) | Unf. ratio | Checking (s) | Chk. ratio | Pass (s) | Firings | Timeouts | Errors | Wrong |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| all | 2835 | 15789 | 43023 | 1.000 | 1.000 | 27.548 | 1.000 | 264.646 | 1.000 | 0.000 | 0 | 0 | 0 | 0 |
| without-expression-simplification | 3313 | 18262 | 55925 | 1.169 | 1.300 | 25.797 | 0.936 | 266.301 | 1.006 | 0.642 | 132 | 0 | 0 | 0 |
| without-operation-flattening | 2835 | 16963 | 43023 | 1.000 | 1.000 | 25.748 | 0.935 | 257.264 | 0.972 | 0.510 | 78 | 0 | 0 | 0 |
| without-redundant-operation-removal | 2836 | 16735 | 43877 | 1.000 | 1.020 | 22.983 | 0.834 | 253.480 | 0.958 | 0.228 | 48 | 0 | 0 | 0 |
| without-assume-false-propagation | 3312 | 20124 | 47859 | 1.168 | 1.112 | 27.623 | 1.003 | 269.326 | 1.018 | 0.238 | 73 | 0 | 0 | 0 |
| without-constant-variable-substitution | 2840 | 15794 | 43054 | 1.002 | 1.001 | 25.583 | 0.929 | 263.646 | 0.996 | 0.511 | 5 | 0 | 0 | 0 |
| without-copy-propagation | 2839 | 15812 | 43064 | 1.001 | 1.001 | 26.315 | 0.955 | 270.537 | 1.022 | 3.055 | 7 | 0 | 0 | 0 |
| without-dead-store-elimination | 2835 | 15791 | 43026 | 1.000 | 1.000 | 23.821 | 0.865 | 257.158 | 0.972 | 0.077 | 63 | 0 | 0 | 0 |
| without-dead-code-removal | 2836 | 15790 | 43028 | 1.000 | 1.000 | 21.862 | 0.794 | 257.703 | 0.974 | 1.008 | 1 | 0 | 0 | 0 |
| without-variable-liveness | 3811 | 15830 | 44993 | 1.344 | 1.046 | 23.450 | 0.851 | 266.724 | 1.008 | 0.817 | 96 | 0 | 0 | 0 |
| none | 4287 | 24912 | 62063 | 1.512 | 1.443 | 17.875 | 0.649 | 281.244 | 1.063 | 7.087 | 503 | 0 | 0 | 0 |

| Model | Backend | all | without-expression-simplification | without-operation-flattening | without-redundant-operation-removal | without-assume-false-propagation | without-constant-variable-substitution | without-copy-propagation | without-dead-store-elimination | without-dead-code-removal | without-variable-liveness | none |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| abstract_redefinition.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.079 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.079 | 1.105 |
| arithmetic.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| arrays.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.075 | 1.000 | 1.000 | 1.000 | 1.031 | 1.009 | 1.009 | 1.016 | 1.183 | 1.276 |
| boolean_operators.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.006 | 1.006 |
| cast.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.095 | 1.190 |
| classes_and_inheritance.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.023 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.057 | 1.080 |
| comparison.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| control_flow.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.049 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.065 | 1.065 |
| crossroads.sysml | theta-cegar-expl | 1.000 | 1.319 | 1.000 | 1.020 | 1.120 | 1.000 | 1.000 | 1.000 | 1.000 | 1.044 | 1.466 |
| enums.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| globals.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| if_then_else.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.182 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.212 | 1.212 |
| initial_values.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| inline_calls.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| inline_operations.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| local_instances.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.148 | 1.000 | 1.130 | 1.026 | 1.000 | 1.065 | 1.000 | 1.000 | 1.213 | 1.470 |
| local_vars.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.024 | 1.000 | 1.000 | 1.008 | 1.024 |
| navigation.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.051 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.077 | 1.077 |
| opposites.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| optimizer_soundness.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.014 | 1.014 |
| parametric.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.088 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.113 | 1.125 |
| parity.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| primitives.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| property_calls.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.091 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.152 | 1.152 |
| records.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.052 | 1.000 | 1.000 | 1.000 | 1.091 | 1.000 | 1.000 | 1.000 | 1.138 | 1.164 |
| runtime_for.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| temporal.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| tracing.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |
| variable_dispatch.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.133 | 1.000 | 1.022 | 1.022 | 1.000 | 1.222 | 1.000 | 1.000 | 1.111 | 1.422 |

| Model | Backend | all | without-expression-simplification | without-operation-flattening | without-redundant-operation-removal | without-assume-false-propagation | without-constant-variable-substitution | without-copy-propagation | without-dead-store-elimination | without-dead-code-removal | without-variable-liveness | none |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| abstract_redefinition.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.996 | 0.997 | 1.005 | 1.068 | 0.927 | 0.926 | 0.926 | 0.966 | 1.240 | 1.249 |
| arithmetic.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.114 | 1.064 | 1.256 | 1.092 | 1.152 | 1.319 | 1.233 | 1.114 | 1.168 | 1.161 |
| arrays.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.989 | 1.026 | 0.983 | 0.939 | 0.943 | 0.963 | 0.994 | 1.053 | 1.025 | 1.071 |
| boolean_operators.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.015 | 0.989 | 0.959 | 0.932 | 0.938 | 1.137 | 0.978 | 1.037 | 1.055 | 1.059 |
| cast.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.853 | 0.848 | 0.858 | 0.961 | 0.909 | 0.834 | 0.986 | 0.873 | 0.844 | 0.837 |
| classes_and_inheritance.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.035 | 0.994 | 1.002 | 0.982 | 1.016 | 1.005 | 0.968 | 1.162 | 1.255 |
| comparison.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.968 | 0.965 | 0.984 | 0.963 | 1.060 | 0.987 | 0.975 | 0.943 | 0.931 | 0.920 |
| control_flow.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.951 | 0.987 | 1.015 | 0.988 | 0.956 | 0.928 | 0.980 | 1.124 | 1.014 | 1.072 |
| crossroads.sysml | theta-cegar-expl | 1.000 | 1.006 | 0.942 | 0.900 | 1.044 | 1.004 | 1.017 | 0.932 | 0.938 | 0.963 | 1.071 |
| enums.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.969 | 0.982 | 0.971 | 0.974 | 0.970 | 0.964 | 0.977 | 0.965 | 0.958 | 0.984 |
| globals.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.021 | 1.014 | 0.991 | 1.207 | 1.275 | 1.124 | 1.074 | 1.049 | 1.042 | 1.032 |
| if_then_else.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.039 | 0.641 | 0.638 | 0.655 | 0.633 | 0.814 | 0.867 | 0.781 | 0.803 | 0.706 |
| initial_values.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.853 | 0.911 | 0.904 | 1.145 | 0.983 | 1.066 | 0.951 | 0.895 | 0.994 | 0.933 |
| inline_calls.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.998 | 0.981 | 0.998 | 1.060 | 0.993 | 0.998 | 1.001 | 0.997 | 1.001 | 0.982 |
| inline_operations.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.010 | 1.004 | 1.001 | 1.019 | 1.004 | 1.000 | 1.001 | 1.013 | 1.015 | 1.003 |
| local_instances.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.261 | 1.257 | 1.255 | 1.064 | 0.978 | 1.169 | 0.974 | 0.994 | 1.275 | 1.268 |
| local_vars.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.019 | 1.004 | 1.015 | 1.005 | 1.002 | 1.011 | 1.001 | 1.001 | 0.998 | 0.992 |
| navigation.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.019 | 1.020 | 1.004 | 1.006 | 1.009 | 1.026 | 1.009 | 1.002 | 1.325 | 1.311 |
| opposites.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.960 | 0.970 | 0.959 | 0.976 | 0.989 | 0.984 | 0.987 | 0.987 | 0.964 | 0.973 |
| optimizer_soundness.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.011 | 1.010 | 0.997 | 1.008 | 1.004 | 1.007 | 0.999 | 1.011 | 1.009 |
| parametric.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.987 | 0.985 | 0.991 | 0.989 | 0.986 | 0.985 | 0.994 | 0.995 | 1.125 | 1.129 |
| parity.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.000 | 1.013 | 1.002 | 1.004 | 1.006 | 1.002 | 1.015 | 1.008 | 1.010 | 1.003 |
| primitives.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.018 | 1.001 | 0.995 | 1.007 | 1.004 | 1.007 | 0.999 | 1.012 | 1.001 | 1.008 |
| property_calls.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.988 | 1.001 | 1.022 | 0.981 | 0.991 | 0.986 | 0.983 | 0.992 | 1.629 | 1.644 |
| records.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.012 | 1.005 | 1.009 | 1.011 | 1.021 | 1.004 | 1.018 | 1.013 | 1.025 | 1.031 |
| runtime_for.oxsts | theta-cegar-expl-pred-combined | 1.000 | 1.003 | 1.011 | 1.008 | 1.008 | 1.004 | 1.000 | 1.000 | 0.996 | 1.007 | 1.002 |
| temporal.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.975 | 0.989 | 0.988 | 0.980 | 0.988 | 0.984 | 0.990 | 0.984 | 0.983 | 0.980 |
| tracing.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.985 | 0.956 | 0.978 | 0.994 | 0.979 | 0.981 | 0.952 | 0.981 | 0.976 | 1.002 |
| variable_dispatch.oxsts | theta-cegar-expl-pred-combined | 1.000 | 0.995 | 0.980 | 0.996 | 0.996 | 0.994 | 1.032 | 0.981 | 0.999 | 0.992 | 1.005 |
