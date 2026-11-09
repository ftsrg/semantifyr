/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.formatting;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.formatting.FormattingService;
import org.eclipse.xtext.resource.SaveOptions;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.serializer.ISerializer;
import org.eclipse.xtext.util.CancelIndicator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ModelFileFormattingTest {

    private static final List<Path> MODEL_DIRECTORIES = List.of(
            Path.of("../../oxsts-test-models/tutorial"),
            Path.of("../oxsts.lang/src/main/resources/hu/bme/mit/semantifyr/oxsts/lang/library"),
            Path.of("../frontends/gamma/models/libraries/default"),
            Path.of("../frontends/sysml/models/libraries/default"),
            Path.of("../frontends/sysml/models/libraries/topdown"));

    private static Injector injector;

    @BeforeAll
    static void createInjector() {
        injector = new OxstsIdeSetup().createInjectorAndDoEMFRegistration();
    }

    static Stream<Path> modelFiles() throws IOException {
        var files = Stream.<Path>builder();
        for (var directory : MODEL_DIRECTORIES) {
            try (var paths = Files.list(directory)) {
                paths.filter(path -> path.toString().endsWith(".oxsts")).sorted().forEach(files::add);
            }
        }
        return files.build();
    }

    @ParameterizedTest
    @MethodSource("modelFiles")
    void formattingAModelFileChangesNothing(Path modelFile) throws IOException {
        var text = Files.readString(modelFile).replace("\r\n", "\n");

        assertThat(format(text)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("modelFiles")
    void formattingWhatTheSerializerWritesChangesNothing(Path modelFile) throws IOException {
        var resourceSet = injector.getInstance(XtextResourceSet.class);
        try (var siblings = Files.list(modelFile.getParent())) {
            for (var sibling : siblings.filter(path -> path.toString().endsWith(".oxsts")).toList()) {
                resourceSet.getResource(URI.createFileURI(sibling.toAbsolutePath().toString()), true);
            }
        }
        var parsed = resourceSet.getResource(URI.createFileURI(modelFile.toAbsolutePath().toString()), true);
        EcoreUtil.resolveAll(resourceSet);
        var copy = EcoreUtil.copy(parsed.getContents().getFirst());
        resourceSet.createResource(URI.createURI("copy.oxsts")).getContents().add(copy);
        var serialized = injector.getInstance(ISerializer.class)
                .serialize(copy, SaveOptions.newBuilder().format().getOptions())
                .replace("\t", "    ");

        assertThat(format(serialized)).isEmpty();
    }

    private static List<? extends TextEdit> format(String text) throws IOException {
        var resource = load(text);
        assertThat(resource.getParseResult().getSyntaxErrors()).isEmpty();
        var params = new DocumentFormattingParams(new TextDocumentIdentifier(resource.getURI().toString()), new FormattingOptions(4, true));
        return injector.getInstance(FormattingService.class).format(new Document(1, text), resource, params, CancelIndicator.NullImpl);
    }

    private static XtextResource load(String text) throws IOException {
        var resource = (XtextResource) injector.getInstance(XtextResourceSet.class).createResource(URI.createURI("model.oxsts"));
        resource.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), Map.of());
        return resource;
    }
}
