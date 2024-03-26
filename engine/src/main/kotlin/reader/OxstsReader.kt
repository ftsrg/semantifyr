package hu.bme.mit.gamma.oxsts.engine.reader

import hu.bme.mit.gamma.oxsts.model.oxsts.OxstsPackage
import hu.bme.mit.gamma.oxsts.model.oxsts.Package
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.resource.XtextResource
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import java.io.File

fun File.walkFiles() = walkTopDown().filter { it.isFile }

fun prepareOxsts() {
    hu.bme.mit.gamma.oxsts.lang.OxstsStandaloneSetup.doSetup()
    OxstsPackage.eINSTANCE.name
}

class OxstsReader(
    val inputDirectory: String
) {
    private val resourceSet = ResourceSetImpl()

    val rootElements
        get() = resourceSet.resources.flatMap { it.contents }.map { it as Package }

    init {
        resourceSet.loadOptions[XtextResource.OPTION_ENCODING] = "UTF-8"
    }

    fun read() {
        val inputFile = File(inputDirectory)

        for (file in inputFile.walkFiles().filter { it.extension == "oxsts" }) {
            val resource = resourceSet.getResource(URI.createURI(file.path), true)
            resource.load(emptyMap<Any, Any>())
            EcoreUtil2.resolveAll(resource)
            if (resource.errors.any()) {
                println(resource.errors)
                error("Errors found in the model: {${resource.errors.joinToString(",\n")}}")
            }
            if (resource.warnings.any()) {
                println(resource.warnings)
            }
            val validator = (resource as XtextResource).resourceServiceProvider.resourceValidator
            val issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl)
            for (issue in issues) {
                println("${issue.severity} - ${issue.message}")
            }
        }

    }

}
