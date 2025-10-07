/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.codelens;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands.CompileOxstsCommandHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.lsp4j.*;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.codelens.ICodeLensResolver;
import org.eclipse.xtext.ide.server.codelens.ICodeLensService;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

import java.util.List;

@Singleton
public class OxstsCodeLensProvider implements ICodeLensResolver, ICodeLensService {

    @Inject
    private CompileOxstsCommandHandler compileOxstsCommandHandler;

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

        if (! (rootElement instanceof OxstsModelPackage oxstsPackage)) {
            return List.of();
        }

        return oxstsPackage.getDeclarations().stream()
                .filter(c -> c instanceof ClassDeclaration)
                .map(c -> (ClassDeclaration) c)
//                .filter(c -> !c.getAnnotation().getAnnotations().isEmpty())
                .map(c -> {
                    var codeLens = new CodeLens();
                    var command = new Command(compileOxstsCommandHandler.getTitle(), compileOxstsCommandHandler.getId(), compileOxstsCommandHandler.serializeArguments(c));
                    codeLens.setCommand(command);
                    var classNode = NodeModelUtils.getNode(c);
                    var start = new Position(classNode.getStartLine() - 1, 0);
                    codeLens.setRange(new Range(start, start));
                    return codeLens;
                })
                .toList();


    }

}
