# Test Models

This directory contains SysML v2 test models used for testing the Semantifyr SysML v2 frontend.

The artifacts produced by Semantifyr will reside in the `./out` directory.

| Model file                 | Description                                                                                                                                |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| semanticstest.sysml        | Semantic validation test suite designed designed for Semantifyr                                                                            |
| topdowntransition.sysml    | Top-down transition priority testing model, use the `topdown` library variant!                                                             |
| fifoqueue.sysml            | Per-port FIFO delivery testing model, use the `fifo` library variant                                                                       |
| crossroads.sysml           | Rewritten version of the Gamma Crossroads tutorial model                                                                                   |
| spacecraft.sysml           | The Simple Space Mission's spacecraft model rewritten into SysML v2                                                                        |
| compressedspacecraft.sysml | Same as spacecraft but the battery and data variables are incremented/decremented by 10 instead of 1                                       |
| door_access.sysml          | Sequential cyclic workflow, do-actions on transitions                                                                                      |
| power_subsystems.sysml     | Guarded self-transitions, entry actions, terminal absorbing state                                                                          |
| aircraft_engine.sysml      | Large flat state machine (13 states), boolean guard, structurally unreachable state                                                        |
| autonomous_driving.sysml   | Model based on paper "Automatic Formal Verification of SysML State Machine Diagrams for Vehicular Control Systems" from Clemson University |
| orion_protocol.sysml       | Bidirectional protocol, `accept when true`, timed keep-alive, cross-hierarchy transitions                                                  |
| level_crossing.sysml       | Two tracks, a controller and a barrier; the controller forgets a second train                                                              |

The verification cases were either added as natural "state reachability" properties, or were derived as safety properties from the model.

`manifest.json` lists every verification case with its expected verdict (`Unknown` cases are benchmarked, not asserted), the cheapest test tier that runs it and whether it is a falsification twin. The SysML frontend's tests and the vscode-server image read it, and `SysMLCorpusTest` fails when it drifts from the models. The models live serves, with their origins and rights, are in the examples registry, `subprojects/semantifyr-editor-common/src/examples/examples.json`.
