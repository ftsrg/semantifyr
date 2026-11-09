/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { workspace } from "vscode";
import type {
    ArtifactsLocation,
    ArtifactsPreset,
    OptimizationLevel,
    ServerSettings,
    ThetaExecutor,
} from "@semantifyr/editor-common";

export function readSemantifyrSettings(): ServerSettings {
    const config = workspace.getConfiguration("semantifyr");
    return {
        portfolio: config.get<string>("portfolio", "smart-full"),
        timeoutSeconds: config.get<number>("timeoutSeconds", 300),
        theta: {
            executor: config.get<ThetaExecutor>("theta.executor", "auto"),
            dockerImage: config.get<string>("theta.dockerImage", "ftsrg/theta-xsts-cli:8.6.0"),
        },
        artifacts: {
            location: config.get<ArtifactsLocation>("artifacts.location", "temporary"),
            preset: config.get<ArtifactsPreset>("artifacts.preset", "all"),
        },
        optimization: {
            level: config.get<OptimizationLevel>("optimization.level", "all"),
        },
    };
}

export function readSemantifyrPayload(): { semantifyr: ServerSettings } {
    return { semantifyr: readSemantifyrSettings() };
}
