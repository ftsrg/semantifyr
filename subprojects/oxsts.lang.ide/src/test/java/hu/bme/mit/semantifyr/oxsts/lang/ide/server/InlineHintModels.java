/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

final class InlineHintModels {

    static final String WORKER = """
            package example::factory

            class Worker {
                var done: bool := false

                tran step() {
                    done := true
                }
            }
            """;

    private InlineHintModels() {}
}
