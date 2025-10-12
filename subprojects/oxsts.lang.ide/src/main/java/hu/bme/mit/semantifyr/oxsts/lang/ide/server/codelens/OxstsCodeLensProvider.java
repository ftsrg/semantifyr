/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.codelens;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands.*;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.lsp4j.*;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.codelens.ICodeLensResolver;
import org.eclipse.xtext.ide.server.codelens.ICodeLensService;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class OxstsCodeLensProvider implements ICodeLensResolver, ICodeLensService {

    @Inject
    private InlineOxstsCommandHandler inlineOxstsCommandHandler;

    @Inject
    private CompileInlinedOxstsCommandHandler compileInlinedOxstsCommandHandler;

    @Inject
    private NavigateToRedefinedCommandHandler navigateToRedefinedCommandHandler;

    @Inject
    private BuiltinAnnotationHandler annotationHandler;

    @Override
    public CodeLens resolveCodeLens(Document document, XtextResource resource, CodeLens codeLens, CancelIndicator indicator) {
        return codeLens;
    }

    @Override
    public List<? extends CodeLens> computeCodeLenses(Document document, XtextResource resource, CodeLensParams params, CancelIndicator indicator) {
        if (resource.getContents().isEmpty()) {
            return List.of();
        }

        var rootElement = resource.getContents().getFirst();

        if (rootElement instanceof InlinedOxsts inlinedOxsts) {
            var codeLens = new CodeLens();
            var command = new Command(compileInlinedOxstsCommandHandler.getTitle(), compileInlinedOxstsCommandHandler.getId(), compileInlinedOxstsCommandHandler.serializeArguments(inlinedOxsts));
            codeLens.setCommand(command);
            var node = NodeModelUtils.getNode(inlinedOxsts);
            var start = new Position(node.getStartLine() - 1, 0);
            codeLens.setRange(new Range(start, start));
            return List.of(codeLens);
        }

        var lenses = new ArrayList<CodeLens>();

        EcoreUtil2.getAllProperContents(resource, false).forEachRemaining(element -> {
            if (element instanceof ClassDeclaration classDeclaration) {
                if (annotationHandler.isVerificationCase(classDeclaration)) {
                    lenses.add(createCompileLense(classDeclaration));
                }
            } else if (element instanceof RedefinableDeclaration redefinableDeclaration) {
                if (redefinableDeclaration.isRedefine() || redefinableDeclaration.getRedefined() != null) {
                    lenses.add(createNavigateToRedefinedLense(redefinableDeclaration));
                }
            }
        });

        return lenses;
    }

    private CodeLens createCompileLense(ClassDeclaration classDeclaration) {
        var codeLens = new CodeLens();
        var command = new Command(inlineOxstsCommandHandler.getTitle(), inlineOxstsCommandHandler.getId(), inlineOxstsCommandHandler.serializeArguments(classDeclaration));
        codeLens.setCommand(command);
        var classNode = NodeModelUtils.getNode(classDeclaration);
        var start = new Position(classNode.getStartLine() - 1, 0);
        codeLens.setRange(new Range(start, start));
        return codeLens;
    }

    private CodeLens createNavigateToRedefinedLense(RedefinableDeclaration redefinableDeclaration) {
        var codeLens = new CodeLens();
        var command = new Command(navigateToRedefinedCommandHandler.getTitle(), navigateToRedefinedCommandHandler.getId(), navigateToRedefinedCommandHandler.serializeArguments(redefinableDeclaration));
        codeLens.setCommand(command);
        var classNode = NodeModelUtils.getNode(redefinableDeclaration);
        var start = new Position(classNode.getStartLine() - 1, 0);
        codeLens.setRange(new Range(start, start));
        return codeLens;
    }

}
