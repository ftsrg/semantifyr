/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.theta

import hu.bme.mit.semantifyr.cex.lang.CexStandaloneSetup
import hu.bme.mit.semantifyr.cex.lang.cex.Cex
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.error
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.info
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.warn
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import java.io.File

fun prepareCex() {
    CexStandaloneSetup.doSetup()
}

class CexReader {
    val logger by loggerFactory()

    val resourceSet = ResourceSetImpl()

    init {
        resourceSet.loadOptions[XtextResource.OPTION_ENCODING] = "UTF-8"
    }

    fun validateResource(resource: Resource) {
        if (resource.errors.any()) {
            logger.error { "Errors found in file (${resource.uri.toFileString()})" }

            for (error in resource.errors) {
                logger.error(error.message)
            }

            error("Errors found in file (${resource.uri.toFileString()})}")
        }
        if (resource.warnings.any()) {
            logger.warn { "Warnings found in file (${resource.uri.toFileString()})" }

            for (warning in resource.warnings) {
                logger.warn(warning.message)
            }
        }
        val validator = (resource as XtextResource).resourceServiceProvider.resourceValidator
        val issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl)
        if (issues.any()) {
            logger.info { "Issues found in file (${resource.uri.toFileString()})" }

            for (issue in issues) {
                when (issue.severity) {
                    Severity.INFO -> {
                        logger.info { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }
                    Severity.WARNING -> {
                        logger.warn { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }
                    Severity.ERROR -> {
                        logger.error { "${issue.uriToProblem.toFileString()}[${issue.lineNumber}:${issue.column}] ${issue.message}" }
                    }
                    else -> { }
                }
            }
        }
    }

    fun readCexFile(file: File): Cex {
        val resource = resourceSet.getResource(URI.createFileURI(file.path), true)
        resource.load(emptyMap<Any, Any>())
        validateResource(resource)

        return resource.contents.single() as Cex
    }
}
