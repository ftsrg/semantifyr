import { workspace } from "vscode";

export type SemantifyrSettings = {
    portfolio: string;
    timeoutSeconds: number;
    maxConcurrency: number;
    "theta.executor": string;
    "theta.dockerImage": string;
    "artifacts.location": string;
    "artifacts.directory": string;
    "artifacts.preset": string;
    "optimization.level": string;
};

export function readSemantifyrSettings(): SemantifyrSettings {
    const config = workspace.getConfiguration("semantifyr");
    return {
        portfolio: config.get<string>("portfolio", "theta-full"),
        timeoutSeconds: config.get<number>("timeoutSeconds", 300),
        maxConcurrency: config.get<number>("maxConcurrency", 0),
        "theta.executor": config.get<string>("theta.executor", "auto"),
        "theta.dockerImage": config.get<string>("theta.dockerImage", "ftsrg/theta-xsts-cli:latest"),
        "artifacts.location": config.get<string>("artifacts.location", "temporary"),
        "artifacts.directory": config.get<string>("artifacts.directory", ""),
        "artifacts.preset": config.get<string>("artifacts.preset", "all"),
        "optimization.level": config.get<string>("optimization.level", "all"),
    };
}

export function readSemantifyrPayload(): { semantifyr: SemantifyrSettings } {
    return { semantifyr: readSemantifyrSettings() };
}
