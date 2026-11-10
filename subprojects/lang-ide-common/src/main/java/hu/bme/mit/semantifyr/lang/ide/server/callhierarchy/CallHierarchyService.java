/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.callhierarchy;

import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.xtext.findReferences.IReferenceFinder;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

public interface CallHierarchyService {

    List<CallHierarchyItem> prepareCallHierarchy(
            Document document,
            XtextResource resource,
            CallHierarchyPrepareParams params,
            CancelIndicator cancelIndicator);

    List<CallHierarchyIncomingCall> incomingCalls(
            Resource documentResource,
            CallHierarchyItem item,
            IReferenceFinder.IResourceAccess resourceAccess,
            IResourceDescriptions index,
            CancelIndicator cancelIndicator);

    List<CallHierarchyOutgoingCall> outgoingCalls(Resource documentResource, CallHierarchyItem item);
}
