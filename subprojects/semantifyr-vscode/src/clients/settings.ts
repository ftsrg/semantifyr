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
        portfolio: config.get<string>("portfolio", "smart"),
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

export interface VerificationSettings {
    autoValidate: boolean;
    validationPortfolio: string | undefined;
}

export function readVerificationSettings(): VerificationSettings {
    const config = workspace.getConfiguration("semantifyr");
    const validationPortfolio = config.get<string>("verification.validationPortfolio", "");
    return {
        autoValidate: config.get<boolean>("verification.autoValidate", true),
        validationPortfolio: validationPortfolio === "" ? undefined : validationPortfolio,
    };
}

export function readSemantifyrPayload(): { semantifyr: ServerSettings } {
    return { semantifyr: readSemantifyrSettings() };
}
