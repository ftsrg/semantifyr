const documentSources = {
  sysmlModelOxsts: String.raw`package semantifyr::sysml::model

import semantifyr::sysml::expressions
import semantifyr::sysml::attributes
import semantifyr::sysml::states
import semantifyr::sysml::parts
import semantifyr::sysml::ports
import semantifyr::sysml::items
import semantifyr::sysml::actions
import semantifyr::sysml::triggers
import semantifyr::sysml::verification

class ApproachSignal : Item
class NearSignal : Item
class EnterSignal : Item
class LeaveSignal : Item
class TrainApproaching : Item
class TrainLeft : Item
class Lower : Item
class Lift : Item

class Sensors : NormalPort
class ConjugatedSensors : ConjugatedPort
class TrackReports : NormalPort
class ConjugatedTrackReports : ConjugatedPort

class Track : Part {
    contains sensors: ConjugatedSensors
    contains controller: TrackReports

    contains Behaviour: State {
        contains Free: State
        contains Approaching: State
        contains Near: State
        contains InCrossing: State

        contains UnnamedTransitionUsage1: Transition {
            redefine refers from: StateNode = Free
            redefine refers to: StateNode = Approaching
            contains acceptAction: AcceptAction {
                redefine refers port: Port = sensors
                redefine refers itemType: Item = global_ApproachSignal
            }
            contains effect: SendAction {
                redefine refers port: Port = controller
                redefine refers item: Item = global_TrainApproaching
            }
        }
        contains UnnamedTransitionUsage2: Transition {
            redefine refers from: StateNode = Approaching
            redefine refers to: StateNode = Near
        }
    }
}

class Controller : Part {
    contains trackA: ConjugatedTrackReports
    contains trackB: ConjugatedTrackReports
    contains barrier: BarrierCommands

    contains Behaviour: State {
        contains Idle: State
        contains TrainPresent: State
    }
}

class LevelCrossing : Part {
    contains trackA: Track
    contains trackB: Track
    contains controller: Controller
    contains barrier: Barrier
}

@VerificationCase
class BarrierDownWhileTrainInCrossing : Verification {
    contains system: LevelCrossing

    redefine prop {
        return AG (!(system.trackA.Behaviour.InCrossing.isActive() || system.trackB.Behaviour.InCrossing.isActive()) || system.barrier.Behaviour.Down.isActive())
    }
}

@VerificationCase
class TwoTrainsCanBeInTheCrossing : Verification {
    contains system: LevelCrossing

    redefine prop {
        return EF (system.trackA.Behaviour.InCrossing.isActive() && system.trackB.Behaviour.InCrossing.isActive())
    }
}
`,
  sysmlWitnessOxsts: String.raw`witness of semantifyr::sysml::model::BarrierDownWhileTrainInCrossing

var step : int := 0

contains root : semantifyr::sysml::model::BarrierDownWhileTrainInCrossing

init {
    assume (step == 0)
    inline root.init()
    assume (root.system.trackA.Behaviour.activeState == root.system.trackA.Behaviour.Free)
    assume (root.system.trackB.Behaviour.activeState == root.system.trackB.Behaviour.Free)
    assume (root.system.controller.Behaviour.activeState == root.system.controller.Behaviour.Idle)
    assume (root.system.barrier.Behaviour.activeState == root.system.barrier.Behaviour.Up)
    step := 1
}

tran {
    assume (step == 1)
    inline root.main()
    assume (root.system.trackB.sensors.incomingItem == global_ApproachSignal)
    assume (root.system.trackB.Behaviour.activeState == root.system.trackB.Behaviour.Approaching)
    assume (root.system.trackB.controller.outgoingItem == global_TrainApproaching)
    step := 2
} or {
    assume (step == 2)
    inline root.main()
    assume (root.system.trackB.Behaviour.activeState == root.system.trackB.Behaviour.Near)
    assume (root.system.controller.Behaviour.activeState == root.system.controller.Behaviour.TrainPresent)
    assume (root.system.controller.barrier.outgoingItem == global_Lower)
    step := 3
} or {
    assume (step == 3)
    inline root.main()
    assume (root.system.trackB.Behaviour.activeState == root.system.trackB.Behaviour.InCrossing)
    assume (root.system.barrier.Behaviour.activeState == root.system.barrier.Behaviour.Down)
    step := 4
} or {
    assume (step == 4)
    inline root.main()
    assume (root.system.trackA.Behaviour.activeState == root.system.trackA.Behaviour.Approaching)
    assume (root.system.trackB.Behaviour.activeState == root.system.trackB.Behaviour.Free)
    assume (root.system.trackA.controller.outgoingItem == global_TrainApproaching)
    assume (root.system.trackB.controller.outgoingItem == global_TrainLeft)
    step := 5
} or {
    assume (step == 5)
    inline root.main()
    assume (root.system.trackA.Behaviour.activeState == root.system.trackA.Behaviour.Near)
    assume (root.system.controller.Behaviour.activeState == root.system.controller.Behaviour.Idle)
    assume (root.system.controller.barrier.outgoingItem == global_Lift)
    step := 6
} or {
    assume (step == 6)
    inline root.main()
    assume (root.system.trackA.Behaviour.activeState == root.system.trackA.Behaviour.InCrossing)
    assume (root.system.barrier.Behaviour.activeState == root.system.barrier.Behaviour.Up)
    step := 7
}
`,
  sysmlStatesLibrary: String.raw`package semantifyr::sysml::states

import semantifyr::sysml::actions
import semantifyr::sysml::triggers
import semantifyr::sysml::expressions

abstract class StateNode {
    derived refers incomingTransitions: Transition[0..*] opposite to
    derived refers outgoingTransitions: Transition[0..*] opposite from

    abstract prop isActive(): bool

    abstract tran enter(commonState: StateNode)
    abstract tran exit(commonState: StateNode)
}

abstract class AbstractState : StateNode {
    container parent: AbstractState[0..1] opposite states
    contains states: AbstractState[0..*] opposite parent

    contains entryAction: Action[1]
    contains exitAction: Action[1]

    redefine prop isActive(): bool {
        return parent?.isInnerStateActive(self)
    }

    abstract prop getActiveState(): AbstractState[0..1]

    abstract prop isInnerStateActive(state: AbstractState): bool

    abstract tran fireTransitions()
    abstract tran assumeNoTransitionEnabled()
    abstract tran activateRecursive()
    abstract tran deactivateRecursive(deactivate: bool)
    abstract tran passTime()

    abstract tran enterRecursive(commonState: StateNode)
    abstract tran exitRecursive(commonState: StateNode)

    abstract tran setActiveState(state: AbstractState[0..1])
}

class State : AbstractState {
    contains start: EntryState[1]

    contains transitions: Transition[0..*] opposite parentState

    var activeState: states[0..1] := nothing

    redefine prop isInnerStateActive(state: AbstractState): bool {
        return state == activeState
    }

    redefine prop getActiveState(): AbstractState[0..1] {
        return activeState
    }
    
    redefine tran setActiveState(state: AbstractState) {
        activeState := state
    }

    redefine tran fireTransitions() {
        choice {
            inline assumeNoInnerTransitionEnabled()
            inline fireLocalTransitions()
        } or {
            inline fireInnerTransitions()
        }
    }
    tran fireLocalTransitions() {
        inline for choice (transition in transitions) {
            inline transition.fire()
        } else {
            assume (false)
        }
    }
    tran fireInnerTransitions() {
        inline for choice (state in states) {
            inline state.fireTransitions()
        } else {
            assume (false)
        }
    }

    redefine tran assumeNoTransitionEnabled() {
        inline assumeNoLocalTransitionEnabled()
        inline assumeNoInnerTransitionEnabled()
    }
    tran assumeNoLocalTransitionEnabled() {
        inline for (transition in transitions) {
            assume (!transition.isEnabled())
        }
    }
    tran assumeNoInnerTransitionEnabled() {
        inline for (state in states) {
`,
  sysmlScalarValues: String.raw`standard library package ScalarValues {
    doc
    /*
     * This package contains a basic set of primitive scalar (non-collection) values.
     */

    private import Base::DataValue;

    abstract datatype ScalarValue specializes DataValue;
    datatype Boolean specializes ScalarValue;
    datatype String specializes ScalarValue;

    abstract datatype NumericalValue specializes ScalarValue;
    abstract datatype Number specializes NumericalValue;
    datatype Complex specializes Number;
    datatype Real specializes Complex;
    datatype Rational specializes Real;
    datatype Integer specializes Rational;
    datatype Natural specializes Integer;
    datatype Positive specializes Natural;
}
`,
  gammaModelOxsts: String.raw`package semantifyr::gamma::model

import semantifyr::gamma::statecharts
import semantifyr::gamma::ports
import semantifyr::gamma::verification

class Sensors : Interface {
    contains approach: Event
    contains near: Event
    contains enter: Event
    contains leave: Event
}

class Track : Statechart {
    contains sensors: RequiredPort
    contains controller: ProvidedPort

    contains Main: Region {
        contains Free: State
        contains Approaching: State
        contains Near: State
        contains InCrossing: State

        contains transition2: Transition {
            redefine refers from: StateNode = Free
            redefine refers to: StateNode = Approaching
            redefine refers trigger: Event = sensors.interface.approach
            contains effect: RaiseAction {
                redefine refers event: Event = controller.interface.trainApproaching
            }
        }
    }
}

class LevelCrossing : SyncComponent {
    contains trackA: Track
    contains trackB: Track
    contains controller: Controller
    contains barrier: Barrier
}

@VerificationCase
class BarrierDownWhileTrainInCrossing : Verification {
    contains system: LevelCrossing
}
`,
  gammaStatecharts: String.raw`package semantifyr::gamma::statecharts

import semantifyr::gamma::ports

class Region {
    contains states: State[0..*]
    contains transitions: Transition[0..*]

    var activeState: State[0..1] := nothing

    tran fireTransitions() {
        inline for choice (transition in transitions) {
            inline transition.fire()
        } else {
            assume (false)
        }
    }
}

class Transition {
    refers from: State
    refers to: State
    refers trigger: Event[0..1]
    contains effect: Action[0..1]

    prop isEnabled(): bool {
        return from.isActive() && (trigger == nothing || trigger.isRaised)
    }

    @Trace
    tran fire() {
        assume (isEnabled())
        from.exit()
        inline effect?.run()
        to.enter()
    }
}
`,
  oxstsLibrary: String.raw`package semantifyr

annotation VerificationCase
annotation Trace

abstract class Anything {
    abstract tran init()
    abstract tran main()
}

class Verification : Anything {
    var step: int := 0

    tran passTime() {
        step := step + 1
    }
}

enum Result {
    Passed,
    Failed,
}
`,
};
