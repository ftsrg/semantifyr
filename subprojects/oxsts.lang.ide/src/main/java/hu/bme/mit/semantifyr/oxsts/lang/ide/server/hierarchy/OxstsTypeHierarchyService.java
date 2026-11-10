/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.hierarchy;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.lang.ide.server.hierarchy.HierarchyItemData;
import hu.bme.mit.semantifyr.lang.ide.server.hierarchy.TypeHierarchyService;
import hu.bme.mit.semantifyr.oxsts.lang.ide.server.references.RedefinitionChainCollector;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.InheritanceHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RedefinableDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.DocumentExtensions;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.resource.EObjectAtOffsetHelper;
import org.eclipse.xtext.resource.IResourceDescriptionsProvider;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

public class OxstsTypeHierarchyService implements TypeHierarchyService {

    @Inject
    private EObjectAtOffsetHelper eObjectAtOffsetHelper;

    @Inject
    private InheritanceHandler inheritanceHandler;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Inject
    private RedefinitionChainCollector redefinitionChainCollector;

    @Inject
    private IResourceDescriptionsProvider resourceDescriptionsProvider;

    @Inject
    private DocumentExtensions documentExtensions;

    @Inject
    private IQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private IQualifiedNameConverter qualifiedNameConverter;

    @Override
    public List<TypeHierarchyItem> prepareTypeHierarchy(
            Document document,
            XtextResource resource,
            TypeHierarchyPrepareParams params,
            CancelIndicator cancelIndicator) {
        var element = eObjectAtOffsetHelper.resolveElementAt(resource, document.getOffSet(params.getPosition()));
        while (element != null
                && !(element instanceof ClassDeclaration)
                && !(element instanceof RedefinableDeclaration)) {
            element = element.eContainer();
        }
        if (element == null || element.eIsProxy()) {
            return List.of();
        }
        return createItems(List.of(element), params.getTextDocument().getUri());
    }

    @Override
    public List<TypeHierarchyItem> supertypes(Resource documentResource, TypeHierarchyItem item) {
        var data = HierarchyItemData.of(item.getData());
        return switch (data.resolve(documentResource)) {
            case ClassDeclaration classDeclaration -> createItems(superClasses(classDeclaration), data.documentUri());
            case RedefinableDeclaration declaration -> createItems(redefined(declaration), data.documentUri());
            case null, default -> List.of();
        };
    }

    @Override
    public List<TypeHierarchyItem> subtypes(Resource documentResource, TypeHierarchyItem item) {
        var data = HierarchyItemData.of(item.getData());
        return switch (data.resolve(documentResource)) {
            case ClassDeclaration classDeclaration -> createItems(subClasses(classDeclaration), data.documentUri());
            case RedefinableDeclaration declaration -> createItems(redefiners(declaration), data.documentUri());
            case null, default -> List.of();
        };
    }

    private List<ClassDeclaration> superClasses(ClassDeclaration classDeclaration) {
        var superClasses = new ArrayList<ClassDeclaration>();
        for (var superDomain : inheritanceHandler.getSuperDomains(classDeclaration)) {
            if (superDomain instanceof ClassDeclaration superClass) {
                superClasses.add(superClass);
            }
        }
        return superClasses;
    }

    private Collection<ClassDeclaration> subClasses(ClassDeclaration classDeclaration) {
        var resourceSet = classDeclaration.eResource().getResourceSet();
        var index = resourceDescriptionsProvider.getResourceDescriptions(resourceSet);
        var subClasses = new LinkedHashSet<ClassDeclaration>();
        for (var description : index.getExportedObjectsByType(OxstsPackage.Literals.CLASS_DECLARATION)) {
            if (resourceSet.getEObject(description.getEObjectURI(), true) instanceof ClassDeclaration candidate
                    && inheritanceHandler.getSuperDomains(candidate).contains(classDeclaration)) {
                subClasses.add(candidate);
            }
        }
        return subClasses;
    }

    private List<EObject> redefined(RedefinableDeclaration declaration) {
        var redefined = redefinitionHandler.getRedefinedDeclaration(declaration);
        if (redefined == null
                || !redefinitionChainCollector.collect(declaration).contains(redefined)) {
            return List.of();
        }
        return List.of(redefined);
    }

    private List<EObject> redefiners(RedefinableDeclaration declaration) {
        var redefiners = new ArrayList<EObject>();
        for (var member : redefinitionChainCollector.collect(declaration)) {
            if (member instanceof RedefinableDeclaration redefiner
                    && redefinitionHandler.getRedefinedDeclaration(redefiner) == declaration) {
                redefiners.add(redefiner);
            }
        }
        return redefiners;
    }

    private List<TypeHierarchyItem> createItems(Collection<? extends EObject> elements, String documentUri) {
        var items = new ArrayList<TypeHierarchyItem>();
        for (var element : elements) {
            var name = NamingUtil.getName(element);
            var location = documentExtensions.newLocation(element);
            var fullLocation = documentExtensions.newFullLocation(element);
            if (name == null || location == null || fullLocation == null) {
                continue;
            }
            var item = new TypeHierarchyItem(
                    name, symbolKind(element), location.getUri(), fullLocation.getRange(), location.getRange());
            item.setDetail(detail(element));
            item.setData(
                    new HierarchyItemData(documentUri, EcoreUtil.getURI(element).toString()));
            items.add(item);
        }
        return items;
    }

    private SymbolKind symbolKind(EObject element) {
        return switch (element) {
            case ClassDeclaration _ -> SymbolKind.Class;
            case TransitionDeclaration _ -> SymbolKind.Method;
            case PropertyDeclaration _ -> SymbolKind.Function;
            case FeatureDeclaration _ -> SymbolKind.Field;
            default -> SymbolKind.Property;
        };
    }

    private String detail(EObject element) {
        var container = element.eContainer();
        if (container == null) {
            return null;
        }
        var qualifiedName = qualifiedNameProvider.getFullyQualifiedName(container);
        if (qualifiedName == null) {
            return null;
        }
        return qualifiedNameConverter.toString(qualifiedName);
    }
}
