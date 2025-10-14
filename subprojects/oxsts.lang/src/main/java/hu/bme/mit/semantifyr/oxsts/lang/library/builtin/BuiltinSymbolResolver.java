/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryResourceResolver;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.NamedElementResolver;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.naming.QualifiedName;

@Singleton
public class BuiltinSymbolResolver {

    public static final QualifiedName CONTROL_ANNOTATION_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("Control");
    public static final QualifiedName SHARED_ANNOTATION_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("Shared");

    public static final QualifiedName VERIFICATION_CASE_ANNOTATION_NAME = BuiltinLibrary.BUILTIN_VERIFICATION_LIBRARY_NAME.append("VerificationCase");
    public static final String VERIFICATION_CASE_EXPECTED_RESULTS_NAME = "expected";
    public static final String VERIFICATION_CASE_SUMMARY_NAME = "summary";

    public static final QualifiedName ANYTHING_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("Anything");
//    public static final String ANYTHING_CHILDREN_NAME = "children";
//    public static final String ANYTHING_PARENT_NAME = "parent";
    public static final String ANYTHING_INIT_NAME = "init";
    public static final String ANYTHING_MAIN_NAME = "main";
    public static final String ANYTHING_HAVOC_NAME = "havoc";

    public static final QualifiedName BOOL_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("bool");
    public static final QualifiedName INT_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("int");
    public static final QualifiedName REAL_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("real");
    public static final QualifiedName STRING_NAME = BuiltinLibrary.BUILTIN_LIBRARY_NAME.append("string");

    @Inject
    protected BuiltinLibrary builtinLibraryProvider;

    @Inject
    private OxstsQualifiedNameProvider qualifiedNameProvider;

    @Inject
    private NamedElementResolver namedElementResolver;

    @Inject
    protected LibraryResourceResolver libraryResourceResolver;

    public AnnotationDeclaration controlAnnotation(EObject context) {
        return findInBuiltin(context, AnnotationDeclaration.class, CONTROL_ANNOTATION_NAME);
    }

    public AnnotationDeclaration sharedAnnotation(EObject context) {
        return findInBuiltin(context, AnnotationDeclaration.class, SHARED_ANNOTATION_NAME);
    }

    public AnnotationDeclaration verificationCaseAnnotation(EObject context) {
        return findInBuiltinVerification(context, AnnotationDeclaration.class, VERIFICATION_CASE_ANNOTATION_NAME);
    }

    public ParameterDeclaration verificationCaseAnnotationExpectedResults(EObject context) {
        var verificationCase = verificationCaseAnnotation(context);
        return findInParameters(verificationCase, ParameterDeclaration.class, VERIFICATION_CASE_EXPECTED_RESULTS_NAME);
    }

    public ParameterDeclaration verificationCaseAnnotationSummary(EObject context) {
        var verificationCase = verificationCaseAnnotation(context);
        return findInParameters(verificationCase, ParameterDeclaration.class, VERIFICATION_CASE_SUMMARY_NAME);
    }

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

    protected <T extends Declaration> T findInAnything(EObject context, Class<T> type, String name) {
        var anythingClass = anythingClass(context);

        return findInClassDeclaration(anythingClass, type, name);
    }

//    public FeatureDeclaration anythingChildrenFeature(EObject context) {
//        return findInBuiltin(context, FeatureDeclaration.class, ANYTHING_CHILDREN_NAME);
//    }

//    public FeatureDeclaration anythingParentFeature(EObject context) {
//        return findInBuiltin(context, FeatureDeclaration.class, ANYTHING_PARENT_NAME);
//    }

    public TransitionDeclaration anythingInitTransition(EObject context) {
        return findInAnything(context, TransitionDeclaration.class, ANYTHING_INIT_NAME);
    }

    public TransitionDeclaration anythingMainTransition(EObject context) {
        return findInAnything(context, TransitionDeclaration.class, ANYTHING_MAIN_NAME);
    }

    public TransitionDeclaration anythingHavocTransition(EObject context) {
        return findInAnything(context, TransitionDeclaration.class, ANYTHING_HAVOC_NAME);
    }

    public boolean isBuiltin(EObject eObject) {
        return eObject.eResource().getURI() == builtinLibraryProvider.getBuiltinResourceUri();
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

    protected <T> T findInParameters(ParametricDeclaration parametricDeclaration, Class<T> type, String name) {
        for (var candidate : parametricDeclaration.getParameters()) {
            if (name.equals(NamingUtil.getName(candidate)) && type.isInstance(candidate)) {
                //noinspection unchecked
                return (T) candidate;
            }
        }

        throw new IllegalArgumentException("Built-in declaration '" + name + "' was not found in parametric declaration " + parametricDeclaration.getName());
    }

    protected <T extends Declaration> T findInClassDeclaration(ClassDeclaration classDeclaration, Class<T> type, String name) {
        for (var candidate : classDeclaration.getMembers()) {
            if (name.equals(NamingUtil.getName(candidate)) && type.isInstance(candidate)) {
                //noinspection unchecked
                return (T) candidate;
            }
        }

        throw new IllegalArgumentException("Built-in declaration '" + name + "' was not found in class " + classDeclaration.getName());
    }

    protected <T extends Declaration> T findInBuiltin(EObject context, Class<T> type, QualifiedName name) {
        var builtinResource = libraryResourceResolver.resolveResource(builtinLibraryProvider.getBuiltinResourceUri(), context);
        return findInResource(builtinResource, type, name);
    }

    protected <T extends Declaration> T findInBuiltinVerification(EObject context, Class<T> type, QualifiedName name) {
        var builtinResource = libraryResourceResolver.resolveResource(builtinLibraryProvider.getBuiltinVerificationResourceUri(), context);
        return findInResource(builtinResource, type, name);
    }

    protected <T extends Declaration> T findInResource(Resource resource, Class<T> type, QualifiedName name) {
        return namedElementResolver.findQualifiedElement(resource, type, name);
    }

}
