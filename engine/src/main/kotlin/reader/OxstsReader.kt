package hu.bme.mit.gamma.oxsts.engine.reader

import hu.bme.mit.gamma.oxsts.model.oxsts.OxstsPackage
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.xtext.resource.XtextResource
import java.io.File

fun File.walkFiles() = walkTopDown().filter { it.isFile }

class OxstsReader(
    val inputDirectory: String
) {
    private val extensions = listOf("oxsts")
    private val resourceSet = ResourceSetImpl()

    val rootElements
        get() = resourceSet.resources.flatMap { it.contents }

    init {
        hu.bme.mit.gamma.oxsts.lang.OxstsStandaloneSetup.doSetup()
        OxstsPackage.eINSTANCE.name

        resourceSet.loadOptions[XtextResource.OPTION_ENCODING] = "UTF-8"
    }

    fun read() {
        val inputFile = File(inputDirectory)

        for (file in inputFile.walkFiles()) {
            resourceSet.getResource(URI.createFileURI(file.path), true)
        }
    }
}
