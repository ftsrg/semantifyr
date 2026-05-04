/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.discovery

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltInLibraryUtils
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralString
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.verification.VerificationCase
import kotlin.streams.asSequence

/**
 * Discovers [VerificationCase]s in a loaded [SemantifyrModelContext].
 */
class VerificationCaseDiscoverer @Inject constructor(
    private val builtInLibraryUtils: BuiltInLibraryUtils,
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
) {

    fun discover(context: SemantifyrModelContext): List<VerificationCase> {
        val packages = context.modelResources.asSequence().flatMap {
            it.contents.asSequence()
        }.filterIsInstance<OxstsModelPackage>()

        return packages.flatMap {
            builtInLibraryUtils.streamTestCases(it).asSequence()
        }.map {
            val qualifiedName = oxstsQualifiedNameProvider.getFullyQualifiedNameString(it)
            VerificationCase(
                classDeclaration = it,
                qualifiedName = qualifiedName,
                tags = tagsOf(it),
            )
        }.toList()
    }

    fun discover(context: SemantifyrModelContext, filter: CaseFilter): List<VerificationCase> {
        return discover(context).filter(filter::matches)
    }

    fun findByQualifiedNameOrNull(context: SemantifyrModelContext, qualifiedName: String): VerificationCase? {
        return discover(context).firstOrNull {
            it.qualifiedName == qualifiedName
        }
    }

    fun findByQualifiedName(context: SemantifyrModelContext, qualifiedName: String): VerificationCase {
        val cases = discover(context)
        return cases.firstOrNull {
            it.qualifiedName == qualifiedName
        } ?: error("No verification case named '$qualifiedName' (known: ${cases.joinToString { it.qualifiedName }})")
    }

    private fun tagsOf(classDeclaration: ClassDeclaration): Set<String> {
        val tagAnnotation = builtinSymbolResolver.tagAnnotation(classDeclaration) ?: return emptySet()
        val categoryParameter = builtinSymbolResolver.tagAnnotationCategory(classDeclaration) ?: return emptySet()
        return OxstsUtils.getAnnotations(classDeclaration, tagAnnotation).asSequence().map {
            OxstsUtils.getAnnotationValue(it, categoryParameter)
        }.filterIsInstance<LiteralString>().map {
            it.value
        }.toSet()
    }
}
