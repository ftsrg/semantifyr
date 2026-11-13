/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory
import org.apache.logging.log4j.core.impl.Log4jLogEvent
import org.apache.logging.log4j.message.SimpleMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.inputStream

class LogLayoutTest {

    private val layout: Layout<*> by lazy {
        val configuration = Path("src/main/resources/log4j2.properties").inputStream().use {
            PropertiesConfigurationFactory().getConfiguration(LoggerContext("launcher"), ConfigurationSource(it))
        }
        configuration.initialize()
        configuration.getAppender<ConsoleAppender>("STDERR").layout
    }

    private fun format(
        level: Level,
        message: String,
        thrown: Throwable? = null,
    ): String {
        val event = Log4jLogEvent.newBuilder()
            .setLoggerName("hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader")
            .setLevel(level)
            .setMessage(SimpleMessage(message))
            .setThrown(thrown)
            .build()
        return layout.toSerializable(event).toString()
    }

    @Test
    fun `a warning is one line with its word and message and nothing of the log`() {
        assertThat(format(Level.WARN, "Skipped notes.txt, which is not an OXSTS file")).isEqualTo("warning: Skipped notes.txt, which is not an OXSTS file${System.lineSeparator()}")
    }

    @Test
    fun `an error with an exception is one line that ends with the exception's message`() {
        assertThat(format(Level.ERROR, "The trace was not written", IOException("disk full"))).isEqualTo("error: The trace was not written: disk full${System.lineSeparator()}")
    }

    @Test
    fun `a line below the default level keeps the time, the thread and the class`() {
        assertThat(format(Level.INFO, "compile model=smoke.oxsts")).contains("[INFO ]").contains("SemantifyrLoader - compile model=smoke.oxsts")
    }
}
