/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.validation;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.OppositeHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.PropertyTypeHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.RedefinitionHandler;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.MetaConstantExpressionEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.ExpressionModalityEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.modality.Modality;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.ExpressionTypeEvaluatorProvider;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.InvalidTypeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeCompatibility;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem.TypeEvaluation;
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

    @Inject
    private ExpressionModalityEvaluatorProvider modalityEvaluatorProvider;

    @Inject
    private ExpressionTypeEvaluatorProvider typeEvaluatorProvider;

    @Inject
    private TypeCompatibility typeCompatibility;

    @Inject
    private PropertyTypeHandler propertyTypeHandler;

    @Inject
    private MultiplicityRangeEvaluator multiplicityRangeEvaluator;

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
    public static final String INCORRECT_ASSIGNMENT = ISSUE_PREFIX + "INCORRECT_ASSIGNMENT";
    public static final String INCORRECT_CALLED_ELEMENT = ISSUE_PREFIX + "INCORRECT_CALLED_ELEMENT";
    public static final String VARIABLE_WITH_IMPLICIT_TYPE = ISSUE_PREFIX + "VARIABLE_WITH_IMPLICIT_TYPE";
    public static final String UNSUPPORTED_FEATURE = ISSUE_PREFIX + "UNSUPPORTED_FEATURE";
    public static final String EXPRESSION_MODALITY_TOO_HIGH = ISSUE_PREFIX + "EXPRESSION_MODALITY_TOO_HIGH";
    public static final String TYPE_MISMATCH = ISSUE_PREFIX + "TYPE_MISMATCH";
    public static final String INVALID_CAST = ISSUE_PREFIX + "INVALID_CAST";

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
    public void checkRecordDeclarationNotYetSupported(RecordDeclaration recordDeclaration) {
        acceptError(
                "Record types are not yet supported by the compiler. Use a class or a primitive type instead.",
                recordDeclaration,
                OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                0,
                UNSUPPORTED_FEATURE
        );
    }

    @Check
    public void checkTransitionKindSupported(TransitionDeclaration transitionDeclaration) {
        var kind = transitionDeclaration.getKind();
        if (kind == TransitionKind.ENV || kind == TransitionKind.HAVOC) {
            acceptError(
                "Transition kind `" + kind.getLiteral() + "` is not yet supported by the compiler.",
                transitionDeclaration,
                UNSUPPORTED_FEATURE
            );
        }
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

        var parameters = parametricDeclaration.getParameters();
        var arguments = callSuffixExpression.getArguments();

        if (arguments.size() > parameters.size()) {
            acceptError(
                "Expected at most " + parameters.size() + " arguments, found " + arguments.size() + ".",
                callSuffixExpression,
                INVALID_CALL_ARGUMENTS_COUND
            );
            return;
        }

        var minimumRequired = parameters.stream().filter(p -> !isParameterOptional(p)).count();
        if (arguments.size() < minimumRequired) {
            var unbound = parameters.stream()
                .filter(p -> !isParameterOptional(p))
                .skip(arguments.size())
                .map(NamedElement::getName)
                .collect(Collectors.joining(", "));
            acceptError(
                "Missing argument(s) for non-optional parameter(s): " + unbound + ".",
                callSuffixExpression,
                INVALID_CALL_ARGUMENTS_COUND
            );
        }
    }

    @Check
    public void checkParameterOrder(ParametricDeclaration parametricDeclaration) {
        ParameterDeclaration firstOptional = null;
        for (var parameter : parametricDeclaration.getParameters()) {
            boolean optional = isParameterOptional(parameter);
            if (optional) {
                if (firstOptional == null) {
                    firstOptional = parameter;
                }
            } else if (firstOptional != null) {
                var optionalName = firstOptional.getName() != null ? firstOptional.getName() : "<unnamed>";
                var paramName = parameter.getName() != null ? parameter.getName() : "<unnamed>";
                acceptError(
                    "Non-optional parameter '" + paramName + "' cannot follow optional parameter '"
                        + optionalName + "'. Declare required parameters before optional ones.",
                    parameter,
                    OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                    0,
                    INVALID_CALL_ARGUMENTS_COUND
                );
            }
        }
    }

    /**
     * A parameter is optional iff its declared multiplicity's lower bound
     * is 0 (i.e. {@code [0..1]}, {@code [0..*]}, or {@code []}). Parameters
     * with no explicit multiplicity default to ONE and are therefore
     * required.
     */
    private boolean isParameterOptional(ParameterDeclaration parameter) {
        var typeSpec = parameter.getTypeSpecification();
        if (typeSpec == null) {
            return false;
        }
        try {
            var range = multiplicityRangeEvaluator.evaluate(typeSpec);
            return range != null && range.getLowerBound() == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Check
    public void checkCallExpressionArgumentTypes(CallSuffixExpression callSuffixExpression) {
        var primary = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());
        if (!(primary instanceof ParametricDeclaration parametricDeclaration)) {
            return;
        }
        var parameters = parametricDeclaration.getParameters();
        var arguments = callSuffixExpression.getArguments();
        int pairs = Math.min(parameters.size(), arguments.size());
        for (int i = 0; i < pairs; i++) {
            var argument = arguments.get(i);
            if (argument.getParameter() != null) {
                // Named argument: rely on its resolved parameter if any.
                checkArgumentAssignableToParameter(argument, argument.getParameter());
            } else {
                checkArgumentAssignableToParameter(argument, parameters.get(i));
            }
        }
    }

    @Check
    public void checkAnnotationArgumentTypes(Annotation annotation) {
        var declaration = annotation.getDeclaration();
        if (declaration == null || declaration.eIsProxy()) {
            return;
        }
        var parameters = declaration.getParameters();
        var arguments = annotation.getArguments();
        int positional = 0;
        for (var argument : arguments) {
            if (argument.getParameter() != null) {
                checkArgumentAssignableToParameter(argument, argument.getParameter());
            } else if (positional < parameters.size()) {
                checkArgumentAssignableToParameter(argument, parameters.get(positional));
                positional++;
            }
        }
    }

    private void checkArgumentAssignableToParameter(Argument argument, ParameterDeclaration parameter) {
        if (parameter == null || parameter.eIsProxy() || parameter.getTypeSpecification() == null) {
            return;
        }
        Expression expression = argument.getExpression();
        if (expression == null) {
            return;
        }
        TypeEvaluation expressionType = evaluateTypeSafely(expression);
        if (expressionType == null) {
            return;
        }
        TypeEvaluation parameterType = typeEvaluatorProvider.getEvaluator(parameter).fromTypeSpecification(parameter.getTypeSpecification());
        if (!typeCompatibility.isAssignable(parameterType, expressionType, argument)) {
            acceptError(
                "Argument of type " + formatType(expressionType)
                    + " is not assignable to parameter '" + parameter.getName() + "' of type " + formatType(parameterType) + ".",
                argument,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkHavocTargetIsVariable(HavocOperation havocOperation) {
        var reference = havocOperation.getReference();
        if (reference == null) {
            return;
        }
        var target = metaConstantExpressionEvaluatorProvider.evaluate(reference);
        if (!(target instanceof VariableDeclaration)) {
            acceptError(
                "havoc target must be a variable reference.",
                havocOperation,
                OxstsPackage.Literals.HAVOC_OPERATION__REFERENCE,
                0,
                INCORRECT_ASSIGNMENT
            );
        }
    }

    @Check
    public void checkPropertyReturnType(PropertyDeclaration propertyDeclaration) {
        var expression = propertyDeclaration.getExpression();
        if (expression == null) {
            return;
        }
        var returnTypeSpec = propertyTypeHandler.getPropertyReturnType(propertyDeclaration);
        if (returnTypeSpec == null) {
            return;
        }
        var expected = typeEvaluatorProvider.getEvaluator(propertyDeclaration).fromTypeSpecification(returnTypeSpec);
        var actual = evaluateTypeSafely(expression);
        if (actual == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(expected, actual, expression)) {
            acceptError(
                "Property body has type " + formatType(actual)
                    + " but is expected to return " + formatType(expected) + ".",
                propertyDeclaration,
                OxstsPackage.Literals.PROPERTY_DECLARATION__EXPRESSION,
                0,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkVariableInitializerType(VariableDeclaration variableDeclaration) {
        if (OxstsUtils.isLoopVariable(variableDeclaration)) {
            return;
        }
        var initializer = variableDeclaration.getExpression();
        if (initializer == null || variableDeclaration.getTypeSpecification() == null) {
            return;
        }
        var declared = typeEvaluatorProvider.getEvaluator(variableDeclaration).fromTypeSpecification(variableDeclaration.getTypeSpecification());
        var actual = evaluateTypeSafely(initializer);
        if (actual == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(declared, actual, variableDeclaration)) {
            acceptError(
                "Initializer of type " + formatType(actual)
                    + " is not assignable to variable '" + variableDeclaration.getName() + "' of type " + formatType(declared) + ".",
                variableDeclaration,
                OxstsPackage.Literals.VARIABLE_DECLARATION__EXPRESSION,
                0,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkFeatureBoundExpressionType(FeatureDeclaration featureDeclaration) {
        var expression = featureDeclaration.getExpression();
        if (expression == null || featureDeclaration.getTypeSpecification() == null) {
            return;
        }
        var declared = typeEvaluatorProvider.getEvaluator(featureDeclaration).fromTypeSpecification(featureDeclaration.getTypeSpecification());
        var actual = evaluateTypeSafely(expression);
        if (actual == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(declared, actual, featureDeclaration)) {
            acceptError(
                "Feature bound expression of type " + formatType(actual)
                    + " is not assignable to '" + featureDeclaration.getName() + "' of type " + formatType(declared) + ".",
                featureDeclaration,
                OxstsPackage.Literals.FEATURE_DECLARATION__EXPRESSION,
                0,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkForRangeType(AbstractForOperation forOperation) {
        var range = forOperation.getRangeExpression();
        if (range == null) {
            return;
        }
        var type = evaluateTypeSafely(range);
        if (type == null || type instanceof InvalidTypeEvaluation) {
            return;
        }
        if (typeCompatibility.isFeatureType(type) || typeCompatibility.isClassType(type)) {
            return;
        }
        var rangeEvaluation = type.getRange();
        boolean isCollection = rangeEvaluation != null && !(rangeEvaluation.getLowerBound() == 1 && rangeEvaluation.getUpperBound() == 1);
        boolean isIntRange = typeCompatibility.isNumeric(type, forOperation) && rangeEvaluation != null && rangeEvaluation.getLowerBound() != rangeEvaluation.getUpperBound();
        if (!isCollection && !isIntRange) {
            acceptError(
                "`for` range must be a collection or an integer range (got " + formatType(type) + ").",
                forOperation,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkRedefinitionSignatureCompatible(RedefinableDeclaration declaration) {
        if (!declaration.isRedefine()) {
            return;
        }
        RedefinableDeclaration inherited;
        try {
            inherited = redefinitionHandler.getRedefinedDeclaration(declaration);
        } catch (Exception e) {
            return;
        }
        if (inherited == null) {
            return;
        }
        var declaredSpec = typeSpecificationOf(declaration);
        var inheritedSpec = typeSpecificationOf(inherited);
        if (declaredSpec == null || inheritedSpec == null) {
            return;
        }
        var declaredType = typeEvaluatorProvider.getEvaluator(declaration).fromTypeSpecification(declaredSpec);
        var inheritedType = typeEvaluatorProvider.getEvaluator(inherited).fromTypeSpecification(inheritedSpec);
        if (!typeCompatibility.isAssignable(inheritedType, declaredType, declaration)) {
            acceptError(
                "Redefinition has type " + formatType(declaredType)
                    + " which is not assignable to the inherited type " + formatType(inheritedType) + ".",
                declaration,
                OxstsPackage.Literals.NAMED_ELEMENT__NAME,
                0,
                TYPE_MISMATCH
            );
        }
    }

    private TypeSpecification typeSpecificationOf(RedefinableDeclaration declaration) {
        return switch (declaration) {
            case VariableDeclaration variable -> variable.getTypeSpecification();
            case FeatureDeclaration feature -> feature.getTypeSpecification();
            case PropertyDeclaration property -> propertyTypeHandler.getPropertyReturnType(property);
            default -> null;
        };
    }

    @Check
    public void noAssignmentsToConstants(AssignmentOperation assignmentOperation) {
        var assigned = metaConstantExpressionEvaluatorProvider.evaluate(assignmentOperation.getReference());

        if (!(assigned instanceof VariableDeclaration)) {
            acceptError("Only variables can be assigned to!", assignmentOperation, OxstsPackage.Literals.ASSIGNMENT_OPERATION__REFERENCE, 0, INCORRECT_ASSIGNMENT);
        }
    }

    @Check
    public void checkValidCalledElements(CallSuffixExpression callSuffixExpression) {
        var called = metaConstantExpressionEvaluatorProvider.evaluate(callSuffixExpression.getPrimary());

        if (! OxstsUtils.isCallable(called)) {
            acceptError("Element " + called.getName() + " is not callable!", callSuffixExpression, OxstsPackage.Literals.POSTFIX_UNARY_EXPRESSION__PRIMARY, 0, INCORRECT_CALLED_ELEMENT);
        }
    }

    @Check
    public void checkFeatureBoundExpressionModality(FeatureDeclaration featureDeclaration) {
        var expression = featureDeclaration.getExpression();
        if (expression == null) {
            return;
        }
        checkModalityAtMost(
            expression,
            Modality.COMPILE_TIME,
            "Feature bound expression must be evaluable at compile time"
        );
    }

    @Check
    public void checkMultiplicityNotBareInfinity(DefiniteMultiplicity definiteMultiplicity) {
        if (definiteMultiplicity.getExpression() instanceof LiteralInfinity) {
            acceptError(
                "`[*]` is not a valid multiplicity. Use `[]` for unbounded or `[lb..*]` for a lower-bounded unbounded range.",
                definiteMultiplicity,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkMultiplicityBoundModality(DefiniteMultiplicity definiteMultiplicity) {
        Expression expression = definiteMultiplicity.getExpression();
        if (expression == null) {
            return;
        }
        checkModalityAtMost(
            expression,
            Modality.COMPILE_TIME,
            "Multiplicity bound must be evaluable at compile time"
        );
    }

    @Check
    public void checkVariableInitializerModality(VariableDeclaration variableDeclaration) {
        if (OxstsUtils.isLocalVariable(variableDeclaration) || OxstsUtils.isLoopVariable(variableDeclaration)) {
            return;
        }
        var expression = variableDeclaration.getExpression();
        if (expression == null) {
            return;
        }
        checkModalityAtMost(
            expression,
            Modality.COMPILE_TIME,
            "Variable initializer must be evaluable at compile time"
        );
    }

    @Check
    public void checkInlineIfGuardModality(InlineIfOperation inlineIfOperation) {
        var guard = inlineIfOperation.getGuard();
        if (guard == null) {
            return;
        }
        checkModalityAtMost(
            guard,
            Modality.COMPILE_TIME,
            "`inline if` guard must be evaluable at compile time"
        );
    }

    @Check
    public void checkInlineForRangeModality(InlineForOperation inlineForOperation) {
        var range = inlineForOperation.getRangeExpression();
        if (range == null) {
            return;
        }
        checkModalityAtMost(
            range,
            Modality.COMPILE_TIME,
            "`inline for` range expression must be evaluable at compile time"
        );
    }

    @Check
    public void checkAssignmentTypes(AssignmentOperation assignmentOperation) {
        var lhs = assignmentOperation.getReference();
        var rhs = assignmentOperation.getExpression();
        if (lhs == null || rhs == null) {
            return;
        }
        var lhsType = evaluateTypeSafely(lhs);
        var rhsType = evaluateTypeSafely(rhs);
        if (lhsType == null || rhsType == null) {
            return;
        }
        if (!typeCompatibility.isAssignable(lhsType, rhsType, assignmentOperation)) {
            acceptError(
                "Cannot assign value of type " + formatType(rhsType)
                    + " to target of type " + formatType(lhsType) + ".",
                assignmentOperation,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkAssumptionType(AssumptionOperation assumptionOperation) {
        requireBoolean(
            assumptionOperation.getExpression(),
            "`assume` expression must be boolean"
        );
    }

    @Check
    public void checkIfGuardType(IfOperation ifOperation) {
        requireBoolean(
            ifOperation.getGuard(),
            "`if` guard must be boolean"
        );
    }

    @Check
    public void checkInlineIfGuardType(InlineIfOperation inlineIfOperation) {
        requireBoolean(
            inlineIfOperation.getGuard(),
            "`inline if` guard must be boolean"
        );
    }

    @Check
    public void checkNegationOperandType(NegationOperator negationOperator) {
        requireBoolean(
            negationOperator.getBody(),
            "negation operand must be boolean"
        );
    }

    @Check
    public void checkArithmeticUnaryOperandType(ArithmeticUnaryOperator operator) {
        requireNumeric(
            operator.getBody(),
            "unary arithmetic operand must be numeric"
        );
    }

    @Check
    public void checkArithmeticBinaryOperandTypes(ArithmeticBinaryOperator operator) {
        requireNumeric(operator.getLeft(), "left arithmetic operand must be numeric");
        requireNumeric(operator.getRight(), "right arithmetic operand must be numeric");
    }

    @Check
    public void checkBooleanOperandTypes(BooleanOperator operator) {
        requireBoolean(operator.getLeft(), "left boolean operand must be boolean");
        requireBoolean(operator.getRight(), "right boolean operand must be boolean");
    }

    @Check
    public void checkComparisonOperandTypes(ComparisonOperator operator) {
        var left = operator.getLeft();
        var right = operator.getRight();
        if (left == null || right == null) {
            return;
        }
        var leftType = evaluateTypeSafely(left);
        var rightType = evaluateTypeSafely(right);
        if (leftType == null || rightType == null) {
            return;
        }
        if (!typeCompatibility.isComparable(leftType, rightType, operator)) {
            acceptError(
                "Cannot compare values of type " + formatType(leftType)
                    + " and " + formatType(rightType) + ".",
                operator,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkIndexingTypes(IndexingSuffixExpression expression) {
        requireNumeric(
            expression.getIndex(),
            "array index must be numeric"
        );
        var primary = expression.getPrimary();
        if (primary == null) {
            return;
        }
        var primaryType = evaluateTypeSafely(primary);
        if (primaryType == null || primaryType.getRange() == null) {
            return;
        }
        var upper = primaryType.getRange().getUpperBound();
        if (upper != hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation.INFINITY
            && upper <= 1
            && primaryType.getRange().getLowerBound() <= 1) {
            acceptError(
                "Cannot index a non-array value (multiplicity " + primaryType.getRange().getLowerBound() + ".." + upper + ").",
                expression,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkArrayLiteralHomogeneous(ArrayLiteral arrayLiteral) {
        var values = arrayLiteral.getValues();
        if (values.size() < 2) {
            return;
        }
        var firstType = evaluateTypeSafely(values.getFirst());
        if (firstType == null) {
            return;
        }
        for (int i = 1; i < values.size(); i++) {
            var elementType = evaluateTypeSafely(values.get(i));
            if (elementType == null) {
                continue;
            }
            if (!typeCompatibility.isComparable(firstType, elementType, arrayLiteral)) {
                acceptError(
                    "Array literal elements must share a common type; element " + i
                        + " has type " + formatType(elementType) + " but the first has type "
                        + formatType(firstType) + ".",
                    arrayLiteral,
                    TYPE_MISMATCH
                );
                return;
            }
        }
    }

    @Check
    public void checkVariableMultiplicityBounded(VariableDeclaration variableDeclaration) {
        if (OxstsUtils.isLoopVariable(variableDeclaration)) {
            return;
        }
        var typeSpec = variableDeclaration.getTypeSpecification();
        if (typeSpec == null) {
            return;
        }
        rejectIfUnboundedMultiplicity(typeSpec.getMultiplicity(), variableDeclaration);
    }

    private void rejectIfUnboundedMultiplicity(Multiplicity multiplicity, EObject host) {
        if (multiplicity instanceof UnboundedMultiplicity unboundedMultiplicity) {
            acceptError(
                "Array size must have a compile-time upper bound (got unbounded multiplicity `[]`).",
                host,
                TYPE_MISMATCH
            );
        } else if (multiplicity instanceof DefiniteMultiplicity definite && definite.getExpression() != null && containsInfinity(definite.getExpression())) {
            acceptError(
                "Array size must have a compile-time upper bound (got `*` / infinity in multiplicity).",
                host,
                TYPE_MISMATCH
            );
        }
    }

    private boolean containsInfinity(EObject expression) {
        if (expression instanceof LiteralInfinity) {
            return true;
        }
        for (var child : expression.eContents()) {
            if (containsInfinity(child)) {
                return true;
            }
        }
        return false;
    }

    @Check
    public void checkCastCompatibility(CastExpression castExpression) {
        if (castExpression.getBody() == null || castExpression.getTypespecification() == null) {
            return;
        }
        var bodyType = evaluateTypeSafely(castExpression.getBody());
        if (bodyType == null) {
            return;
        }
        var castType = typeEvaluatorProvider.getEvaluator(castExpression).fromTypeSpecification(castExpression.getTypespecification());
        if (castType == null || castType.getDomain() == null) {
            return;
        }

        if (!typeCompatibility.isAssignable(bodyType, castType, castExpression)) {
            acceptError(
                "Cast from " + formatType(bodyType) + " to " + formatType(castType)
                    + " is not a valid narrowing cast (widening or unrelated types are rejected).",
                castExpression,
                INVALID_CAST
            );
        }
    }

    @Check
    public void checkIfThenElseGuardType(IfThenElse expression) {
        requireBoolean(
            expression.getGuard(),
            "if-then-else guard must be boolean"
        );
    }

    @Check
    public void checkIfThenElseBranchCompatibility(IfThenElse expression) {
        if (expression.getThen() == null || expression.getElse() == null) {
            return;
        }
        var thenType = evaluateTypeSafely(expression.getThen());
        var elseType = evaluateTypeSafely(expression.getElse());
        if (thenType == null || elseType == null) {
            return;
        }
        if (!typeCompatibility.isComparable(thenType, elseType, expression)) {
            acceptError(
                "Branches of if-then-else expression have incompatible types: "
                    + formatType(thenType) + " and " + formatType(elseType) + ".",
                expression,
                TYPE_MISMATCH
            );
        }
    }

    @Check
    public void checkAGBodyType(AG expression) {
        requireBoolean(
            expression.getBody(),
            "`AG` body must be boolean"
        );
    }

    @Check
    public void checkEFBodyType(EF expression) {
        requireBoolean(
            expression.getBody(),
            "`EF` body must be boolean"
        );
    }

    private void requireBoolean(Expression expression, String messagePrefix) {
        if (expression == null) {
            return;
        }
        var type = evaluateTypeSafely(expression);
        if (type == null) {
            return;
        }
        if (!typeCompatibility.isBoolean(type, expression)) {
            acceptError(
                messagePrefix + " (got " + formatType(type) + ").",
                expression,
                TYPE_MISMATCH
            );
        }
    }

    private void requireNumeric(Expression expression, String messagePrefix) {
        if (expression == null) {
            return;
        }
        var type = evaluateTypeSafely(expression);
        if (type == null) {
            return;
        }
        if (!typeCompatibility.isNumeric(type, expression)) {
            acceptError(
                messagePrefix + " (got " + formatType(type) + ").",
                expression,
                TYPE_MISMATCH
            );
        }
    }

    private TypeEvaluation evaluateTypeSafely(Expression expression) {
        try {
            return typeEvaluatorProvider.evaluate(expression);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String formatType(TypeEvaluation type) {
        if (type == null) {
            return "<unknown>";
        }
        var domain = type.getDomain();
        var name = domain != null ? domain.getName() : "<invalid>";
        return name != null ? name : "<anonymous>";
    }

    private void checkModalityAtMost(Expression expression, Modality upperBound, String messagePrefix) {
        Modality actual;
        try {
            actual = modalityEvaluatorProvider.evaluate(expression);
        } catch (RuntimeException e) {
            return;
        }
        if (!actual.isAtMost(upperBound)) {
            acceptError(
                messagePrefix + " (got modality " + actual + ").",
                expression,
                EXPRESSION_MODALITY_TOO_HIGH
            );
        }
    }

    // FIXME: this should be removed along with solving the problem in semantics#OxstsInflator#pullDownVariables()
    @Check
    public void checkNoImplicitlyTypeVariables(VariableDeclaration variableDeclaration) {
        if (OxstsUtils.isLoopVariable(variableDeclaration)) {
            return;
        }

        if (variableDeclaration.getTypeSpecification() == null) {
            acceptError("Variables may not have implicit types!", variableDeclaration, VARIABLE_WITH_IMPLICIT_TYPE);
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
