---
title: Trying It in Live
sidebar_position: 1
description: Verify a SysML v2, Gamma or OXSTS model in Semantifyr Live, in the browser, with nothing to install.
---

import LiveLimit from '@site/src/components/LiveLimit';

# Trying It in Live

[Semantifyr Live](https://live.semantifyr.org) edits and verifies a SysML v2, Gamma or OXSTS model in the browser, with nothing to install and no account. Its editor has syntax highlighting, diagnostics, content assist and inlay hints; for SysML v2 the language server runs in your browser, with the standard library and the verification library at hand.

1. Open [Live](https://live.semantifyr.org). It opens on a SysML v2 example, the level crossing.
2. Pick a language in the toolbar's switch, SysML v2, Gamma or OXSTS, and a model in the example menu beside it.
3. Press **Verify all** above the cases, or a case's own run button. Each case gets its verdict as the server answers.
4. Select a case with a trace to read it below the cases: for SysML v2 as a SysML v2 model, for Gamma in Gamma's trace language, and for every language as the steps and calls of the OXSTS model.
5. Edit the model; the cases follow your edits. **Save** writes the model to a file, and **Share** puts the model and its results into a link.

The public instance stops a verification after <LiveLimit />. [Live's Screen](../tools/live.md) lists every control and view, and [what Live's server keeps](../tools/privacy.md) of your model is on a page of its own.
