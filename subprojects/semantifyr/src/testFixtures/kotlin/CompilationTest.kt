/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr

import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.XstsSerializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.XstsTransformer
import org.junit.jupiter.api.Assertions
import java.io.File
import java.nio.charset.Charset

open class CompilationTest {

    private val commentsRegex = Regex("""/\*[^*]*\*+(?:[^/*][^*]*\*+)*/""")

    fun simpleReadTransformWrite(directory: String, library: String = "", rewriteChoice: Boolean = true) {
        File("$directory/model.xsts").delete()

        val reader = OxstsReader(library)
        reader.readDirectory(directory)

        val transformer = XstsTransformer(reader)
        val xsts = transformer.transform("Mission", rewriteChoice)
        val serializedXsts = XstsSerializer.serialize(xsts)

        File("$directory/model.xsts").writeText(serializedXsts)
    }

    fun assertModelEqualsExpected(directory: String) {
        val expected = File("$directory/expected.xsts")
            .readTextOrEmpty()
            .replace("\r\n", "\n")
            .replace(commentsRegex, "")
            .trim()
        val actual = File("$directory/model.xsts")
            .readTextOrEmpty()
            .replace("\r\n", "\n")
            .replace(commentsRegex, "")
            .trim()
        Assertions.assertEquals(expected, actual)
    }

    private fun File.readTextOrEmpty(charset: Charset = Charsets.UTF_8) = if (exists()) {
        readText(charset)
    } else {
        "$$\$FILE_DOES_NOT_EXIST$$$"
    }

}
