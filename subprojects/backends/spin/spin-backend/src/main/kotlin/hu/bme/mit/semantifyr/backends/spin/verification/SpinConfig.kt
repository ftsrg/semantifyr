/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.verification

data class SpinConfig(
    val id: String,
    val extraArguments: List<String> = emptyList(),
) {
    companion object {
        val ExhaustiveDfs = SpinConfig(
            id = "exhaustive-dfs",
            extraArguments = listOf("-a"),
        )
        val ExhaustiveBfs = SpinConfig(
            id = "exhaustive-bfs",
            extraArguments = listOf("-bfs"),
        )
        val SafeDfs = SpinConfig(
            id = "safe-dfs",
            extraArguments = listOf("-DSAFETY", "-DSFH"),
        )
        val FastDfs = SpinConfig(
            id = "fast-dfs",
            extraArguments = listOf("-DSAFETY", "-DSFH", "-DNOFAIR", "-DNOBOUNDCHECK"),
        )
        val Collapse = SpinConfig(
            id = "collapse",
            extraArguments = listOf("-a", "-DCOLLAPSE"),
        )
        val BitstateHashing = SpinConfig(
            id = "bitstate",
            extraArguments = listOf("-a", "-DBITSTATE"),
        )
        val HashCompact = SpinConfig(
            id = "hash-compact",
            extraArguments = listOf("-a", "-DHC"),
        )
    }
}
