/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IResourceDescription;

@Singleton
public class BuiltinSymbolResolver {

    public static final QualifiedName ANYTHING_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("Anything");
    public static final QualifiedName ANYTHING_CHILDREN_NAME = ANYTHING_NAME.append("children");
    public static final QualifiedName ANYTHING_PARENT_NAME = ANYTHING_NAME.append("parent");
    public static final QualifiedName ANYTHING_INIT_NAME = ANYTHING_NAME.append("init");
    public static final QualifiedName ANYTHING_MAIN_NAME = ANYTHING_NAME.append("main");
    public static final QualifiedName ANYTHING_HAVOC_NAME = ANYTHING_NAME.append("havoc");

    public static final QualifiedName BOOL_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("bool");
    public static final QualifiedName INT_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("int");
    public static final QualifiedName REAL_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("real");
    public static final QualifiedName STRING_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("string");

    @Inject
    protected LibraryAdapterFinder libraryAdapterFinder;

    @Inject
    protected BuiltinLibrary builtinLibrary;

    @Inject
    private OxstsQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private IResourceDescription.Manager descriptionManager;

    public ClassDeclaration anythingClass(EObject context) {
        return findInBuiltin(context, ClassDeclaration.class, ANYTHING_NAME);
    }

    public DataTypeDeclaration boolDatatype(EObject context) {
        return findInBuiltin(context, DataTypeDeclaration.class, BOOL_NAME);
    }

    public DataTypeDeclaration intDatatype(EObject context) {
        return findInBuiltin(context, DataTypeDeclaration.class, INT_NAME);
    }

    public DataTypeDeclaration realDatatype(EObject context) {
        return findInBuiltin(context, DataTypeDeclaration.class, REAL_NAME);
    }

    public DataTypeDeclaration stringDatatype(EObject context) {
        return findInBuiltin(context, DataTypeDeclaration.class, STRING_NAME);
    }

//    public FeatureDeclaration anythingChildrenFeature(EObject context) {
//        return findInBuiltin(context, FeatureDeclaration.class, ANYTHING_CHILDREN_NAME);
//    }

//    public FeatureDeclaration anythingParentFeature(EObject context) {
//        return findInBuiltin(context, FeatureDeclaration.class, ANYTHING_PARENT_NAME);
//    }

    public TransitionDeclaration anythingInitTransition(EObject context) {
        return findInBuiltin(context, TransitionDeclaration.class, ANYTHING_INIT_NAME);
    }

    public TransitionDeclaration anythingMainTransition(EObject context) {
        return findInBuiltin(context, TransitionDeclaration.class, ANYTHING_MAIN_NAME);
    }

    public TransitionDeclaration anythingHavocTransition(EObject context) {
        return findInBuiltin(context, TransitionDeclaration.class, ANYTHING_HAVOC_NAME);
    }

    public boolean isBuiltin(EObject eObject) {
        return eObject.eResource().getURI() == builtinLibrary.getBuiltinResourceUri();
    }

    protected boolean isNamed(EObject eObject, QualifiedName name) {
        return name.equals(qualifiedNameProvider.getFullyQualifiedName(eObject));
    }

    public boolean isAnythingClass(ClassDeclaration classDeclaration) {
        return isBuiltin(classDeclaration) && isNamed(classDeclaration, ANYTHING_NAME);
    }

//    public boolean isAnythingParentFeature(FeatureDeclaration featureDeclaration) {
//        return isBuiltin(featureDeclaration) && isNamed(featureDeclaration, ANYTHING_PARENT_NAME);
//    }

//    public boolean isAnythingChildrenFeature(FeatureDeclaration featureDeclaration) {
//        return isBuiltin(featureDeclaration) && isNamed(featureDeclaration, ANYTHING_CHILDREN_NAME);
//    }

    protected <T extends Declaration> T findInBuiltin(EObject context, Class<T> type, QualifiedName name) {
        var builtinResource = libraryAdapterFinder.getOrInstall(context).getBuiltinResource();
        var builtins = descriptionManager.getResourceDescription(builtinResource);

        for (var candidate : builtins.getExportedObjects(OxstsPackage.Literals.ELEMENT, name, false)) {
            if (type.isInstance(candidate.getEObjectOrProxy())) {
                // Will always be a loaded EObject, since we force load the resource
                return (T) candidate.getEObjectOrProxy();
            }
        }

        throw new IllegalArgumentException("Built-in declaration '" + name.toString("::") + "' was not found");
    }

}
