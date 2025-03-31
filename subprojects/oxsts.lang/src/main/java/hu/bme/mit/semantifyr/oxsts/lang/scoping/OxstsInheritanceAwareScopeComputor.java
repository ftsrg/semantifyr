/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Package;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.xtext.util.IResourceScopeCache;
import org.eclipse.xtext.util.Tuples;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class OxstsInheritanceAwareScopeComputor {

    private static final String BASE_KEY = "hu.bme.mit.semantifyr.oxsts.lang.scoping.OxstsStructureComputor";
    private static final String INHERITED_TRANSITIONS_KEY = BASE_KEY + ".INHERITED_TRANSITIONS";
    private static final String INHERITED_FEATURES_KEY = BASE_KEY + ".INHERITED_FEATURES";
    private static final String INHERITED_VARIABLES_KEY = BASE_KEY + ".INHERITED_VARIABLES";
    private static final String INHERITED_PROPERTIES_KEY = BASE_KEY + ".INHERITED_PROPERTIES";
    private static final String INHERITED_ELEMENTS_KEY = BASE_KEY + ".INHERITED_ELEMENTS";
    private static final String ACCESSIBLE_ELEMENTS_KEY = BASE_KEY + ".ACCESSIBLE_ELEMENTS";

    @Inject
    private IResourceScopeCache resourceScopeCache;

    public Collection<Transition> getAllInheritedTransitions(Element scope) {
        return getFromCacheComputing(scope, INHERITED_TRANSITIONS_KEY, this::computeAllInheritedTransitions);
    }

    public Collection<Feature> getAllInheritedFeatures(Element scope) {
        return getFromCacheComputing(scope, INHERITED_FEATURES_KEY, this::computeAllInheritedFeatures);
    }

    public Collection<Variable> getAllInheritedVariables(Element scope) {
        return getFromCacheComputing(scope, INHERITED_VARIABLES_KEY, this::computeAllInheritedVariables);
    }

    public Collection<Property> getAllInheritedProperties(Element scope) {
        return getFromCacheComputing(scope, INHERITED_PROPERTIES_KEY, this::computeAllInheritedProperties);
    }

    public Collection<Element> getAllInheritedElements(Element scope) {
        return getFromCacheComputing(scope, INHERITED_ELEMENTS_KEY, this::computeAllInheritedElements);
    }

    protected Collection<? extends Element> getAccessibleElements(Namespace namespace) {
        return resourceScopeCache.get(Tuples.create(ACCESSIBLE_ELEMENTS_KEY, namespace), namespace.eResource(), () -> computeAccessibleElements(namespace));
    }

    protected Collection<? extends Element> computeAccessibleElements(Namespace namespace) {
        return switch (namespace) {
            case Transition transition -> transition.getArguments();
            case Feature feature -> getAllInheritedElements(feature);
            case Type type -> getAllInheritedElements(type);
            case Package _package -> OxstsUtils.getAllElementsTransitive(_package);
            default -> List.of();
        };
    }

    protected Collection<Transition> computeAllInheritedTransitions(BaseType baseType) {
        var transitions = new ArrayList<Transition>();

        transitions.addAll(baseType.getTransitions());
        transitions.addAll(baseType.getInitTransition());
        transitions.addAll(baseType.getMainTransition());
        transitions.addAll(baseType.getHavocTransition());

        if (baseType instanceof Type type) {
            if (type.getSupertype() != null) {
                transitions.addAll(getAllInheritedTransitions(type.getSupertype()));
            }
        }

        if (baseType instanceof Feature feature) {
            transitions.addAll(getAllInheritedTransitions(feature.getTyping()));
        }

        return transitions;
    }

    protected Collection<Feature> computeAllInheritedFeatures(BaseType baseType) {
        var features = new ArrayList<>(baseType.getFeatures());

        if (baseType instanceof Type type) {
            if (type.getSupertype() != null) {
                features.addAll(getAllInheritedFeatures(type.getSupertype()));
            }
        }

        if (baseType instanceof Feature feature) {
            features.addAll(getAllInheritedFeatures(feature.getTyping()));
        }

        return features;
    }

    protected Collection<Variable> computeAllInheritedVariables(BaseType baseType) {
        var variables = new ArrayList<Variable>();

        if (baseType instanceof Type type) {
            variables.addAll(type.getVariables());

            if (type.getSupertype() != null) {
                variables.addAll(getAllInheritedVariables(type.getSupertype()));
            }
        }

        if (baseType instanceof Feature feature) {
            variables.addAll(getAllInheritedVariables(feature.getTyping()));
        }

        return variables;
    }

    protected Collection<Property> computeAllInheritedProperties(BaseType baseType) {
        var properties = new ArrayList<Property>();

        if (baseType instanceof Type type) {
            properties.addAll(type.getProperties());

            if (type.getSupertype() != null) {
                properties.addAll(getAllInheritedProperties(type.getSupertype()));
            }
        }

        if (baseType instanceof Feature feature) {
            properties.addAll(getAllInheritedProperties(feature.getTyping()));
        }

        return properties;
    }

    protected Collection<Element> computeAllInheritedElements(BaseType baseType) {
        var elements = new ArrayList<Element>();

        elements.addAll(getAllInheritedTransitions(baseType));
        elements.addAll(getAllInheritedFeatures(baseType));
        elements.addAll(getAllInheritedProperties(baseType));
        elements.addAll(getAllInheritedVariables(baseType));

        return elements;
    }

    protected <T extends Element> Collection<T> getFromCacheComputing(Element scope, String cacheKey, Function<BaseType, Collection<T>> computor) {
        if (scope instanceof BaseType baseType) {
            return getFromCacheComputing(baseType, cacheKey, computor);
        } else if (scope instanceof Feature feature) {
            return getFromCacheComputing(feature, cacheKey, computor);
        } else if (scope instanceof Argument argument) {
            return getFromCacheComputing(argument, cacheKey, computor);
        } else if (scope instanceof Variable variable) {
            return getFromCacheComputing(variable, cacheKey, computor);
        } else if (scope instanceof ReferenceTyping typing) {
            return getFromCacheComputing(typing, cacheKey, computor);
        }

        return List.of();
    }

    protected <T extends Element> Collection<T> getFromCacheComputing(Feature feature, String cacheKey, Function<BaseType, Collection<T>> computor) {
        if (feature.getTyping() instanceof ReferenceTyping referenceTyping) {
            return getFromCacheComputing(referenceTyping, cacheKey, computor);
        }

        return List.of();
    }

    protected <T extends Element> Collection<T> getFromCacheComputing(Argument argument, String cacheKey, Function<BaseType, Collection<T>> computor) {
        if (argument.getTyping() instanceof ReferenceTyping referenceTyping) {
            return getFromCacheComputing(referenceTyping, cacheKey, computor);
        }

        return List.of();
    }

    protected <T extends Element> Collection<T> getFromCacheComputing(Variable variable, String cacheKey, Function<BaseType, Collection<T>> computor) {
        if (variable.getTyping() instanceof ReferenceTyping referenceTyping) {
            return getFromCacheComputing(referenceTyping, cacheKey, computor);
        }

        return List.of();
    }

    protected <T extends Element> Collection<T> getFromCacheComputing(ReferenceTyping typing, String cacheKey, Function<BaseType, Collection<T>> computor) {
        return getFromCacheComputing(OxstsUtils.getReferencedType(typing), cacheKey, computor);
    }

    protected <T extends Element> Collection<T> getFromCacheComputing(BaseType baseType, String cacheKey, Function<BaseType, Collection<T>> computor) {
        return resourceScopeCache.get(Tuples.create(cacheKey, baseType), baseType.eResource(), () -> computor.apply(baseType));
    }

}
