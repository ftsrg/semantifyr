/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.wrapper

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

@InjectWithOxsts
class ReferenceWitnessTest {

    @Inject
    private lateinit var loader: SemantifyrLoader

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `the crossroads reference witness reparses against the compiled crossroads model`() {
        val workspace = temporaryDirectory.resolve("workspace").createDirectories()
        val model = MODELS.resolve("examples").resolve(MODEL).copyTo(workspace.resolve(MODEL))
        MODELS.resolve("examples").resolve(VERIFICATION).copyTo(workspace.resolve(VERIFICATION))
        val compiled = SysMLv2Compiler(temporaryDirectory.resolve("cache")).compile(model, temporaryDirectory.resolve("crossroads.oxsts"))

        val context = loader.startContext()
            .loadLibraries(MODELS.resolve("libraries").resolve("default"))
            .loadModel(compiled)
            .loadModel(MODELS.resolve("etc").resolve(WITNESS))
            .buildAndResolve()

        val witness = context.modelResources.last().contents.single() as InlinedOxsts
        assertThat(witness.isWitness).isTrue
        assertThat(witness.classDeclaration.name).isEqualTo("TrafficLightABehaviorNormalGreenUnreachableViolated")
    }

    companion object {
        private val MODELS = Path("..", "models")
        private const val MODEL = "crossroads.sysml"
        private const val VERIFICATION = "Verification.sysml"
        private const val WITNESS = "crossroads-green.witness.oxsts"
    }
}
