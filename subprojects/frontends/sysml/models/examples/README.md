# Test Models

This directory contains SysML v2 test models used for testing the Semantifyr SysML v2 frontend.

The artifacts produced by Semantifyr will reside in the `./out` directory.

| Model file                 | Origin                                                                                                       | Description                                                                                                                                |
|----------------------------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| semanticstest.sysml        | Original                                                                                                     | Semantic validation test suite designed designed for Semantifyr                                                                            |
| topdowntransition.sysml    | Original                                                                                                     | Top-down transition priority testing model, use the `topdown` library variant!                                                             |
| crossroads.sysml           | [Gamma tutorial](https://github.com/ftsrg/gamma/tree/master/tutorial/hu.bme.mit.gamma.tutorial.finish/model) | Rewritten version of the Gamma Crossroads tutorial model                                                                                   |
| spacecraft.sysml           | OpenMBEE Simple Space Mission                                                                                | The Simple Space Mission's spacecraft model rewritten into SysML v2                                                                        |
| compressedspacecraft.sysml | OpenMBEE Simple Space Mission                                                                                | Same as spacecraft but the battery and data variables are incremented/decremented by 10 instead of 1                                       |
| door_access.sysml          | -                                                                                                            | Sequential cyclic workflow, do-actions on transitions                                                                                      |
| power_subsystems.sysml     | -                                                                                                            | Guarded self-transitions, entry actions, terminal absorbing state                                                                          |
| aircraft_engine.sysml      | -                                                                                                            | Large flat state machine (13 states), boolean guard, structurally unreachable state                                                        |
| autonomous_driving.sysml   | Paper: "Automatic Formal Verification of SysML State Machine Diagrams for Vehicular Control Systems"         | Model based on paper "Automatic Formal Verification of SysML State Machine Diagrams for Vehicular Control Systems" from Clemson University |
| orion_protocol.sysml       | Prolan                                                                                                       | Bidirectional protocol, `accept when true`, timed keep-alive, cross-hierarchy transitions                                                  |

The verification cases were either added as natural "state reachability" properties, or were derived as safety properties from the model.

`manifest.json` lists every verification case with its expected verdict (`Unknown` cases are benchmarked, not asserted), the cheapest test tier that runs it, whether it is a falsification twin, and the engines observed to decide it fast (a lower bound, empty where none was measured). The SysML frontend's tests read it, and `SysMLCorpusTest` fails when it drifts from the models.
