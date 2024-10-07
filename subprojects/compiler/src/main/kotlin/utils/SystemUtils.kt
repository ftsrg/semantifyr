package hu.bme.mit.semantifyr.oxsts.compiler.utils

import kotlin.reflect.KProperty

open class EnvVar {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return System.getenv(property.name)
    }

}

@Suppress("ClassName")
object environment : EnvVar()
