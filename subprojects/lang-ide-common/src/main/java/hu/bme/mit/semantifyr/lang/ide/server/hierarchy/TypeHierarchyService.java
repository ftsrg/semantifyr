/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.hierarchy;

import java.util.List;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.resource.XtextResource;

public interface TypeHierarchyService {

    List<TypeHierarchyItem> prepare(XtextResource resource, Document document, TypeHierarchyPrepareParams params);

    List<TypeHierarchyItem> supertypes(ResourceSet resourceSet, TypeHierarchyItem item);

    List<TypeHierarchyItem> subtypes(ResourceSet resourceSet, TypeHierarchyItem item);
}
