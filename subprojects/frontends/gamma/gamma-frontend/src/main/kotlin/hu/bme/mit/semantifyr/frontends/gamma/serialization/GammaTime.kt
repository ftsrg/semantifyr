/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ClockDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SetTimeoutAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeUnit
import org.eclipse.xtext.EcoreUtil2

object GammaTime {

    private const val MILLISECONDS_PER_SECOND = 1000

    // the greatest unit every time value of the model is a whole number of, so the clock bounds stay as small as the model allows
    @JvmStatic
    fun unitMilliseconds(gammaModel: GammaModelPackage): Int {
        val packages = listOf(gammaModel) + reachablePackages(gammaModel)
        val values = packages.flatMap {
            timeValuesMilliseconds(it)
        }.filter {
            it != 0
        }
        if (values.isEmpty()) {
            return 1
        }
        return values.fold(0) { divisor, value ->
            divisor.toBigInteger().gcd(value.toBigInteger()).toInt()
        }
    }

    fun milliseconds(value: Int, unit: TimeUnit): Int {
        if (unit == TimeUnit.SECOND) {
            return Math.multiplyExact(value, MILLISECONDS_PER_SECOND)
        }
        return value
    }

    fun inUnit(value: Int, unit: TimeUnit, unitMilliseconds: Int): Int {
        val valueMilliseconds = milliseconds(value, unit)
        require(valueMilliseconds % unitMilliseconds == 0) {
            "The time value $valueMilliseconds ms is no whole number of the unit $unitMilliseconds ms"
        }
        return valueMilliseconds / unitMilliseconds
    }

    private fun timeValuesMilliseconds(gammaModel: GammaModelPackage): List<Int> {
        val timeouts = EcoreUtil2.getAllContentsOfType(gammaModel, SetTimeoutAction::class.java).mapNotNull { setting ->
            GammaUtils.evaluateInteger(setting.value)?.let {
                milliseconds(Math.absExact(it), setting.unit)
            }
        }
        val clocks = EcoreUtil2.getAllContentsOfType(gammaModel, ClockDeclaration::class.java).mapNotNull { clock ->
            GammaUtils.evaluateInteger(clock.value)?.let {
                milliseconds(Math.absExact(it), clock.unit)
            }
        }
        return timeouts + clocks
    }

    // every package the model reaches through its imports or its own name
    fun reachablePackages(gammaModel: GammaModelPackage): List<GammaModelPackage> {
        val reached = linkedSetOf(gammaModel)
        val pending = ArrayDeque(listOf(gammaModel))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val importedPackages = current.imports.map {
                it.importedPackage
            }
            val packageNames = importedPackages.map {
                it.name
            }.toSet() + current.name
            val loadedPackages = current.eResource().resourceSet.resources.flatMap {
                it.contents
            }.filterIsInstance<GammaModelPackage>().filter {
                it.name in packageNames
            }
            for (candidate in importedPackages + loadedPackages) {
                if (reached.add(candidate)) {
                    pending += candidate
                }
            }
        }
        return reached.drop(1)
    }
}
