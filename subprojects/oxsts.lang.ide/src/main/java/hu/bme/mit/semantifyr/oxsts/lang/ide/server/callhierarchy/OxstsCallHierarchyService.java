/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.callhierarchy;

import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.lang.ide.server.callhierarchy.CallHierarchyService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.references.RedefinitionChainCollector;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.CallSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Declaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.xtext.findReferences.IReferenceFinder;
import org.eclipse.xtext.findReferences.ReferenceAcceptor;
import org.eclipse.xtext.findReferences.TargetURICollector;
import org.eclipse.xtext.findReferences.TargetURIs;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.DocumentExtensions;
import org.eclipse.xtext.ide.util.CancelIndicatorProgressMonitor;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.resource.EObjectAtOffsetHelper;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

public class OxstsCallHierarchyService implements CallHierarchyService {

    @Inject
    private EObjectAtOffsetHelper eObjectAtOffsetHelper;

    @Inject
    private DocumentExtensions documentExtensions;

    @Inject
    private IReferenceFinder referenceFinder;

    @Inject
    private TargetURICollector targetURICollector;

    @Inject
    private Provider<TargetURIs> targetURIsProvider;

    @Inject
    private IResourceServiceProvider.Registry resourceServiceProviderRegistry;

    @Inject
    private RedefinitionChainCollector redefinitionChainCollector;

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private IQualifiedNameConverter qualifiedNameConverter;

    @Override
    public List<CallHierarchyItem> prepareCallHierarchy(
            Document document,
            XtextResource resource,
            CallHierarchyPrepareParams params,
            CancelIndicator cancelIndicator) {
        var offset = document.getOffSet(params.getPosition());
        var element = eObjectAtOffsetHelper.resolveElementAt(resource, offset);
        if (!isCallable(element)) {
            return List.of();
        }
        return List.of(toItem(element));
    }

    @Override
    public List<CallHierarchyIncomingCall> incomingCalls(
            Resource resource,
            CallHierarchyItem item,
            IReferenceFinder.IResourceAccess resourceAccess,
            IResourceDescriptions index,
            CancelIndicator cancelIndicator) {
        var callee = resolveItem(resource, item);
        if (!isCallable(callee)) {
            return List.of();
        }
        var targetURIs = targetURIsProvider.get();
        for (var member : redefinitionChainCollector.collect(callee)) {
            targetURICollector.add(member, targetURIs);
        }
        var calls = new LinkedHashMap<URI, CallHierarchyIncomingCall>();
        var acceptor = new ReferenceAcceptor(resourceServiceProviderRegistry, reference -> {
            if (!isCalleeReference(reference.getEReference())) {
                return;
            }
            var sourceUri = reference.getSourceEObjectUri();
            resourceAccess.readOnly(sourceUri, resourceSet -> {
                var source = resourceSet.getEObject(sourceUri, true);
                if (source != null && isCalledElement(source)) {
                    var caller = findCaller(source);
                    if (caller != null) {
                        calls.computeIfAbsent(
                                        EcoreUtil.getURI(caller),
                                        uri -> new CallHierarchyIncomingCall(toItem(caller), new ArrayList<>()))
                                .getFromRanges()
                                .add(callRange(source, reference.getEReference()));
                    }
                }
                return null;
            });
        });
        referenceFinder.findAllReferences(
                targetURIs, resourceAccess, index, acceptor, new CancelIndicatorProgressMonitor(cancelIndicator));
        return List.copyOf(calls.values());
    }

    @Override
    public List<CallHierarchyOutgoingCall> outgoingCalls(
            Resource resource, CallHierarchyItem item, CancelIndicator cancelIndicator) {
        var caller = resolveItem(resource, item);
        if (caller == null) {
            return List.of();
        }
        var calls = new LinkedHashMap<URI, CallHierarchyOutgoingCall>();
        var contents = caller.eAllContents();
        while (contents.hasNext()) {
            if (contents.next() instanceof CallSuffixExpression call) {
                var primary = call.getPrimary();
                var callee = getCalledElement(primary);
                if (isCallable(callee) && !callee.eIsProxy()) {
                    calls.computeIfAbsent(
                                    EcoreUtil.getURI(callee),
                                    uri -> new CallHierarchyOutgoingCall(toItem(callee), new ArrayList<>()))
                            .getFromRanges()
                            .add(callRange(primary, calleeReference(primary)));
                }
            }
        }
        return List.copyOf(calls.values());
    }

    private CallHierarchyItem toItem(EObject element) {
        var location = documentExtensions.newLocation(element);
        var item = new CallHierarchyItem(
                NamingUtil.getName(element),
                getKind(element),
                location.getUri(),
                documentExtensions.newFullLocation(element).getRange(),
                location.getRange());
        item.setDetail(getDetail(element));
        item.setData(element.eResource().getURIFragment(element));
        return item;
    }

    private SymbolKind getKind(EObject element) {
        return switch (element) {
            case TransitionDeclaration ignored -> SymbolKind.Method;
            case PropertyDeclaration ignored -> SymbolKind.Function;
            case FeatureDeclaration ignored -> SymbolKind.Field;
            case VariableDeclaration ignored -> SymbolKind.Variable;
            default -> SymbolKind.Object;
        };
    }

    private String getDetail(EObject element) {
        var owner = element.eContainer();
        if (owner == null) {
            return null;
        }
        var qualifiedName = qualifiedNameProvider.getFullyQualifiedName(owner);
        if (qualifiedName != null) {
            return qualifiedNameConverter.toString(qualifiedName);
        }
        return NamingUtil.getName(owner);
    }

    private EObject resolveItem(Resource resource, CallHierarchyItem item) {
        var fragment = switch (item.getData()) {
            case JsonPrimitive primitive -> primitive.getAsString();
            case String string -> string;
            case null, default -> null;
        };
        if (fragment == null) {
            return null;
        }
        return resource.getEObject(fragment);
    }

    private boolean isCallable(EObject element) {
        return element instanceof TransitionDeclaration || element instanceof PropertyDeclaration;
    }

    private boolean isCalleeReference(EReference reference) {
        return reference == OxstsPackage.Literals.ELEMENT_REFERENCE__ELEMENT
                || reference == OxstsPackage.Literals.NAVIGATION_SUFFIX_EXPRESSION__MEMBER;
    }

    private boolean isCalledElement(EObject source) {
        return source.eContainer() instanceof CallSuffixExpression call && call.getPrimary() == source;
    }

    private EObject getCalledElement(EObject primary) {
        return switch (primary) {
            case ElementReference reference -> reference.getElement();
            case NavigationSuffixExpression navigation -> navigation.getMember();
            case null, default -> null;
        };
    }

    private EReference calleeReference(EObject primary) {
        if (primary instanceof ElementReference) {
            return OxstsPackage.Literals.ELEMENT_REFERENCE__ELEMENT;
        }
        return OxstsPackage.Literals.NAVIGATION_SUFFIX_EXPRESSION__MEMBER;
    }

    private EObject findCaller(EObject source) {
        var current = source.eContainer();
        while (current != null && !(current instanceof Declaration)) {
            current = current.eContainer();
        }
        return current;
    }

    private Range callRange(EObject source, EReference reference) {
        return documentExtensions.newLocation(source, reference, -1).getRange();
    }
}
