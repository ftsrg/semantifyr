/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import assert from "node:assert/strict";
import { commands, extensions, Uri, workspace } from "vscode";
import type { VerificationCaseResult, VerificationCaseSpecification } from "@semantifyr/editor-common";

export async function run() {
    const extension = extensions.getExtension("ftsrg.semantifyr");
    assert.ok(extension, "the extension is not installed");
    await extension.activate();

    const registered = await commands.getCommands(true);
    for (const command of ["semantifyr.gamma.compile", "semantifyr.witness.show", "oxsts.case.discover", "oxsts.case.verify", "gamma.case.discover", "gamma.case.verify"]) {
        assert.ok(registered.includes(command), `${command} is not registered`);
    }

    const folder = workspace.workspaceFolders?.[0];
    assert.ok(folder, "no workspace folder is open");
    const model = Uri.joinPath(folder.uri, "smoke.oxsts").toString();
    const cases = await commands.executeCommand<VerificationCaseSpecification[]>("oxsts.case.discover", model);
    const [smokeCase] = cases;
    assert.ok(smokeCase, "the server discovered no case");
    assert.deepEqual(cases.map((verificationCase) => verificationCase.label), ["semantifyr::verification::smoke::CounterReachesThree"]);

    const result = await commands.executeCommand<VerificationCaseResult>("oxsts.case.verify", { uri: model, range: smokeCase.location.range, portfolio: "theta-cegar-expl" });
    assert.equal(result.status, "passed", result.message ?? "no message");
    const witnessUri = result.trace?.witnessUri;
    assert.ok(witnessUri, "the result carries no witness");
    const witness = await workspace.openTextDocument(Uri.parse(witnessUri));
    assert.match(witness.getText(), /CounterReachesThree/);
}
