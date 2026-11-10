/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'

import { propertyText } from '../trace/property'

// Goal: a failed case's step list closes on the property's text, cut from the case's definition in each language

const OXSTS = `
@VerificationCase
class Inherits : LevelCrossing {
}

@VerificationCase
class BarrierDownWhileTrainInCrossing : LevelCrossing {
    prop {
        return AG (!(trackA.state == TrackState::InCrossing) || barrier.state == BarrierState::Down)
    }
}
`

const GAMMA = `
verification case BarrierDownWhileTrainInCrossing {
    component system : LevelCrossing
    property {
        AG ((!isActive (system.trackA.Main.InCrossing)) || isActive (system.barrier.Main.Down))
    }
}
`

const SYSML = `
verification def BarrierDownWhileTrainInCrossingViolated {
    subject system : LevelCrossing;

    objective {
        verify requirement {
            require constraint {
                mustAlways(
                    not isStateActive(system.trackA.Behaviour.InCrossing)
                    or isStateActive(system.barrier.Behaviour.Down)
                )
            }
        }
    }
}
`

function at(line: number): { line: number, character: number } {
  return { line, character: 0 }
}

describe('propertyText', () => {
  it('cuts the OXSTS property without its return', () => {
    expect(propertyText('oxsts', OXSTS, at(6), null)).toBe('AG (!(trackA.state == TrackState::InCrossing) || barrier.state == BarrierState::Down)')
  })

  it('cuts the Gamma property', () => {
    expect(propertyText('gamma', GAMMA, at(1), null)).toBe('AG ((!isActive (system.trackA.Main.InCrossing)) || isActive (system.barrier.Main.Down))')
  })

  it('cuts the SysML constraint in one line', () => {
    expect(propertyText('sysml', SYSML, at(1), null)).toBe('mustAlways( not isStateActive(system.trackA.Behaviour.InCrossing) or isStateActive(system.barrier.Behaviour.Down) )')
  })

  it('finds nothing for a case that inherits its property, not the next case\'s', () => {
    expect(propertyText('oxsts', OXSTS, at(2), at(5))).toBeNull()
  })
})
