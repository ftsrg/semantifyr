/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.lang.ide.server.OpenDocumentTexts;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.verifier.CaseDirectoryNameKt;
import hu.bme.mit.semantifyr.verifier.VerificationCase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.SaveOptions;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.serializer.ISerializer;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompileInlinedOxstsCommandHandler extends AbstractCommandHandler<Location, InlinedOxsts> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompileInlinedOxstsCommandHandler.class);

    @Inject
    protected SemantifyrLoader semantifyrLoader;

    @Inject
    protected OpenDocumentTexts openDocumentTexts;

    @Inject
    protected OxstsQualifiedNameProvider oxstsQualifiedNameProvider;

    @Inject
    protected ISerializer serializer;

    @Inject
    protected ServerSettings serverSettings;

    @Inject
    protected Injector injector;

    @Override
    public String getId() {
        return "oxsts.inlined.compile";
    }

    @Override
    public String getTitle() {
        return "Compile";
    }

    @Override
    public List<Object> serializeArguments(InlinedOxsts arguments) {
        return List.of(getLocation(arguments));
    }

    @Override
    protected Class<Location> getRequestType() {
        return Location.class;
    }

    @Override
    protected InlinedOxsts resolveArgument(
            Location request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        return (InlinedOxsts) getElement(access, request);
    }

    @Override
    protected Object execute(
            InlinedOxsts arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        LOGGER.info("LSP compile-inlined request");

        var sourceUri = arguments.eResource().getURI();
        var sourceText = NodeModelUtils.getNode(arguments).getRootNode().getText();
        var context = semantifyrLoader.fromResourceSet(
                arguments.eResource().getResourceSet(),
                openDocumentTexts.of(arguments.eResource().getResourceSet()));
        var compiler = new SemantifyrCompiler(
                injector, serverSettings.resolveArtifactConfig(), serverSettings.resolveOptimizationConfig());
        var outputDirectory = serverSettings.resolveArtifactOutputDirectory(access);

        return performBackgroundWork(() -> {
            progressContext.checkIsCancelled();
            try {
                var resource = context.getResourceSet().createResource(sourceUri);
                resource.load(
                        new ByteArrayInputStream(sourceText.getBytes(StandardCharsets.UTF_8)),
                        Map.of(XtextResource.OPTION_ENCODING, StandardCharsets.UTF_8.name()));
                var inlinedOxsts = (InlinedOxsts) resource.getContents().getFirst();
                var classDeclaration = inlinedOxsts.getClassDeclaration();
                var verificationCase = new VerificationCase(
                        oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration),
                        classDeclaration,
                        Set.of());
                var caseDirectory = outputDirectory.resolve(CaseDirectoryNameKt.caseDirectoryName(verificationCase));
                var compilation = compiler.compile(inlinedOxsts, caseDirectory);
                // not inlined.oxsts, which may be the very model being compiled
                var compiledModel = caseDirectory.resolve("compiled.oxsts");
                Files.createDirectories(caseDirectory);
                try (var writer = Files.newBufferedWriter(compiledModel)) {
                    serializer.serialize(compilation.getInlinedOxsts(), writer, SaveOptions.defaultOptions());
                }
                LOGGER.info("LSP compile-inlined -> {}", compiledModel);
                return compiledModel.toUri().toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
