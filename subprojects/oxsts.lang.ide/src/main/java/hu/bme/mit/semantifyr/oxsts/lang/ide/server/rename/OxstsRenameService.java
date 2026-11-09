/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.rename;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.references.RedefinitionChainCollector;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.xtext.findReferences.IReferenceFinder;
import org.eclipse.xtext.findReferences.ReferenceAcceptor;
import org.eclipse.xtext.findReferences.TargetURICollector;
import org.eclipse.xtext.findReferences.TargetURIs;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.ILanguageServerAccess;
import org.eclipse.xtext.ide.server.UriExtensions;
import org.eclipse.xtext.ide.server.rename.RenameService2;
import org.eclipse.xtext.ide.util.CancelIndicatorProgressMonitor;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.IReferenceDescription;
import org.eclipse.xtext.resource.IResourceDescriptionsProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.util.concurrent.IUnitOfWork;
import org.eclipse.xtext.xbase.lib.Exceptions;

public class OxstsRenameService extends RenameService2 {

    private static final String IDENTIFIER_RULE = "Identifier";

    @Inject
    private RedefinitionChainCollector redefinitionChainCollector;

    @Inject
    private IResourceDescriptionsProvider resourceDescriptionsProvider;

    @Inject
    private IReferenceFinder referenceFinder;

    @Inject
    private TargetURICollector targetURICollector;

    @Inject
    private Provider<TargetURIs> targetURIsProvider;

    @Inject
    private IScopeProvider scopeProvider;

    @Inject
    private IQualifiedNameConverter qualifiedNameConverter;

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private UriExtensions uriExtensions;

    @Override
    public WorkspaceEdit rename(Options options) {
        var params = options.getRenameParams();
        var access = options.getLanguageServerAccess();
        var workspaceRoots = getWorkspaceRoots(access.getInitializeParams());
        return access.doSyncRead(
                params.getTextDocument().getUri(),
                context -> rename(context, params, workspaceRoots, options.getCancelIndicator()));
    }

    protected WorkspaceEdit rename(
            ILanguageServerAccess.Context context,
            RenameParams params,
            List<String> workspaceRoots,
            CancelIndicator cancelIndicator) {
        var resource = (XtextResource) context.getResource();
        var element = getElementAtOffset(resource, context.getDocument(), params.getPosition());
        if (element == null || element.eIsProxy()) {
            throw refusal("No declaration to rename at the cursor");
        }
        if (element instanceof OxstsModelPackage) {
            throw refusal("Packages cannot be renamed");
        }

        var oldName = NamingUtil.getName(element);
        var newName = (String) getValueConverterService().toValue(params.getNewName(), IDENTIFIER_RULE, null);
        if (Strings.isEmpty(newName)) {
            throw refusal("The new name is empty");
        }
        if (newName.equals(oldName)) {
            return new WorkspaceEdit();
        }

        var renamed = redefinitionChainCollector.collect(element).stream()
                .filter(member -> {
                    return Objects.equals(NamingUtil.getName(member), oldName);
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));

        var roots = workspaceRoots.isEmpty() ? List.of(getParentUri(resource.getURI())) : workspaceRoots;
        var edits = new LinkedHashMap<Resource, TreeMap<Integer, ILeafNode>>();
        for (var member : renamed) {
            var nameLeaf = getNameLeaf(member);
            if (nameLeaf == null) {
                throw refusal(oldName + " has no name of its own to rename");
            }
            checkInWorkspace(member.eResource(), roots, oldName);
            addEdit(edits, member.eResource(), nameLeaf);
        }

        checkDeclarationClashes(renamed, newName);

        var resourceSet = resource.getResourceSet();
        for (var reference : findReferences(renamed, resourceSet, cancelIndicator)) {
            var source = resourceSet.getEObject(reference.getSourceEObjectUri(), true);
            var nodes = NodeModelUtils.findNodesForFeature(source, reference.getEReference());
            var node = nodes.get(Math.max(reference.getIndexInList(), 0));
            checkInWorkspace(source.eResource(), roots, oldName);
            checkReferenceClash(source, reference, node, renamed, newName);
            addEdit(edits, source.eResource(), getLastLeaf(node));
        }

        var newText = getValueConverterService().toString(newName, IDENTIFIER_RULE);
        var changes = new LinkedHashMap<String, List<TextEdit>>();
        edits.forEach((editedResource, leaves) -> {
            var text = ((XtextResource) editedResource)
                    .getParseResult()
                    .getRootNode()
                    .getText();
            var document = new Document(-1, text);
            var textEdits = leaves.values().stream()
                    .map(leaf -> {
                        var range = new Range(
                                document.getPosition(leaf.getOffset()), document.getPosition(leaf.getEndOffset()));
                        return new TextEdit(range, newText);
                    })
                    .toList();
            changes.put(uriExtensions.toUriString(editedResource.getURI()), textEdits);
        });
        return new WorkspaceEdit(changes);
    }

    private static void addEdit(Map<Resource, TreeMap<Integer, ILeafNode>> edits, Resource resource, ILeafNode leaf) {
        edits.computeIfAbsent(resource, key -> new TreeMap<>()).put(leaf.getOffset(), leaf);
    }

    private List<IReferenceDescription> findReferences(
            Set<EObject> targets, ResourceSet resourceSet, CancelIndicator cancelIndicator) {
        var targetURIs = targetURIsProvider.get();
        for (var target : targets) {
            targetURICollector.add(target, targetURIs);
        }
        var resourceAccess = new IReferenceFinder.IResourceAccess() {
            @Override
            public <R> R readOnly(URI targetURI, IUnitOfWork<R, ResourceSet> work) {
                try {
                    return work.exec(resourceSet);
                } catch (Exception e) {
                    throw Exceptions.sneakyThrow(e);
                }
            }
        };
        var references = new ArrayList<IReferenceDescription>();
        referenceFinder.findAllReferences(
                targetURIs,
                resourceAccess,
                resourceDescriptionsProvider.getResourceDescriptions(resourceSet),
                new ReferenceAcceptor(getServiceProviderRegistry(), references::add),
                new CancelIndicatorProgressMonitor(cancelIndicator));
        return references;
    }

    private void checkDeclarationClashes(Set<EObject> renamed, String newName) {
        for (var member : renamed) {
            var container = member.eContainer();
            for (var sibling : container.eContents()) {
                if (!renamed.contains(sibling) && newName.equals(NamingUtil.getName(sibling))) {
                    throw refusal(describe(container) + " already declares " + newName);
                }
            }
        }

        var resourceSet = renamed.iterator().next().eResource().getResourceSet();
        var contents = resourceSet.getAllContents();
        while (contents.hasNext()) {
            if (contents.next() instanceof ClassDeclaration classDeclaration) {
                var seesRenamed = false;
                var clashes = false;
                for (var member : domainMemberCollectionProvider
                        .getMembers(classDeclaration)
                        .getExportedObjects()) {
                    if (renamed.contains(member.getEObjectOrProxy())) {
                        seesRenamed = true;
                    } else if (newName.equals(member.getName().toString())) {
                        clashes = true;
                    }
                }
                if (seesRenamed && clashes) {
                    throw refusal(describe(classDeclaration) + " already has a member named " + newName);
                }
            }
        }
    }

    private void checkReferenceClash(
            EObject source, IReferenceDescription reference, INode node, Set<EObject> renamed, String newName) {
        var referenceText = getLinkingHelper().getCrossRefNodeAsString(node, true);
        var renamedReference = qualifiedNameConverter
                .toQualifiedName(referenceText)
                .skipLast(1)
                .append(newName);
        var existing = scopeProvider.getScope(source, reference.getEReference()).getSingleElement(renamedReference);
        if (existing == null) {
            return;
        }
        var existingElement = EcoreUtil.resolve(existing.getEObjectOrProxy(), source);
        if (!renamed.contains(existingElement)) {
            var fileName = source.eResource().getURI().lastSegment();
            throw refusal(qualifiedNameConverter.toString(renamedReference) + " in " + fileName + " already refers to "
                    + describe(existingElement));
        }
    }

    private void checkInWorkspace(Resource resource, List<String> roots, String name) {
        var uri = uriExtensions.toUriString(resource.getURI());
        for (var root : roots) {
            if (uri.startsWith(root)) {
                return;
            }
        }
        throw refusal(name + " is used in " + resource.getURI().lastSegment() + " outside the workspace");
    }

    private List<String> getWorkspaceRoots(InitializeParams initializeParams) {
        var roots = new ArrayList<String>();
        if (initializeParams == null) {
            return roots;
        }
        var workspaceFolders = initializeParams.getWorkspaceFolders();
        if (workspaceFolders != null && !workspaceFolders.isEmpty()) {
            for (var workspaceFolder : workspaceFolders) {
                roots.add(toDirectoryUri(workspaceFolder.getUri()));
            }
        } else if (initializeParams.getRootUri() != null) {
            roots.add(toDirectoryUri(initializeParams.getRootUri()));
        }
        return roots;
    }

    private String toDirectoryUri(String uri) {
        var normalized = uriExtensions.toUriString(uriExtensions.toUri(uri));
        if (normalized.endsWith("/")) {
            return normalized;
        }
        return normalized + "/";
    }

    private String getParentUri(URI uri) {
        return uriExtensions.toUriString(uri.trimSegments(1).appendSegment(""));
    }

    private String describe(EObject element) {
        var qualifiedName = qualifiedNameProvider.getFullyQualifiedName(element);
        if (qualifiedName != null) {
            return qualifiedNameConverter.toString(qualifiedName);
        }
        return NamingUtil.getName(element);
    }

    private static ILeafNode getNameLeaf(EObject element) {
        var nodes = NodeModelUtils.findNodesForFeature(element, OxstsPackage.Literals.NAMED_ELEMENT__NAME);
        if (nodes.isEmpty()) {
            return null;
        }
        return getLastLeaf(nodes.getFirst());
    }

    private static ILeafNode getLastLeaf(INode node) {
        ILeafNode last = null;
        for (var leaf : node.getLeafNodes()) {
            if (!leaf.isHidden()) {
                last = leaf;
            }
        }
        return last;
    }

    private static ResponseErrorException refusal(String message) {
        return new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, message, null));
    }
}
