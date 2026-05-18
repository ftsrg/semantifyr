/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Creates a Logger with the current [javaClass] using a delegate property pattern.
 * Usage: val logger by loggerFactory()
 */
fun <R : Any> R.loggerFactory(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(javaClass) }
}

/**
 * Inlined helper logging only if trace is enabled.
 */
inline fun Logger.trace(msgSupplier: () -> String) {
    if (this.isTraceEnabled) {
        trace(msgSupplier())
    }
}

/**
 * Inlined helper logging only if trace is enabled.
 */
inline fun Logger.trace(throwable: Throwable, msgSupplier: () -> String) {
    if (this.isTraceEnabled) {
        trace(msgSupplier(), throwable)
    }
}

/**
 * Inlined helper logging only if debug is enabled.
 */
inline fun Logger.debug(msgSupplier: () -> String) {
    if (this.isDebugEnabled) {
        debug(msgSupplier())
    }
}

/**
 * Inlined helper logging only if debug is enabled.
 */
inline fun Logger.debug(throwable: Throwable, msgSupplier: () -> String) {
    if (this.isDebugEnabled) {
        debug(msgSupplier(), throwable)
    }
}

/**
 * Inlined helper logging only if info is enabled.
 */
inline fun Logger.info(msgSupplier: () -> String) {
    if (this.isInfoEnabled) {
        info(msgSupplier())
    }
}

/**
 * Inlined helper logging only if info is enabled.
 */
inline fun Logger.info(throwable: Throwable, msgSupplier: () -> String) {
    if (this.isInfoEnabled) {
        info(msgSupplier(), throwable)
    }
}

/**
 * Inlined helper logging only if warn is enabled.
 */
inline fun Logger.warn(msgSupplier: () -> String) {
    if (this.isWarnEnabled) {
        warn(msgSupplier())
    }
}

/**
 * Inlined helper logging only if warn is enabled.
 */
inline fun Logger.warn(throwable: Throwable, msgSupplier: () -> String) {
    if (this.isWarnEnabled) {
        warn(msgSupplier(), throwable)
    }
}

/**
 * Inlined helper logging only if error is enabled.
 */
inline fun Logger.error(msgSupplier: () -> String) {
    if (this.isErrorEnabled) {
        error(msgSupplier())
    }
}

/**
 * Inlined helper logging only if error is enabled.
 */
inline fun Logger.error(throwable: Throwable, msgSupplier: () -> String) {
    if (this.isErrorEnabled) {
        error(msgSupplier(), throwable)
    }
}
