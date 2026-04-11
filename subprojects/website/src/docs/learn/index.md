---
title: Introduction
sidebar_position: 0
---

# Introduction to Semantifyr

**Semantifyr** is a verification framework that brings formal methods to high-level engineering languages without requiring users to write the verification model by hand. Instead of building a single, monolithic translator from each engineering language to a model checker, Semantifyr lets you encode the *execution semantics* of a language in a reusable **semantic library**, and then verify any model in that language by transforming it into a thin instantiation of the library.

The result: the same library can verify many models, the same model can be checked against different semantic variants, and the underlying model checker (currently [Theta](https://github.com/ftsrg/theta)) never has to know about the high-level language.

## Where to go next

- [Getting Started](getting-started.md) to set up your environment.
- [Language Reference](language/index.md) to look up specific constructs.
- [Tutorials](tutorials/index.md) to build your first model from scratch.
