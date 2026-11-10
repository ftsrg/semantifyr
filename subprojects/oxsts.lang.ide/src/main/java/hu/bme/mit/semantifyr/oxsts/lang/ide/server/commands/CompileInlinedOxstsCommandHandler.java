/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import com.google.inject.Inject;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler;
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactKind;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.lang.ide.server.OpenDocumentTexts;
import hu.bme.mit.semantifyr.lang.ide.server.ServerSettings;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.verifier.CaseDirectoryNameKt;
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer;
import java.util.List;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.lsp4j.Location;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
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
    protected VerificationCaseDiscoverer verificationCaseDiscoverer;

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

        var context = semantifyrLoader.fromResourceSet(
                arguments.eResource().getResourceSet(),
                openDocumentTexts.of(arguments.eResource().getResourceSet()));
        var inlinedOxsts = (InlinedOxsts) context.getResourceSet().getEObject(EcoreUtil.getURI(arguments), true);
        var compiler = new SemantifyrCompiler(
                injector,
                serverSettings.resolveArtifactConfig().withEnabled(ArtifactKind.FlattenedModel),
                serverSettings.resolveOptimizationConfig());
        var outputDirectory = serverSettings.resolveArtifactOutputDirectory(access);

        return performBackgroundWork(() -> {
            progressContext.checkIsCancelled();
            var verificationCase = verificationCaseDiscoverer.caseOf(inlinedOxsts.getClassDeclaration());
            // a directory of its own, since the model compiled may be the one an inline left in the case directory
            var compilationDirectory = CaseDirectoryNameKt.caseDirectory(outputDirectory, verificationCase)
                    .resolve("compiled");
            var compilation = compiler.compile(inlinedOxsts, compilationDirectory);
            var compiledModel = compilation.getFlattenedModelPath();
            LOGGER.info("LSP compile-inlined -> {}", compiledModel);
            return compiledModel.toUri().toString();
        });
    }
}
