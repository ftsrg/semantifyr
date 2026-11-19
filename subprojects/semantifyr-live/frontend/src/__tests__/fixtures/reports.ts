/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ModelSize, OptimizationStats, ReportKind, ReportTrace, ReportVerdict, SoundnessDirection, VerificationReport } from '@semantifyr/editor-common'

const NO_OPTIMIZATION: OptimizationStats = { duration: 'PT0S', passes: [], analyses: [] }

const NO_SIZE: ModelSize = { variables: 0, operations: 0, expressions: 0 }

export interface ReportSeed {
  verdict: ReportVerdict
  backendId: string
  seconds: number
  soundness?: SoundnessDirection
  kind?: ReportKind
  trace?: ReportTrace | null
}

export function reportOf(seed: ReportSeed): VerificationReport {
  return {
    version: 2,
    kind: seed.kind ?? 'run',
    verificationCase: 'example::Case',
    portfolioId: 'smart',
    optimization: { enabled: [] },
    timeout: 'PT5M',
    result: {
      metadata: { backendId: seed.backendId, startedAt: '2026-11-16T10:00:00Z' },
      verdict: seed.verdict,
      soundness: seed.soundness ?? 'Exact',
      isTrusted: true,
      metrics: {
        totalDuration: `PT${seed.seconds}S`,
        backend: { preparationDuration: 'PT0S', verificationDuration: `PT${seed.seconds}S`, backAnnotationDuration: 'PT0S' },
        verifier: {
          compilationDuration: 'PT0S',
          compilation: {
            createDuration: 'PT0S',
            instantiationDuration: 'PT0S',
            inliningDuration: 'PT0S',
            flatteningDuration: 'PT0S',
            inliningOptimization: NO_OPTIMIZATION,
            flatteningOptimization: NO_OPTIMIZATION,
            inputSize: NO_SIZE,
            inlinedSize: NO_SIZE,
            flattenedSize: NO_SIZE,
          },
          backAnnotationDuration: 'PT0S',
          portfolioDuration: `PT${seed.seconds}S`,
        },
      },
      message: null,
    },
    trace: seed.trace ?? null,
  }
}
