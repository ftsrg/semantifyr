/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.verification

import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport
import hu.bme.mit.semantifyr.live.backend.testing.StagedTestSupport.stagedModel
import hu.bme.mit.semantifyr.live.backend.testing.acceptAnyKnownStatus
import hu.bme.mit.semantifyr.live.backend.testing.pickByLabel
import hu.bme.mit.semantifyr.live.backend.testing.pickByLabelSuffix
import hu.bme.mit.semantifyr.live.backend.testing.verifyEndToEnd
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class LiveSmokeTest {

    @Test
    suspend fun `oxsts verification case passes end-to-end`(@TempDir tmp: Path) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = "oxsts",
            modelSource = stagedModel(StagedTestSupport.oxstsTestModelsDirectory, "semantifyr.live.oxstsTestModels", "smoke.oxsts"),
            pickCase = {
                it.pickByLabelSuffix("CounterReachesThree")
            },
            assertVerdict = {
                assertThat(it.status()).isEqualTo("passed")
            },
        )
    }

    @Test
    suspend fun `gamma verification case passes end-to-end`(@TempDir tmp: Path) {
        verifyEndToEnd(
            tmp = tmp,
            flavor = "gamma",
            languageId = "gamma",
            documentUri = "file:///workspace/snippet.gamma",
            modelSource = stagedModel(StagedTestSupport.gammaTestModelsDirectory, "semantifyr.live.gammaTestModels", "Simple.gamma"),
            discoverCommand = "gamma.case.discover",
            verifyCommand = "gamma.case.verify",
            pickCase = {
                val idleReachable = it.pickByLabel("LeaderStatechartIdleReachable")
                assertThat(idleReachable.id()).isEqualTo("Simple.LeaderStatechartIdleReachable")
                idleReachable
            },
            assertVerdict = {
                acceptAnyKnownStatus("LeaderStatechartIdleReachable", it)
                assertThat(it.status()).isIn("passed", "not_supported")
            },
            verifyTimeout = 2.minutes,
        )
    }
}
