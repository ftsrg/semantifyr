/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.codelens;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.LspCliOptions;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands.*;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinersFinder;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.codelens.ICodeLensResolver;
import org.eclipse.xtext.ide.server.codelens.ICodeLensService;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

public class OxstsCodeLensProvider implements ICodeLensResolver, ICodeLensService {

    @Inject
    private InlineClassCommandHandler inlineClassCommandHandler;

    @Inject
    private CompileInlinedOxstsCommandHandler compileInlinedOxstsCommandHandler;

    @Inject
    private VerifyInlinedOxstsCommandHandler verifyInlinedOxstsCommandHandler;

    @Inject
    private NavigateToRedefinedCommandHandler navigateToRedefinedCommandHandler;

    @Inject
    private NavigateToRedefinersCommandHandler navigateToRedefinersCommandHandler;

    @Inject
    private BuiltinAnnotationHandler annotationHandler;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Inject
    private RedefinersFinder redefinersFinder;

    @Inject
    private LspCliOptions cliOptions;

    @Override
    public CodeLens resolveCodeLens(
            Document document, XtextResource resource, CodeLens codeLens, CancelIndicator indicator) {
        return codeLens;
    }

    @Override
    public List<? extends CodeLens> computeCodeLenses(
            Document document, XtextResource resource, CodeLensParams params, CancelIndicator indicator) {
        if (cliOptions.codeLensMode() == LspCliOptions.CodeLensMode.NONE) {
            return List.of();
        }

        if (resource.getContents().isEmpty()) {
            return List.of();
        }

        var rootElement = resource.getContents().getFirst();

        if (rootElement instanceof InlinedOxsts inlinedOxsts) {
            return List.of(createVerifyInlinedOxstsLens(inlinedOxsts), createCompileInlinedOxstsLens(inlinedOxsts));
        }

        var lenses = new ArrayList<CodeLens>();

        EcoreUtil2.getAllProperContents(resource, false).forEachRemaining(element -> {
            if (element instanceof ClassDeclaration classDeclaration) {
                if (annotationHandler.isVerificationCase(classDeclaration)) {
                    lenses.add(createCompileInlinedOxstsLens(classDeclaration, false));
                    lenses.add(createCompileInlinedOxstsLens(classDeclaration, true));
                }
            } else if (element instanceof RedefinableDeclaration redefinableDeclaration) {
                if (redefinitionHandler.getRedefinedDeclaration(redefinableDeclaration) != null) {
                    lenses.add(createNavigateToRedefinedLens(redefinableDeclaration));
                }
                if (!redefinersFinder
                        .getRedefinerDeclarations(redefinableDeclaration)
                        .isEmpty()) {
                    lenses.add(createNavigateToRedefinersLens(redefinableDeclaration));
                }
            }
        });

        return lenses;
    }

    private CodeLens createCompileInlinedOxstsLens(InlinedOxsts inlinedOxsts) {
        var codeLens = new CodeLens();
        var command = new Command(
                compileInlinedOxstsCommandHandler.getTitle(),
                compileInlinedOxstsCommandHandler.getId(),
                compileInlinedOxstsCommandHandler.serializeArguments(inlinedOxsts));
        codeLens.setCommand(command);
        var node = NodeModelUtils.getNode(inlinedOxsts);
        var start = new Position(node.getStartLine() - 1, 0);
        codeLens.setRange(new Range(start, start));
        return codeLens;
    }

    private CodeLens createVerifyInlinedOxstsLens(InlinedOxsts inlinedOxsts) {
        var codeLens = new CodeLens();
        var command = new Command(
                verifyInlinedOxstsCommandHandler.getTitle(),
                verifyInlinedOxstsCommandHandler.getId(),
                verifyInlinedOxstsCommandHandler.serializeArguments(
                        new VerifyInlinedOxstsCommandParams(inlinedOxsts, null)));
        codeLens.setCommand(command);
        var node = NodeModelUtils.getNode(inlinedOxsts);
        var start = new Position(node.getStartLine() - 1, 0);
        codeLens.setRange(new Range(start, start));
        return codeLens;
    }

    private CodeLens createCompileInlinedOxstsLens(ClassDeclaration classDeclaration, boolean serializeStep) {
        var codeLens = new CodeLens();
        var title = serializeStep
                ? inlineClassCommandHandler.getTitle() + " (with steps)"
                : inlineClassCommandHandler.getTitle();
        var command = new Command(
                title,
                inlineClassCommandHandler.getId(),
                inlineClassCommandHandler.serializeArguments(
                        new InlineClassCommandParams(classDeclaration, serializeStep)));
        codeLens.setCommand(command);
        var classNode = NodeModelUtils.getNode(classDeclaration);
        var start = new Position(classNode.getStartLine() - 1, 0);
        codeLens.setRange(new Range(start, start));
        return codeLens;
    }

    private CodeLens createNavigateToRedefinedLens(RedefinableDeclaration redefinableDeclaration) {
        var codeLens = new CodeLens();
        var command = new Command(
                navigateToRedefinedCommandHandler.getTitle(),
                navigateToRedefinedCommandHandler.getId(),
                navigateToRedefinedCommandHandler.serializeArguments(redefinableDeclaration));
        codeLens.setCommand(command);
        var classNode = NodeModelUtils.getNode(redefinableDeclaration);
        var start = new Position(classNode.getStartLine() - 1, 0);
        codeLens.setRange(new Range(start, start));
        return codeLens;
    }

    private CodeLens createNavigateToRedefinersLens(RedefinableDeclaration redefinableDeclaration) {
        var codeLens = new CodeLens();
        var command = new Command(
                navigateToRedefinersCommandHandler.getTitle(),
                navigateToRedefinersCommandHandler.getId(),
                navigateToRedefinersCommandHandler.serializeArguments(redefinableDeclaration));
        codeLens.setCommand(command);
        var classNode = NodeModelUtils.getNode(redefinableDeclaration);
        var start = new Position(classNode.getStartLine() - 1, 0);
        codeLens.setRange(new Range(start, start));
        return codeLens;
    }
}
