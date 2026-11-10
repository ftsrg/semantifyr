/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.syntaxcoloring;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.ide.InlineModels;
import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.xtext.ide.server.semantictokens.SemanticTokensService;
import org.junit.jupiter.api.Test;

class OxstsSemanticTokensTest extends OxstsLanguageServerTest {

    private static final String GARAGE_WITH_QUALIFIED_REFERENCES = """
            package example::garage

            import example::library

            class Garage {
                contains lamp: example::library::Lamp
                var color: LightColor := LightColor::Red
            }
            """;

    private static final String VEHICLES_WITH_MODIFIERS = """
            package example::vehicles

            abstract class Vehicle {
                container garage: Garage
                abstract tran drive()
            }

            class Garage {
                refers vehicle: Vehicle
                var open: bool := false
            }
            """;

    record Token(int length, String type, Set<String> modifiers) {}

    record HighlightedModel(String text, Map<Position, Token> tokens) {

        Token tokenAt(String markedSnippet) {
            var snippet = MarkedText.of(markedSnippet);
            var index = text.indexOf(snippet.text());
            assertThat(index).as("snippet occurs in the model").isNotNegative();
            assertThat(text.lastIndexOf(snippet.text()))
                    .as("snippet occurs once")
                    .isEqualTo(index);
            var offset = index + snippet.position().getCharacter();
            var line = (int) text.substring(0, offset)
                    .chars()
                    .filter(character -> {
                        return character == '\n';
                    })
                    .count();
            var character = offset - text.lastIndexOf('\n', offset - 1) - 1;
            return tokens.get(new Position(line, character));
        }
    }

    @Inject
    private SemanticTokensService semanticTokensService;

    @Test
    void eachSegmentOfAPackageNameIsANamespaceDeclaration() throws Exception {
        var model = highlight(GARAGE_WITH_QUALIFIED_REFERENCES);

        assertThat(model.tokenAt("package <|>example::garage"))
                .isEqualTo(new Token(7, SemanticTokenTypes.Namespace, Set.of(SemanticTokenModifiers.Declaration)));
        assertThat(model.tokenAt("package example<|>::garage")).isNull();
        assertThat(model.tokenAt("package example::<|>garage"))
                .isEqualTo(new Token(6, SemanticTokenTypes.Namespace, Set.of(SemanticTokenModifiers.Declaration)));
    }

    @Test
    void eachSegmentOfAQualifiedTypeReferenceHasTheKindOfWhatItResolvesTo() throws Exception {
        var model = highlightWithLibrary(GARAGE_WITH_QUALIFIED_REFERENCES);

        assertThat(model.tokenAt("lamp: <|>example::library::Lamp"))
                .isEqualTo(new Token(7, SemanticTokenTypes.Namespace, Set.of()));
        assertThat(model.tokenAt("lamp: example::<|>library::Lamp"))
                .isEqualTo(new Token(7, SemanticTokenTypes.Namespace, Set.of()));
        assertThat(model.tokenAt("lamp: example::library<|>::Lamp")).isNull();
        assertThat(model.tokenAt("lamp: example::library::<|>Lamp"))
                .isEqualTo(new Token(4, SemanticTokenTypes.Class, Set.of()));
    }

    @Test
    void anEnumLiteralReferenceHasAnEnumAndAnEnumMemberSegment() throws Exception {
        var model = highlightWithLibrary(GARAGE_WITH_QUALIFIED_REFERENCES);

        assertThat(model.tokenAt(":= <|>LightColor::Red")).isEqualTo(new Token(10, SemanticTokenTypes.Enum, Set.of()));
        assertThat(model.tokenAt(":= LightColor::<|>Red"))
                .isEqualTo(new Token(3, SemanticTokenTypes.EnumMember, Set.of()));
    }

    @Test
    void abstractDeclarationsAndTheirReferencesAreAbstract() throws Exception {
        var model = highlight(VEHICLES_WITH_MODIFIERS);

        assertThat(model.tokenAt("abstract class <|>Vehicle"))
                .isEqualTo(new Token(
                        7,
                        SemanticTokenTypes.Class,
                        Set.of(SemanticTokenModifiers.Declaration, SemanticTokenModifiers.Abstract)));
        assertThat(model.tokenAt("abstract tran <|>drive"))
                .isEqualTo(new Token(
                        5,
                        SemanticTokenTypes.Method,
                        Set.of(SemanticTokenModifiers.Declaration, SemanticTokenModifiers.Abstract)));
        assertThat(model.tokenAt("vehicle: <|>Vehicle"))
                .isEqualTo(new Token(7, SemanticTokenTypes.Class, Set.of(SemanticTokenModifiers.Abstract)));
    }

    @Test
    void referenceAndContainerFeaturesAreReadonlyAndAVariableIsNot() throws Exception {
        var model = highlight(VEHICLES_WITH_MODIFIERS);

        assertThat(model.tokenAt("refers <|>vehicle"))
                .isEqualTo(new Token(
                        7,
                        SemanticTokenTypes.Property,
                        Set.of(SemanticTokenModifiers.Declaration, SemanticTokenModifiers.Readonly)));
        assertThat(model.tokenAt("container <|>garage"))
                .isEqualTo(new Token(
                        6,
                        SemanticTokenTypes.Property,
                        Set.of(SemanticTokenModifiers.Declaration, SemanticTokenModifiers.Readonly)));
        assertThat(model.tokenAt("var <|>open"))
                .isEqualTo(new Token(4, SemanticTokenTypes.Variable, Set.of(SemanticTokenModifiers.Declaration)));
    }

    @Test
    void aBuiltinTypeIsFromTheDefaultLibrary() throws Exception {
        var model = highlight(VEHICLES_WITH_MODIFIERS);

        assertThat(model.tokenAt("open: <|>bool"))
                .isEqualTo(new Token(4, SemanticTokenTypes.Type, Set.of(SemanticTokenModifiers.DefaultLibrary)));
    }

    private HighlightedModel highlightWithLibrary(String text) throws Exception {
        addLibraryFile(InlineModels.LAMP_LIBRARY_FILE_NAME, InlineModels.LAMP_LIBRARY);
        return highlight(text);
    }

    private HighlightedModel highlight(String text) throws Exception {
        var document = openModel(text);
        var data = languageServer
                .semanticTokensFull(new SemanticTokensParams(document))
                .get()
                .getData();
        var tokenTypes = semanticTokensService.getTokenTypes();
        var tokenModifiers = semanticTokensService.getTokenModifiers();
        var tokens = new HashMap<Position, Token>();
        var line = 0;
        var character = 0;
        for (var index = 0; index < data.size(); index += 5) {
            var deltaLine = data.get(index);
            line += deltaLine;
            if (deltaLine == 0) {
                character += data.get(index + 1);
            } else {
                character = data.get(index + 1);
            }
            var modifiers = new HashSet<String>();
            for (var bit = 0; bit < tokenModifiers.size(); bit++) {
                if ((data.get(index + 4) & (1 << bit)) != 0) {
                    modifiers.add(tokenModifiers.get(bit));
                }
            }
            tokens.put(
                    new Position(line, character),
                    new Token(data.get(index + 2), tokenTypes.get(data.get(index + 3)), modifiers));
        }
        return new HighlightedModel(text, tokens);
    }
}
