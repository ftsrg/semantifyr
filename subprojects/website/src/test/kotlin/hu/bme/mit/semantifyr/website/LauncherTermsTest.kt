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

data class PrintedText(
    val place: String,
    val text: String,
)

enum class SourceLanguage {
    Kotlin,
    Java,
}

// the strings of a Kotlin or Java source as the language reads them, its comments and character literals left out, a template's expression and variable standing as a space
class SourceTexts(
    private val file: String,
    private val source: String,
    private val language: SourceLanguage,
) {
    private var index = 0
    private var line = 1
    private val texts = mutableListOf<PrintedText>()

    fun read(): List<PrintedText> {
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
                    readString(triple = true)
                }
                character == '"' -> {
                    readString(triple = false)
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

    private fun readString(triple: Boolean) {
        val start = line
        val escapes = !(triple && language == SourceLanguage.Kotlin)
        val templates = language == SourceLanguage.Kotlin
        val text = StringBuilder()
        repeat(if (triple) 3 else 1) {
            advance()
        }
        // a text block's content starts on the line after its opening quotes
        if (triple && language == SourceLanguage.Java) {
            while (index < source.length && peek() != '\n') {
                advance()
            }
            if (index < source.length) {
                advance()
            }
        }
        while (index < source.length) {
            val character = peek()
            if (triple && character == '"' && peek(1) == '"' && peek(2) == '"') {
                // Kotlin's raw string ends at the last quote of a run, Java's text block at the third
                if (language == SourceLanguage.Kotlin) {
                    while (peek(3) == '"') {
                        text.append(advance())
                    }
                }
                repeat(3) {
                    advance()
                }
                break
            }
            if (!triple && character == '"') {
                advance()
                break
            }
            if (escapes && character == '\\') {
                advance()
                readEscape(text)
                continue
            }
            if (templates && character == '$' && peek(1) == '{') {
                advance()
                advance()
                readCode('}')
                text.append(' ')
                continue
            }
            if (templates && character == '$' && isIdentifierStart(peek(1))) {
                advance()
                while (isIdentifierStart(peek()) || peek()?.isDigit() == true) {
                    advance()
                }
                text.append(' ')
                continue
            }
            text.append(advance())
        }
        texts.add(PrintedText("$file:$start", text.toString()))
    }

    private fun isIdentifierStart(character: Char?): Boolean {
        return character != null && (character.isLetter() || character == '_')
    }

    private fun readEscape(text: StringBuilder) {
        if (index >= source.length) {
            return
        }
        when (val character = advance()) {
            'n' -> text.append('\n')
            't' -> text.append('\t')
            'r' -> text.append('\r')
            'b' -> text.append('\b')
            'f' -> text.append('\u000C')
            's' -> text.append(' ')
            // a text block's line ends in a backslash to go on on the next line
            '\n' -> {
            }
            'u' -> {
                while (peek() == 'u') {
                    advance()
                }
                val digits = source.substring(index, minOf(index + 4, source.length))
                repeat(digits.length) {
                    advance()
                }
                text.append(digits.toInt(16).toChar())
            }
            in '0'..'7' -> {
                var digits = character.toString()
                while (digits.length < 3 && peek() in '0'..'7' && (digits + peek()).toInt(8) <= 255) {
                    digits += advance()
                }
                text.append(digits.toInt(8).toChar())
            }
            else -> text.append(character)
        }
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
            val language = if (file.extension == "kt") {
                SourceLanguage.Kotlin
            } else {
                SourceLanguage.Java
            }
            SourceTexts(name, file.readText(), language).read().filter {
                isText(it.text)
            }
        }
    }

    // sysml-2ls's diagnostic of a construct reaches the SysML v2 launcher's user as its row's detail, word for word
    private fun constructDetails(): List<PrintedText> {
        return Path.of("build/sysml-constructs/constructs.tsv").readLines().drop(1).filter {
            it.isNotBlank()
        }.map {
            val columns = it.split('\t')
            PrintedText("the row \"${columns.first()}\" of sysml-2ls's construct table, a text of sysml-2ls", columns.last())
        }
    }

    private fun rejectedIn(texts: List<PrintedText>): List<String> {
        return texts.flatMap { printed ->
            rules.filter {
                it.pattern.containsMatchIn(printed.text)
            }.map {
                "${printed.place}: \"${it.word}\", say \"${it.term}\""
            }
        }
    }

    @Test
    fun `the texts reach every module of the three launchers`() {
        val files = launcherTexts().map {
            it.place
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
            val variable = "The ${'$'}engineName backend"
        """.trimIndent()

        val texts = SourceTexts("Source.kt", source, SourceLanguage.Kotlin).read()

        assertThat(texts).containsExactlyInAnyOrder(
            PrintedText("Source.kt:2", "Theta   answered"),
            PrintedText("Source.kt:2", ", "),
            PrintedText("Source.kt:5", "Raw   text"),
            PrintedText("Source.kt:6", "The   backend"),
        )
    }

    @Test
    fun `a string's escapes are read as the language reads them`() {
        val kotlin = """
            val message = "Ends a line\nengine, a tab\tand \"quoted\" \u0041 \${'$'}name"
            val raw = ${"\"\"\""}Raw\n${"\"\"\""}
        """.trimIndent()
        val java = """
            String message = "Java's ${'$'}name\sand\101";
            String block = ${"\"\"\""}
                Joined \
                line${"\"\"\""};
        """.trimIndent()

        assertThat(SourceTexts("Source.kt", kotlin, SourceLanguage.Kotlin).read()).containsExactly(
            PrintedText("Source.kt:1", "Ends a line\nengine, a tab\tand \"quoted\" A ${'$'}name"),
            PrintedText("Source.kt:2", "Raw\\n"),
        )
        assertThat(SourceTexts("Source.java", java, SourceLanguage.Java).read()).containsExactly(
            PrintedText("Source.java:1", "Java's ${'$'}name andA"),
            PrintedText("Source.java:2", "    Joined     line"),
        )
    }
}
