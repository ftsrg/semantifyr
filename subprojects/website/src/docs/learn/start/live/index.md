---
title: Try in the Browser
slug: /learn/live
description: Verify a SysML v2, Gamma or Semantifyr model in Semantifyr Live, in the browser with nothing to install, and what the controls of Live and of the editor a page embeds show.
---

import ThemedImage from '@theme/ThemedImage';
import useBaseUrl from '@docusaurus/useBaseUrl';
import LiveLimit from '@site/src/components/LiveLimit';

# Try in the Browser

[Semantifyr Live](https://live.semantifyr.org) edits and verifies a SysML v2, Gamma or Semantifyr model in the browser, with nothing to install and no account. Its editor has syntax highlighting, diagnostics, content assist and inlay hints; for SysML v2 the language server runs in your browser, with the standard library and the verification library at hand. A page of this site embeds the same editor in place of a code block.

## A First Run

1. Open [Live](https://live.semantifyr.org). It opens on a SysML v2 example, the level crossing.
2. Pick a language and one of its example models in the toolbar.
3. Press **Verify all**. Each case gets its verdict as the server answers.
4. Select a case with a trace to read it below the cases.
5. Edit the model, then **Save** it to a file or **Share** it.

The public instance stops a verification after <LiveLimit />.

## The Embedded Editor

The editor embedded in a page has the model on top and the verification below it:

<ThemedImage alt="The embedded editor on the model of the second lesson, its cases verified" sources={{ light: useBaseUrl('/img/screenshots/embed-light.png'), dark: useBaseUrl('/img/screenshots/embed-dark.png') }} />

- The **editor** underlines errors in red and opens content assist with `Ctrl+Space`. The language server validates the model as you type.
- The strip under the editor holds **Verify all**, which verifies every case of the model and turns into **Cancel** while they run, a status line, and **Open in Live**, which opens the model and its results in Live, in a new tab.
- Under the strip, every verification case of the model has a line, which follows your edits: its name and its verdict, *Passed*, *Failed*, *Inconclusive* or *Error*, or *Not run* before it has run, *Queued* while it waits its turn and *Running* while the server runs it, and the time the server spent running it. When the server is busy with other people's verifications, the status line says so and names the case that waits. A case with a trace says **Trace** at the end of its line; a click on the line opens the trace below it, which shows how the verdict came about, in views: *Steps* lists the state changes of each step, *Calls* the traced calls of each step, and *Witness* the witness as a read-only Semantifyr model. A SysML v2 or a Gamma case has a first view more, the trace in its model's language, *SysML trace* or *Gamma trace*. After **Verify all** the trace of the first failed case opens by itself, or the first trace there is when no case failed. *Calls* fills only for calls of transitions marked [`@Trace`](../../language/specification/library-and-output/standard-library.md#trace). A trace whose witness passed [validation](../../backends/portfolios-and-verdicts.mdx#validation) is marked *validated*.

A model without a verification case opens the editor alone.

## Live

<ThemedImage alt="Live on the SysML v2 level crossing, its cases verified and a trace open" sources={{ light: useBaseUrl('/img/screenshots/live-light.png'), dark: useBaseUrl('/img/screenshots/live-dark.png') }} />

Live itself, behind **Open in Live** and at [live.semantifyr.org](https://live.semantifyr.org), puts the cases in a column to the right of the editor. The column's header counts the cases that *Passed*, *Failed*, were *Inconclusive* or ended in an *Error* and holds **Verify all** and the settings of the verification, the portfolio among them; the play button in a case's row verifies that case alone, and the trace of the selected case sits below the cases, where **Open** moves a textual view to a read-only tab beside the model. Its toolbar has the language switch (SysML v2, Gamma and Semantifyr Language), for the Semantifyr Language the library the model is written with, the example menu, **Open** and **Save** for files, **Share**, which puts your model and its results into a link, **Documentation**, which opens this site, **About**, the developer panel, and the theme.

What Live's server keeps of your model and what it logs is on [its own page](privacy.md).
