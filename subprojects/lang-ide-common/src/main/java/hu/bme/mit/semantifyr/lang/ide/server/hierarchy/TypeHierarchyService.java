/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.hierarchy;

import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

public interface TypeHierarchyService {

    List<TypeHierarchyItem> prepareTypeHierarchy(
            Document document,
            XtextResource resource,
            TypeHierarchyPrepareParams params,
            CancelIndicator cancelIndicator);

    List<TypeHierarchyItem> supertypes(Resource documentResource, TypeHierarchyItem item);

    List<TypeHierarchyItem> subtypes(Resource documentResource, TypeHierarchyItem item);
}
