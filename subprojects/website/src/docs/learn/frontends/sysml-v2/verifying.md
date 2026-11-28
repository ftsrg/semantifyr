---
title: 'Verifying SysML v2 Models'
sidebar_position: 1
description: Verify a SysML v2 model in Live or on your machine, and write its verification cases.
---

import CodeBlock from '@theme/CodeBlock';
import { selectDeclarations } from '@site/src/components/LiveEmbed';
import room from '@served-examples/sysml/room';

# Verifying SysML v2 Models

## In Live

Open [Live](https://live.semantifyr.org), pick one of its SysML v2 examples in the toolbar or write your own model, and press **Verify all**.

## On Your Machine

```bash
semantifyr-sysml verify model.sysml
```

In VS Code, see [Try in VS Code](../../start/vscode.md). To install either, see [Install](../../start/install.md).

## Writing a Verification Case

Requirements use three calculation definitions, which Semantifyr recognizes by their names:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['import', 'eventually', 'mustAlways', 'isStateActive'])}</CodeBlock>

`eventually` (short name `EF`) states that some execution reaches a situation, `mustAlways` (short name `AG`) that every execution stays in one, and `isStateActive` asks whether a state is active.

A verification case is a `verification def` whose subject is the system under verification and whose objective is a requirement over it:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['LampLitReachable'])}</CodeBlock>

To build a model and its cases step by step, see [Lesson: A Switch and a Lamp](./lesson.md).
