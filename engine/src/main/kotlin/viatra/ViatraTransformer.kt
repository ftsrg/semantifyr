package hu.bme.mit.gamma.oxsts.engine.viatra

import hu.bme.mit.gamma.oxsts.model.oxsts.Pattern
import hu.bme.mit.gamma.oxsts.model.oxsts.PatternConstraint
import org.eclipse.viatra.query.patternlanguage.emf.util.PatternParserBuilder
import org.eclipse.viatra.query.runtime.api.GenericPatternMatcher
import org.eclipse.viatra.query.runtime.api.IQuerySpecification
import org.eclipse.xtext.EcoreUtil2
import java.util.*

object ViatraTransformer {

    val transformedPatterns = mutableMapOf<Pattern, IQuerySpecification<GenericPatternMatcher>>()

    fun transform(pattern: Pattern) = transformedPatterns.computeIfAbsent(pattern) {
        val vqlparser = PatternParserBuilder().build()

        val referencedPatterns = allReferencedPatterns(pattern)

        val vql = buildString {
            appendLine(PatternSerializer.helperPatterns)

            for (pattern in referencedPatterns) {
                appendLine()
                appendLine(PatternSerializer.serialize(pattern))
            }
        }

        val results = vqlparser.parse(vql)

        if (results.hasError()) {
            error(results.errors)
        }

        results.querySpecifications.first {
            it.simpleName == pattern.fullyQualifiedName
        } as IQuerySpecification<GenericPatternMatcher>
    }

    fun allReferencedPatterns(pattern: Pattern): Set<Pattern> {
        val patterns = mutableSetOf<Pattern>()

        val patternQueue = LinkedList<Pattern>()

        patternQueue += pattern

        while (patternQueue.any()) {
            val pattern = patternQueue.removeFirst()
            if (patterns.add(pattern)) {
                val patternConstraints = EcoreUtil2.getAllContentsOfType(pattern, PatternConstraint::class.java)
                patternQueue += patternConstraints.map { it.pattern }
            }
        }

        return patterns
    }

//        val parseResults = vqlparser.parse("""
//            import "http://www.bme.hu/mit/2023/oxsts"
//
//            pattern instances(e) {
//                Instance(e);
//            }
//        """.trimIndent())
//
//        val engine = ViatraQueryEngine.on(EMFScope(target))
//
//        println(parseResults)
//
//        val matcher = engine.getMatcher(parseResults.querySpecifications.first())
//        matcher.streamAllMatches().forEach {
//            println(it)
//        }

}
