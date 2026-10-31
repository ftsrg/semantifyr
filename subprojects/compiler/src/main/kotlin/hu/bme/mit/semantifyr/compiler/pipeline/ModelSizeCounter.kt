/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.InlinedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.ModelSize
import hu.bme.mit.semantifyr.compiler.pipeline.utils.treeSequence
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource

class ModelSizeCounter @Inject constructor(
    private val domainMemberCollectionProvider: DomainMemberCollectionProvider,
) {

    fun count(resource: Resource): ModelSize {
        return count(resource.allContents)
    }

    fun count(root: EObject): ModelSize {
        return count(root.eAllContents())
    }

    // class variables are not pulled down yet, so count the ones flattening will create per instance
    fun count(context: InlinedCompilationContext): ModelSize {
        val variables = context.rootInstance.treeSequence().drop(1).sumOf { instance ->
            domainMemberCollectionProvider.getMemberCollection(instance.domain).declarations.filterIsInstance<VariableDeclaration>().distinct().size
        }
        return count(context.inlinedOxsts).copy(variables = variables)
    }

    private fun count(contents: Iterator<EObject>): ModelSize {
        var variables = 0
        var operations = 0
        var expressions = 0
        for (element in contents) {
            if (element is VariableDeclaration && element !is LocalVarDeclarationOperation) {
                variables++
            }
            if (element is Operation) {
                operations++
            }
            if (element is Expression) {
                expressions++
            }
        }
        return ModelSize(variables, operations, expressions)
    }

}
