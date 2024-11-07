package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.BaseType;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.DefaultDeclarativeQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;

public class OxstsQualifiedNameProvider extends DefaultDeclarativeQualifiedNameProvider {

    protected QualifiedName qualifiedName(Transition transition) {
        var parentsName = getFullyQualifiedName(transition.eContainer());
        var name = elementName(transition);

        return parentsName.append(name);
    }

    protected String elementName(Transition transition) {
        var baseType = EcoreUtil2.getContainerOfType(transition, BaseType.class);

        if (baseType.getMainTransition().contains(transition)) {
            return "main";
        } else if (baseType.getInitTransition().contains(transition)) {
            return "init";
        } else if (baseType.getHavocTransition().contains(transition)) {
            return "havoc";
        }

        return transition.getName();
    }

}
