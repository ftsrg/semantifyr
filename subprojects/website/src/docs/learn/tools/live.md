---
title: Live's Screen
sidebar_position: 1
description: The controls of Semantifyr Live and of the editor a page embeds, and what each shows.
---

# Live's Screen

[Semantifyr Live](https://live.semantifyr.org) edits and verifies a model in the browser. A page of this site embeds the same editor in place of a code block. This page lists what each of them shows.

## The Embedded Editor

The editor embedded in a page has the model on top and the verification below it:

- The **editor** is a code editor with syntax highlighting, diagnostics (red underlines for errors) and content assist (triggered with `Ctrl+Space`). The language server validates the model as you type.
- The strip under the editor holds **Verify all**, which verifies every case of the model and turns into **Cancel** while they run, a status line, and **Open in Live**, which opens the model and its results in Live, in a new tab.
- Under the strip, every verification case of the model has a line, which follows your edits: its name and its verdict, *Passed*, *Failed*, *Inconclusive* or *Error*, or *Not run* before it has run, *Queued* while it waits its turn and *Running* while the server runs it, and the time the server spent running it. When the server is busy with other people's verifications, the status line says so and names the case that waits. A case with a trace says **Trace** at the end of its line; a click on the line opens the trace below it, which shows how the verdict came about, in views: *Steps* lists the state changes of each step, *Calls* the traced calls of each step, and *Witness* the witness as a read-only OXSTS model. A SysML v2 or a Gamma case has a first view more, the trace in its model's language, *SysML trace* or *Gamma trace*, and calls the other two *OXSTS steps* and *OXSTS witness*. After **Verify all** the trace of the first failed case opens by itself, or the first trace there is when no case failed. *Calls* fills only for calls of transitions marked [`@Trace`](../reference/language/standard-library.md#trace). A trace whose witness a second verification confirmed is marked *validated*.

A model without a verification case opens the editor alone.

## Live

Live itself, behind **Open in Live** and at [live.semantifyr.org](https://live.semantifyr.org), puts the cases in a column to the right of the editor. The column's header counts the cases that *Passed*, *Failed*, were *Inconclusive* or ended in an *Error* and holds **Verify all** and the settings of the verification, the portfolio among them; the play button in a case's row verifies that case alone, and the trace of the selected case sits below the cases, where **Open** moves a textual view to a read-only tab beside the model. Its toolbar has the language switch (SysML v2, Gamma and OXSTS), for OXSTS the library the model is written with, the example menu, **Open** and **Save** for files, **Share**, which puts your model and its results into a link, **About**, the developer panel, and the theme.

What Live's server keeps of your model and what it logs is on [its own page](privacy.md).
