/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.validation;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.resource.ILocationInFileProvider;
import org.eclipse.xtext.validation.Check;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class contains custom validation rules.
 * <p>
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#validation
 */
@SuppressWarnings("unused") // check functions are used by reflection
public class OxstsValidator extends AbstractOxstsValidator {

    @Inject
    private ILocationInFileProvider locationInFileProvider;

    @Inject
    private RedefinitionHandler redefinitionHandler;

    @Inject
    private OppositeHandler oppositeHandler;

    @Inject
    private DomainMemberCollectionProvider domainMemberCollectionProvider;

    @Inject
    private MetaConstantExpressionEvaluatorProvider metaConstantExpressionEvaluatorProvider;

    private static final String ISSUE_PREFIX = "OXSTS.";
    public static final String DUPLICATE_NAME_ISSUE = ISSUE_PREFIX + "DUPLICATE_NAME";
    public static final String DATA_TYPE_NOT_IN_BUILTIN_ISSUE = ISSUE_PREFIX + "DATA_TYPE_NOT_IN_BUILTIN";
    public static final String REDEFINED_NOT_FOUND_ISSUE = ISSUE_PREFIX + "REDEFINED_NOT_FOUND";
    public static final String MEMBER_NOT_IN_ABSTRACT_CLASS = ISSUE_PREFIX + "MEMBER_NOT_IN_ABSTRACT_CLASS";
    public static final String ELEMENT_SHADOWED = ISSUE_PREFIX + "ELEMENT_SHADOWED";
    public static final String INVALID_CALL_ARGUMENTS_COUND = ISSUE_PREFIX + "INVALID_CALL_ARGUMENTS_COUNT";
    public static final String INCORRECT_OPPOSITE = ISSUE_PREFIX + "INCORRECT_OPPOSITE";
    public static final String ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED = ISSUE_PREFIX + "ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED";
    public static final String ABSTRACT_FEATURE_INHERITED_MEMBERS_NOT_REDEFINED = ISSUE_PREFIX + "ABSTRACT_FEATURE_INHERITED_MEMBERS_NOT_REDEFINED";

    @Inject
    protected BuiltinSymbolResolver builtinSymbolResolver;

    @Override
    protected void handleExceptionDuringValidation(Throwable targetException) throws RuntimeException {
        // swallow all exceptions!
    }

//    @Check
//    public void featuresMustBeInAbstractClass(FeatureDeclaration featureDeclaration) {
//        if (featureDeclaration.getKind() == FeatureKind.FEATURE && featureDeclaration.eContainer() instanceof ClassDeclaration containerClass && ! containerClass.isAbstract()) {
//            acceptError("Abstract features can only be declared in abstract classes.", featureDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, MEMBER_NOT_IN_ABSTRACT_CLASS);
//        }
//    }
//
//    @Check
//    public void featuresMustBeInAbstractFeatures(FeatureDeclaration featureDeclaration) {
//        if (featureDeclaration.getKind() == FeatureKind.FEATURE && featureDeclaration.eContainer() instanceof FeatureDeclaration containerFeature && featureDeclaration.getKind() != FeatureKind.FEATURE) {
//            acceptError("Abstract features can only be declared in abstract features.", featureDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, MEMBER_NOT_IN_ABSTRACT_CLASS);
//        }
//    }

    @Check
    public void abstractTransitionMustBeInAbstractClass(TransitionDeclaration transitionDeclaration) {
        var containerClass = EcoreUtil2.getContainerOfType(transitionDeclaration, ClassDeclaration.class);
        if (transitionDeclaration.isAbstract() && containerClass != null && ! containerClass.isAbstract()) {
            acceptError("Abstract transitions can only be declared in abstract classes.", transitionDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, MEMBER_NOT_IN_ABSTRACT_CLASS);
        }
    }

    @Check
    public void abstractPropertyMustBeInAbstractClass(PropertyDeclaration propertyDeclaration) {
        var containerClass = EcoreUtil2.getContainerOfType(propertyDeclaration, ClassDeclaration.class);
        if (propertyDeclaration.isAbstract() && containerClass != null && ! containerClass.isAbstract()) {
            acceptError("Abstract properties can only be declared in abstract classes.", propertyDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, MEMBER_NOT_IN_ABSTRACT_CLASS);
        }
    }

    @Check
    public void allInheritedAbstractMembersMustBeRealizedInNonAbstractClass(ClassDeclaration classDeclaration) {
        if (! classDeclaration.isAbstract()) {
            var memberCollection = domainMemberCollectionProvider.getMemberCollection(classDeclaration);
            var abstractInheritedMembers = memberCollection.getDeclarations().stream()
                    .filter(OxstsUtils::isDeclarationAbstract)
                    .filter(m -> m.eContainer() != classDeclaration)
                    .toList();

            if (! abstractInheritedMembers.isEmpty()) {
                var message = "Class '" + NamingUtil.getName(classDeclaration) +
                        "' must either be declared abstract or redefine abstract members: " +
                        abstractInheritedMembers.stream().map(m -> "'" + NamingUtil.getName(m) + "'").collect(Collectors.joining(", "));

                acceptError(message, classDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED);
            }
        }
    }

//    @Check
//    public void allInheritedAbstractMembersMustBeRealizedInNonAbstractFeature(FeatureDeclaration featureDeclaration) {
//        if (featureDeclaration.getKind() != FeatureKind.FEATURE) {
//            var memberCollection = domainMemberCollectionProvider.getMemberCollection(featureDeclaration);
//            var abstractInheritedMembers = memberCollection.getDeclarations().stream()
//                    .filter(OxstsUtils::isDeclarationAbstract)
//                    .filter(m -> m.eContainer() != featureDeclaration)
//                    .toList();
//
//            if (! abstractInheritedMembers.isEmpty()) {
//                var message = "Feature '" + NamingUtil.getName(featureDeclaration) +
//                        "' must either be declared abstract or redefine abstract members: " +
//                        abstractInheritedMembers.stream().map(m -> "'" + NamingUtil.getName(m) + "'").collect(Collectors.joining(", "));
//
//                acceptError(message, featureDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, ABSTRACT_FEATURE_INHERITED_MEMBERS_NOT_REDEFINED);
//            }
//        }
//    }

    @Check
    public void checkNoRedefinedDeclarations(RedefinableDeclaration redefinableDeclaration) {
        if (redefinableDeclaration.isRedefine()) {
            try {
                var redefined = redefinitionHandler.getRedefinedDeclaration(redefinableDeclaration);
                if (redefined == null) {
                    acceptError("Could not find redefined declaration.", redefinableDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, REDEFINED_NOT_FOUND_ISSUE);
                }
            } catch (Exception e) {
                acceptError("Could not find redefined declaration.", redefinableDeclaration, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, REDEFINED_NOT_FOUND_ISSUE);
            }
        }
    }

    @Check
    public void checkShadowedNames(NamedElement namedElement) {
        if (namedElement instanceof RedefinableDeclaration redefinableDeclaration && redefinableDeclaration.isRedefine()) {
            return;
        }

        var parentDomain = EcoreUtil2.getContainerOfType(namedElement.eContainer(), DomainDeclaration.class);
        if (parentDomain == null) {
            return;
        }
        var declarations = domainMemberCollectionProvider.getParentCollection(parentDomain).getDeclarations();

        if (declarations.stream().anyMatch(d -> Objects.equals(NamingUtil.getName(d), namedElement.getName()))) {
            acceptWarning("Name shadows another element.", namedElement, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, ELEMENT_SHADOWED);
        }
    }

    @Check
    public void checkDataTypeOnlyInBuiltin(DataTypeDeclaration dataTypeDeclaration) {
        if (! builtinSymbolResolver.isBuiltin(dataTypeDeclaration)) {
            var message = "Custom data types are not allowed!";
            acceptError(message, dataTypeDeclaration, DATA_TYPE_NOT_IN_BUILTIN_ISSUE);
        }
    }

    @Check
    public void checkOpposite(FeatureDeclaration featureDeclaration) {
        var opposite = featureDeclaration.getOpposite();

        if (opposite != null) {
            var oppositeOpposite = opposite.getOpposite();

            if (oppositeOpposite == null) {
                var message = String.format("Expected feature to have opposite '%s'.", NamingUtil.getName(featureDeclaration));
                acceptError(message, opposite, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, INCORRECT_OPPOSITE);
            } else if (oppositeOpposite != featureDeclaration) {
                var oppositeMessage = String.format("Expected feature to have opposite '%s', got '%s' instead.", NamingUtil.getName(featureDeclaration), NamingUtil.getName(oppositeOpposite));
                acceptError(oppositeMessage, opposite, OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE, 0, INCORRECT_OPPOSITE);
            } else {
                if (featureDeclaration.getKind() == FeatureKind.CONTAINER && opposite.getKind() != FeatureKind.CONTAINMENT) {
                    acceptError("Container's opposite must be containment.", featureDeclaration, OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE, 0, INCORRECT_OPPOSITE);
                } else if (featureDeclaration.getKind() == FeatureKind.CONTAINMENT && opposite.getKind() != FeatureKind.CONTAINER) {
                    acceptError("Containment's opposite must be container.", featureDeclaration, OxstsPackage.Literals.FEATURE_DECLARATION__OPPOSITE, 0, INCORRECT_OPPOSITE);
                }
            }
        } else if (featureDeclaration.getKind() == FeatureKind.CONTAINER) {
            acceptError("Container feature must have an opposite.", featureDeclaration, INCORRECT_OPPOSITE);
        }
    }

    @Check
    public void checkUniqueDeclarations(OxstsModelPackage oxstsPackage) {
        checkUniqueSimpleNames(oxstsPackage.getDeclarations());
    }

    @Check
    public void checkUniqueEnumLiterals(EnumDeclaration enumDeclaration) {
        checkUniqueSimpleNames(enumDeclaration.getLiterals());
    }

    @Check
    public void checkUniqueMembers(RecordDeclaration recordDeclaration) {
        checkUniqueSimpleNames(recordDeclaration.getMembers());
    }

    @Check
    public void checkUniqueMembers(ClassDeclaration classDeclaration) {
        checkUniqueSimpleNames(classDeclaration.getMembers());
    }

    @Check
    public void checkUniqueMembers(FeatureDeclaration featureDeclaration) {
        checkUniqueSimpleNames(featureDeclaration.getInnerFeatures());
    }

    @Check
    public void checkCallExpressionArgumentCount(CallSuffixExpression callSuffixExpression) {
        var primary = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());

        if (! (primary instanceof ParametricDeclaration parametricDeclaration)) {
            return;
        }

        if (parametricDeclaration.getParameters().size() != callSuffixExpression.getArguments().size()) {
            acceptError("Expected " + parametricDeclaration.getParameters().size() + " arguments, found " + callSuffixExpression.getArguments().size(), callSuffixExpression, INVALID_CALL_ARGUMENTS_COUND);
        }
    }

    protected void checkUniqueSimpleNames(Iterable<? extends NamedElement> namedElements) {
        var names = new LinkedHashMap<String, Set<NamedElement>>();
        for (var namedElement : namedElements) {
            var name = NamingUtil.getName(namedElement);
            var elementsWithName = names.computeIfAbsent(name, ignored -> new LinkedHashSet<>());
            elementsWithName.add(namedElement);
        }
        for (var entry : names.entrySet()) {
            var elementsWithName = entry.getValue();
            if (elementsWithName.size() <= 1) {
                continue;
            }
            var name = entry.getKey();
            var message = "Duplicate name '%s'.".formatted(name);
            for (var namedElement : elementsWithName) {
                acceptError(message, namedElement, OxstsPackage.Literals.NAMED_ELEMENT__NAME, 0, DUPLICATE_NAME_ISSUE);
            }
        }
    }

    protected void acceptError(String message, EObject object, String code, String... issueData) {
        var region = locationInFileProvider.getFullTextRegion(object);
        acceptError(message, object, region.getOffset(), region.getLength(), code, issueData);
    }

}
