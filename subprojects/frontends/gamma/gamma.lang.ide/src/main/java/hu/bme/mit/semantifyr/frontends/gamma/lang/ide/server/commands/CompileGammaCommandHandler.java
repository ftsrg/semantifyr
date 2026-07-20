/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.ide.server.commands;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage;
import hu.bme.mit.semantifyr.lang.ide.server.commands.AbstractCommandHandler;
import hu.bme.mit.semantifyr.lang.ide.server.commands.CommandProgressContext;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.util.CancelIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompileGammaCommandHandler extends AbstractCommandHandler<String, Resource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompileGammaCommandHandler.class);

    @Inject
    private GammaCompiler gammaCompiler;

    @Override
    public String getId() {
        return "gamma.compile";
    }

    @Override
    public String getTitle() {
        return "Compile Gamma to OXSTS";
    }

    @Override
    public List<Object> serializeArguments(Resource arguments) {
        return List.of(arguments.getURI().toString());
    }

    @Override
    protected Class<String> getRequestType() {
        return String.class;
    }

    @Override
    protected Resource resolveArgument(String request, ILanguageServerAccess access, CancelIndicator cancelIndicator) {
        return access.doSyncRead(request, ILanguageServerAccess.Context::getResource);
    }

    @Override
    protected Object execute(Resource arguments, ILanguageServerAccess access, CommandProgressContext progressContext) {
        if (arguments.getContents().isEmpty()) {
            LOGGER.warn("LSP gamma.compile: empty resource at {}", arguments.getURI());
            return null;
        }
        if (!(arguments.getContents().getFirst() instanceof GammaModelPackage gammaModel)) {
            LOGGER.warn("LSP gamma.compile: resource is not a Gamma model: {}", arguments.getURI());
            return null;
        }

        var sourceUri = arguments.getURI();
        var targetPath = oxstsTargetPath(sourceUri);
        progressContext.begin("Compiling " + sourceUri.lastSegment(), "Generating " + targetPath.getFileName());

        LOGGER.info("LSP gamma.compile: {} -> {}", sourceUri, targetPath);
        gammaCompiler.compile(gammaModel, targetPath);
        return targetPath.toString();
    }

    private static Path oxstsTargetPath(URI sourceUri) {
        if (!sourceUri.isFile()) {
            throw new IllegalArgumentException("gamma.compile only supports file:// URIs (got: " + sourceUri + ")");
        }
        var sourceFile = new File(sourceUri.toFileString());
        var targetName = stripExtension(sourceFile.getName()) + ".oxsts";
        return sourceFile.toPath().resolveSibling(targetName);
    }

    private static String stripExtension(String fileName) {
        var dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
