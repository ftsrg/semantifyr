/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText

@Serializable
private data class TermsFile(
    val terms: Map<String, Term>,
    val outcomes: Map<String, Term>,
)

@Serializable
private data class Term(
    val term: String,
    val rejected: List<String>,
)

private data class RejectedWord(
    val word: String,
    val term: String,
    val pattern: Regex,
)

class PrintedText(
    val file: String,
    val line: Int,
    val text: String,
)

// the strings of a Kotlin or Java source, its comments and character literals left out, a template's expression standing as a space
class SourceTexts(
    private val source: String,
) {
    private var index = 0
    private var line = 1
    private val texts = mutableListOf<Pair<Int, String>>()

    fun read(): List<Pair<Int, String>> {
        readCode(null)
        return texts
    }

    private fun peek(offset: Int = 0): Char? {
        return source.getOrNull(index + offset)
    }

    private fun advance(): Char {
        val character = source[index]
        index++
        if (character == '\n') {
            line++
        }
        return character
    }

    // reads code up to the brace that closes a template's expression, or to the end
    private fun readCode(closing: Char?) {
        var depth = 0
        while (index < source.length) {
            val character = peek()
            when {
                character == '/' && peek(1) == '/' -> {
                    while (index < source.length && peek() != '\n') {
                        advance()
                    }
                }
                character == '/' && peek(1) == '*' -> {
                    advance()
                    advance()
                    while (index < source.length && !(peek() == '*' && peek(1) == '/')) {
                        advance()
                    }
                    if (index < source.length) {
                        advance()
                        advance()
                    }
                }
                character == '\'' -> {
                    advance()
                    while (index < source.length && peek() != '\'') {
                        if (advance() == '\\') {
                            advance()
                        }
                    }
                    if (index < source.length) {
                        advance()
                    }
                }
                character == '"' && peek(1) == '"' && peek(2) == '"' -> {
                    readString(raw = true)
                }
                character == '"' -> {
                    readString(raw = false)
                }
                character == '{' -> {
                    depth++
                    advance()
                }
                character == closing && depth == 0 -> {
                    advance()
                    return
                }
                character == '}' -> {
                    depth--
                    advance()
                }
                else -> {
                    advance()
                }
            }
        }
    }

    private fun readString(raw: Boolean) {
        val start = line
        val text = StringBuilder()
        repeat(if (raw) 3 else 1) {
            advance()
        }
        while (index < source.length) {
            val character = peek()
            if (raw && character == '"' && peek(1) == '"' && peek(2) == '"') {
                while (peek() == '"') {
                    advance()
                }
                break
            }
            if (!raw && character == '"') {
                advance()
                break
            }
            if (!raw && character == '\\') {
                advance()
                if (index < source.length) {
                    text.append(advance())
                }
                continue
            }
            if (character == '$' && peek(1) == '{') {
                advance()
                advance()
                readCode('}')
                text.append(' ')
                continue
            }
            text.append(advance())
        }
        texts.add(start to text.toString())
    }
}

class LauncherTermsTest {

    private val repository = Path.of(System.getProperty("semantifyr.repository")).toAbsolutePath().normalize()

    private val rules = run {
        val file = Json {
            ignoreUnknownKeys = true
        }.decodeFromString<TermsFile>(repository.resolve("subprojects/semantifyr-editor-common/src/terms/terms.json").readText())
        (file.terms.values + file.outcomes.values).flatMap { term ->
            term.rejected.map { word ->
                val words = word.split(Regex("\\s+")).joinToString("\\s+") {
                    Regex.escape(it)
                }
                RejectedWord(word, term.term, Regex("(?<![\\p{L}\\p{N}])$words(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE))
            }
        }
    }

    // a text a reader meets has a space or a capital; an identifier or a key has neither
    private fun isText(text: String): Boolean {
        return text.any {
            it.isWhitespace() || it.isUpperCase()
        }
    }

    private fun launcherTexts(): List<PrintedText> {
        val directories = System.getProperty("semantifyr.launcherSources").split(",").map {
            Path.of(it)
        }
        return directories.flatMap { directory ->
            Files.walk(directory).use { paths ->
                paths.filter {
                    it.isRegularFile() && it.extension in setOf("kt", "java")
                }.toList()
            }
        }.sorted().flatMap { file ->
            val name = repository.relativize(file.toAbsolutePath().normalize()).invariantSeparatorsPathString
            SourceTexts(file.readText()).read().filter { (_, text) ->
                isText(text)
            }.map { (line, text) ->
                PrintedText(name, line, text)
            }
        }
    }

    // sysml-2ls's diagnostic of a construct reaches the SysML v2 launcher's user as its row's detail, word for word
    private fun constructDetails(): List<PrintedText> {
        val table = Path.of("build/sysml-constructs/constructs.tsv")
        return table.readLines().withIndex().drop(1).filter { (_, row) ->
            row.isNotBlank()
        }.map { (index, row) ->
            PrintedText("subprojects/website/${table.invariantSeparatorsPathString}", index + 1, row.split('\t').last())
        }
    }

    private fun rejectedIn(texts: List<PrintedText>): List<String> {
        return texts.flatMap { printed ->
            rules.filter {
                it.pattern.containsMatchIn(printed.text)
            }.map {
                "${printed.file}:${printed.line}: \"${it.word}\", say \"${it.term}\""
            }
        }
    }

    @Test
    fun `the texts reach every module of the three launchers`() {
        val files = launcherTexts().map {
            it.file
        }.toSet()

        assertThat(files).anyMatch {
            it.startsWith("subprojects/oxsts.lang/src/main/java/")
        }
        assertThat(files).anyMatch {
            it.startsWith("subprojects/compiler/src/main/kotlin/")
        }
        assertThat(files).anyMatch {
            it.startsWith("subprojects/frontends/gamma/gamma-cli/src/main/kotlin/")
        }
        assertThat(files).anyMatch {
            it.startsWith("subprojects/frontends/sysml/sysml-cli/src/main/kotlin/")
        }
        assertThat(constructDetails()).isNotEmpty
    }

    @Test
    fun `no text a launcher prints holds a rejected word`() {
        assertThat(rejectedIn(launcherTexts() + constructDetails())).isEmpty()
    }

    @Test
    fun `a string's template and comments are no text of it`() {
        val source = """
            // an engine in a comment
            val message = "Theta ${'$'}{names.joinToString(", ")} answered"
            val character = '"'
            /* a block that says "engine" */
            val raw = ${"\"\"\""}Raw ${'$'}{value} text${"\"\"\""}
        """.trimIndent()

        val texts = SourceTexts(source).read()

        assertThat(texts).containsExactlyInAnyOrder(
            2 to "Theta   answered",
            2 to ", ",
            5 to "Raw   text",
        )
    }
}
