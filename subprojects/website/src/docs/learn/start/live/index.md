---
title: Try in the Browser
slug: /learn/live
description: Verify a SysML v2, Gamma, or Semantifyr model in Semantifyr Live, in the browser with nothing to install.
---

import ThemedImage from '@theme/ThemedImage';
import useBaseUrl from '@docusaurus/useBaseUrl';
import LiveLimit from '@site/src/components/LiveLimit';

# Try in the Browser

[Semantifyr Live](https://live.semantifyr.org) lets you edit and verify SysML v2, Gamma, and Semantifyr models in the browser.

<ThemedImage alt="Live on the SysML v2 level crossing, its cases verified and a trace open" sources={{ light: useBaseUrl('/img/screenshots/live-light.png'), dark: useBaseUrl('/img/screenshots/live-dark.png') }} />

## A First Run

1. Open [Live](https://live.semantifyr.org). It opens on a SysML v2 example, the level crossing.
2. Pick a language and one of its example models in the toolbar.
3. Press **Verify all**. Each case gets its verdict as the server answers.
4. Select a case with a trace to read it below the cases.
5. Edit the model, then **Save** it to a file or **Share** it.

The public instance stops a verification after <LiveLimit />.

What the server keeps of your model is on the [Privacy](privacy.md) page.
