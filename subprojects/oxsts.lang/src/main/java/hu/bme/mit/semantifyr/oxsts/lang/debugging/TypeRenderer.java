/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.debugging;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import org.eclipse.emf.ecore.EObject;

import java.util.ArrayList;

public class TypeRenderer {

    private static TypeRenderer instance;

    @Inject
    protected ExpressionSerializer expressionSerializer;

    static {
        var setup = new OxstsStandaloneSetup();
        var injector = setup.createInjectorAndDoEMFRegistration();
        instance = injector.getInstance(TypeRenderer.class);
    }

    public String serialize(EObject element) {
        if (element instanceof Expression expression) {
            return expressionSerializer.serialize(expression);
        }
        if (element instanceof NamedElement namedElement) {
            return NamingUtil.getName(namedElement);
        }
        if (element instanceof Instance instance) {
            return serialize(instance);
        }

        return element.toString();
    }

    public static String render(EObject element) {
        return instance.serialize(element);
    }

    private static String serialize(Instance element) {
        var list = new ArrayList<DomainDeclaration>();
        list.add(element.getDomain());

        while (element.getParent() != null && element.getParent().getDomain() != null) {
            list.add(element.getParent().getDomain());
            element = element.getParent();
        }

        var names = list.reversed().stream().map(NamingUtil::getName).toList();

        return String.join(".", names);
    }

}
