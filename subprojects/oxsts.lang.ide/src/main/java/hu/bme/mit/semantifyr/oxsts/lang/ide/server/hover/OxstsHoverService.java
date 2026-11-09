/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.hover;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import java.util.ArrayList;
import java.util.HashSet;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.documentation.IEObjectDocumentationProvider;
import org.eclipse.xtext.ide.server.hover.HoverService;

@Singleton
public class OxstsHoverService extends HoverService {

    @Inject
    private OxstsDeclarationRenderer declarationRenderer;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Inject
    private IEObjectDocumentationProvider documentationProvider;

    @Inject
    private OxstsQualifiedNameProvider qualifiedNameProvider;

    @Override
    public String getContents(EObject element) {
        if (element == null) {
            return "";
        }
        var declaration = declarationRenderer.render(element);
        if (declaration == null) {
            return super.getContents(element);
        }

        var sections = new ArrayList<String>();
        var owner = getOwnerName(element);
        if (owner != null) {
            sections.add("`" + owner + "`");
        }
        sections.add("```oxsts\n" + declaration + "\n```");

        var redefined = getRedefined(element);
        if (redefined != null) {
            var redefinedOwner = NamingUtil.getName(redefined.eContainer());
            sections.add("Redefines `" + declarationRenderer.render(redefined) + "` in `" + redefinedOwner + "`.");
        }

        var documentation = getDocumentation(element);
        if (documentation != null && !documentation.isBlank()) {
            sections.add(documentation);
        }

        return String.join("\n\n", sections);
    }

    protected String getOwnerName(EObject element) {
        var owner = element.eContainer();
        while (owner != null) {
            if (qualifiedNameProvider.getFullyQualifiedName(owner) != null) {
                return qualifiedNameProvider.getFullyQualifiedNameString(owner);
            }
            owner = owner.eContainer();
        }
        return null;
    }

    protected RedefinableDeclaration getRedefined(EObject element) {
        if (element instanceof RedefinableDeclaration declaration) {
            return redefinitionHandler.getRedefinedDeclaration(declaration);
        }
        return null;
    }

    // An uncommented redefinition inherits the comment of what it redefines, as a Kotlin override does.
    protected String getDocumentation(EObject element) {
        var visited = new HashSet<EObject>();
        var current = element;
        while (current != null && visited.add(current)) {
            var documentation = documentationProvider.getDocumentation(current);
            if (documentation != null && !documentation.isBlank()) {
                return documentation;
            }
            current = getRedefined(current);
        }
        return null;
    }
}
