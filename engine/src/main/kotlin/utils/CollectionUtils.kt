package hu.bme.mit.semantifyr.oxsts.engine.utils

inline fun <reified T, reified C : Set<T>> C.except(other: T) = filter { it != other }.toSet()
