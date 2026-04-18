/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.commands;

import java.util.List;

/**
 * Wire format for one verifier preset, returned by `oxsts.presets.list`.
 *
 * @param id         stable preset id (matches `BackendPreset.id`)
 * @param displayName human-readable label for picker UIs
 * @param description one-sentence summary
 * @param factoryId   underlying factory id (e.g. `"theta"`)
 * @param status      one of `"available" | "degraded" | "unavailable"`
 * @param reason      degradation/unavailability reason; `null` when status is `available`
 * @param hints       remediation hints (install commands, links, ...); empty when not unavailable
 */
public record PresetSpecification(
        String id,
        String displayName,
        String description,
        String factoryId,
        String status,
        String reason,
        List<String> hints
) {
}
