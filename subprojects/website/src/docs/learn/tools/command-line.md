---
title: Verifying on the Command Line
sidebar_position: 5
description: Build the SysML v2 launcher, verify a model with every installed backend, and find its artifacts and traces on disk.
---

import CodeBlock from '@theme/CodeBlock';
import CaseVerdict from '@site/src/components/CaseVerdict';
import NodeVersion from '@site/src/components/NodeVersion';
import room from '@served-examples/sysml/room';

# Verifying on the Command Line

The `semantifyr-sysml` command verifies a SysML v2 model on your machine, with every installed backend, and writes its artifacts to disk. This page verifies the room of the [SysML v2 tutorial](../sysml-v2/tutorial.md); `semantifyr` for OXSTS and `semantifyr-gamma` for Gamma run the same way, as [the command line's reference](../reference/cli/index.mdx) describes.

## Requirements

- A Java 25 runtime found through `JAVA_HOME` or `PATH`.
- Node.js <NodeVersion /> or newer on `PATH`: the translator of the SysML v2 frontend, a fork of SysIDE, runs on Node.js.
- The `semantifyr-sysml` command-line distribution ([Installing It](../start/install.md#the-command-line)), which verifies SysML v2 models ([reference](../reference/cli/sysml.mdx)). It carries the translator from SysML v2 to OXSTS and the SysML v2 semantic library, so Node.js is the only prerequisite SysML v2 adds.

To build the distribution from a clone of the repository, which needs Git (the first build downloads its dependencies, the SysIDE fork among them, and takes several minutes):

```bash
git clone https://github.com/ftsrg/semantifyr.git
cd semantifyr
./gradlew :sysml-cli:installDist
export SEMANTIFYR_HOME="$PWD/subprojects/frontends/sysml/sysml-cli/build/install/semantifyr-sysml"
```

`SEMANTIFYR_HOME` now holds the launcher in `bin/`, Theta in `theta-xsts-cli/` and the semantic library in `libraries/`; an unzipped `semantifyr-sysml-<version>.zip` has the same layout. The commands on this page are written for Linux and macOS shells; on Windows, use `bin\semantifyr-sysml.bat`.

## Verifying Locally

Create an empty directory and save the complete model in it as `room.sysml`. The translator reads every `.sysml` and `.kerml` file below the model's directory, so keep other models out of it.

<CodeBlock language="sysmlv2" title="room.sysml">{room.text}</CodeBlock>

In the directory of `room.sysml`, verify the model:

```bash
"$SEMANTIFYR_HOME/bin/semantifyr-sysml" verify room.sysml --artifact-directory ../room-artifacts
```

The `verify` command translates `room.sysml` to OXSTS, an OXSTS model that instantiates the classes of the SysML v2 semantic library, which takes about a minute. It then finds the three verification cases and verifies each with the default `smart` portfolio, which races every installed backend. The artifacts go outside the model's directory, since the translator reads every `.sysml` and `.kerml` file below it and the run writes SysML v2 traces.

For each case, the command prints its name, `PASSED` or `FAILED` with the time it took, and the path of its `report.json`, and it exits with status 5 when a case fails (the [exit codes](../reference/cli/index.mdx#exit-codes)). The cases end as their manifest expects:

- <CaseVerdict example={room} name="LampLitReachable" />
- <CaseVerdict example={room} name="LampNeverLitViolated" />
- <CaseVerdict example={room} name="LampLitOnlyWhileSwitchOnViolated" />

The case names start with `semantifyr::sysml::model`, the package the translator gives every model.

## The Artifacts

`--artifact-directory ../room-artifacts` keeps the translated model in `compiled/default/room.oxsts`, with its name map `room.names.json` beside it, and the artifacts of every case in a directory of its own; a case with a witness has `witness.json`, `trace.json`, `witness.oxsts` and `trace.sysml` among them. [Artifacts](../reference/artifacts/index.mdx) describes each file.

The trace of `LampLitOnlyWhileSwitchOnViolated`, which the tutorial reads, is in `trace.json` and `witness.json` in `../room-artifacts/room.LampLitOnlyWhileSwitchOnViolated/`, and `trace.sysml` next to them is the model of the *SysML trace* view.

