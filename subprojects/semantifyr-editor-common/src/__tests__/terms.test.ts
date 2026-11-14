/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import { type ResultStatus, verdictOfStatus } from "../terms/terms.ts";
import { VerificationStatus } from "../wire.ts";

describe("the terms' verdicts", () => {
    it("map every status of a verification's result to one of the four", () => {
        const statuses = Object.values(VerificationStatus).filter((status): status is ResultStatus => {
            return status !== VerificationStatus.Cancelled;
        });
        for (const status of statuses) {
            expect(() => {
                return verdictOfStatus(status);
            }, status).not.toThrow();
        }
    });
});
