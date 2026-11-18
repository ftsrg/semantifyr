/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

abstract class Benchmark<R> {
    open fun setUp() {
    }

    abstract fun measure(): BenchmarkRun<R>

    open fun analyze(run: BenchmarkRun<R>) {
    }
}
