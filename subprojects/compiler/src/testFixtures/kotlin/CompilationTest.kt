/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler

import hu.bme.mit.semantifyr.oxsts.compiler.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.compiler.serialization.Serializer
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.XstsTransformer
import org.junit.jupiter.api.Assertions
import java.io.File
import java.nio.charset.Charset

open class CompilationTest {

    fun simpleReadTransformWrite(directory: String, library: String = "", rewriteChoice: Boolean = true) {
        File("$directory/model.xsts").delete()

        val reader = OxstsReader(directory, library)
        reader.read()

        val transformer = XstsTransformer(reader)
        val xsts = transformer.transform("Mission", rewriteChoice)
        val serializedXsts = Serializer.serialize(xsts)

        File("$directory/model.xsts").writeText(serializedXsts)
    }

    fun assertModelEqualsExpected(directory: String) {
        val expected = File("$directory/expected.xsts")
            .readTextOrEmpty()
            .replace("\r\n", "\n")
            .removePrefix(
                """
                    /*
                     * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
                     *
                     * SPDX-License-Identifier: EPL-2.0
                     */
                """.trimIndent(),
            ).trim()
        val actual = File("$directory/model.xsts")
            .readTextOrEmpty()
            .replace("\r\n", "\n")
            .trim()
        Assertions.assertEquals(expected, actual)
    }

    private fun File.readTextOrEmpty(charset: Charset = Charsets.UTF_8) = if (exists()) {
        readText(charset)
    } else {
        "$$\$FILE_DOES_NOT_EXIST$$$"
    }

}
