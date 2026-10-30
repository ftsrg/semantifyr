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
        portfolio: config.get<string>("portfolio", "theta-full"),
        timeoutSeconds: config.get<number>("timeoutSeconds", 300),
        maxConcurrency: config.get<number>("maxConcurrency", 0),
        theta: {
            executor: config.get<ThetaExecutor>("theta.executor", "auto"),
            dockerImage: config.get<string>("theta.dockerImage", "ftsrg/theta-xsts-cli:latest"),
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
