package hu.bme.mit.semantifyr.semantics.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun <R : Any> R.loggerFactory(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(javaClass) }
}

inline fun Logger.trace(msgSupplier: () -> String) {
    if (this.isTraceEnabled) {
        trace(msgSupplier())
    }
}

inline fun Logger.debug(msgSupplier: () -> String) {
    if (this.isDebugEnabled) {
        debug(msgSupplier())
    }
}

inline fun Logger.info(msgSupplier: () -> String) {
    if (this.isInfoEnabled) {
        info(msgSupplier())
    }
}

inline fun Logger.warn(msgSupplier: () -> String) {
    if (this.isWarnEnabled) {
        warn(msgSupplier())
    }
}

inline fun Logger.error(msgSupplier: () -> String) {
    if (this.isErrorEnabled) {
        error(msgSupplier())
    }
}
