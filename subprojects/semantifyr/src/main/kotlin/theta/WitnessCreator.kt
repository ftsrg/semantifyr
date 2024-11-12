/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.theta

import hu.bme.mit.semantifyr.cex.lang.cex.Cex
import hu.bme.mit.semantifyr.cex.lang.cex.ExplState
import hu.bme.mit.semantifyr.cex.lang.cex.ExplVariableState
import hu.bme.mit.semantifyr.cex.lang.cex.ExplVariableValue
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralBoolean
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralEnum
import hu.bme.mit.semantifyr.cex.lang.cex.LiteralInteger
import hu.bme.mit.semantifyr.cex.lang.cex.State
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Target
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.Namings
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.NothingInstance
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findInitTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findMainTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.fullyQualifiedName
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.resolvePath
import java.io.File

fun ExplVariableValue.toExpression(target: Target): Expression {
    return when (this) {
        is LiteralEnum -> toExpression(target)
        is LiteralBoolean -> OxstsFactory.createLiteralBoolean(isValue)
        is LiteralInteger -> OxstsFactory.createLiteralInteger(value)
        else -> error("Unkown VariableValue: $this")
    }
}

fun LiteralEnum.toExpression(target: Target): Expression {
    val instanceFullyQualifiedName = value.removeSuffix(Namings.LITERAL_SUFFIX)

    if (instanceFullyQualifiedName == NothingInstance.fullyQualifiedName) {
        return OxstsFactory.createChainReferenceExpression(OxstsFactory.createNothingReference())
    }

    return target.resolvePath(instanceFullyQualifiedName)
}

class OxstsVariableState(
    variableState: ExplVariableState,
    target: Target
) {
    val reference = target.resolvePath(variableState.variable)
    val value = variableState.value.toExpression(target)
}
fun ExplVariableState.toOxstsVariableState(target: Target) = OxstsVariableState(this, target)

class OxstsWitnessState(
    state: ExplState,
    target: Target
) {
    val variableStates = state.variableStates.map {
        it.toOxstsVariableState(target)
    }
}
fun ExplState.toOxstsWitnessState(target: Target) = OxstsWitnessState(this, target)

fun State.toOxstsWitnessState(target: Target): OxstsWitnessState {
    return when (this) {
        is ExplState -> toOxstsWitnessState(target)
        else -> error("")
    }
}

fun OxstsVariableState.toAssumption(): AssumptionOperation {
    return OxstsFactory.createEqualityAssumption(reference, value)
}

fun OxstsWitnessState.toAssumptionOperations(): List<AssumptionOperation> {
    return variableStates.map {
        it.toAssumption()
    }
}

class Witness(
    cex: Cex,
    target: Target
) {

//    val initialState = cex.states.single {
//        it.isPreInit
//    }.state.toOxstsWitnessState(target)

    val initializedState = cex.states.first {
        it.isPostInit && it.isLastInternal
    }.state.toOxstsWitnessState(target)

    val transitionStates = cex.states.asSequence().drop(1).filter { // first state is the init transition
        it.isPostInit && it.isLastInternal
    }.map {
        it.state.toOxstsWitnessState(target)
    }

}

fun Cex.toWitness(target: Target) = Witness(this, target)

class WitnessMapping(
    val target: Target
) {

    val witnessPackage by lazy {
        OxstsFactory.createPackage().also {
            it.name = "Witness"
        }
    }

    val witnessType by lazy {
        OxstsFactory.createTarget().also {
            witnessPackage.types += it

            it.name = "${target.name}_Witness"
            it.supertype = target
        }
    }

    val stateVariable by lazy {
        OxstsFactory.createVariable().also {
            witnessType.variables += it

            it.name = "state"
            it.typing = OxstsFactory.createIntegerType()
            it.expression = OxstsFactory.createLiteralInteger(-1)
        }
    }

    fun transform(cex: Cex) {
        val witness = cex.toWitness(target)

        witnessType.initTransition += OxstsFactory.createSequentialTransition {
            operation += OxstsFactory.createInlineCall(OxstsFactory.createChainReferenceExpression(target.findInitTransition()), isStatic = true)

            operation += witness.initializedState.toAssumptionOperations()
        }

        witnessType.mainTransition += OxstsFactory.createSequentialTransition {
            for (transitionState in witness.transitionStates) {
                operation += OxstsFactory.createInlineCall(OxstsFactory.createChainReferenceExpression(target.findMainTransition()), isStatic = true)

                operation += transitionState.toAssumptionOperations()
            }
        }
    }

}

class WitnessCreator(
    private val reader: OxstsReader
) {

    fun createWitness(targetName: String, cex: Cex): hu.bme.mit.semantifyr.oxsts.model.oxsts.Package {
        val target = reader.rootElements.flatMap { it.types }.filterIsInstance<Target>().first {
            it.name == targetName
        }

        val mapping = WitnessMapping(target)

        mapping.transform(cex)

        return mapping.witnessPackage
    }

}


fun main() {
    prepareCex()
    val reader = CexReader()
    val cex = reader.readCexFile(File("C:\\Users\\Armin\\work\\ftsrg\\semantifyr\\subprojects\\semantifyr\\TestModels\\Automated\\Gamma\\Spacecraft\\artifacts\\Spacecraft_batteryCharge_100_Unsafe\\Spacecraft_batteryCharge_100_Unsafe0.cex"))



}
