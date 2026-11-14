---
title: Architecture
sidebar_position: 0
description: Semantifyr from a distance, its parts, the abstractions between them and the decisions that shape them, for a contributor about to read the code.
---

# Architecture

This page is Semantifyr from a distance, for a contributor about to read the code, which holds every detail; the [API documentation](pathname:///api/) is generated from it. [Contributing](contributing.md) says how to make a change.

## The Parts

- **The language.** OXSTS, its grammar, its validator and its type system, in which the semantic libraries and the models verified on them are written. [The OXSTS Language](../learn/reference/language/index.md) specifies it.
- **The compiler.** It turns one verification case of an OXSTS model into a transition system in three phases, instantiation, inlining and flattening, with an optimizer after the last two; [How the Language Works](../learn/how-it-works/index.mdx) shows each phase.
- **The verifier.** It finds the verification cases of a model, has each compiled and verified, and back-annotates a backend's evidence into an OXSTS witness, which it can verify again to validate it.
- **The backends.** Each turns the transition system into the input language of its model checker, refuses up front what that language cannot express, runs the checker and reads its answer and its evidence back. A portfolio runs several of them side by side and decides the verdict from their answers ([Verification and Portfolios](../learn/tools/verification.mdx)).
- **The frontends.** Each takes an engineering language to OXSTS: a translation of its models onto a semantic library of its own, and a generator that writes a witness back as a trace in the language. The SysML v2 frontend translates with a fork of SysIDE, the Gamma frontend with a language of its own beside OXSTS.
- **The launchers.** `semantifyr`, `semantifyr-sysml` and `semantifyr-gamma` share their options, output and exit codes; each distribution carries the Theta backend, and a frontend's launcher its semantic library.
- **The editors.** Language servers for OXSTS and Gamma, and the VS Code extension that runs them and shows a case's witness and trace.
- **Live.** A server that hosts the same language servers and verifications for many visitors, and a browser frontend over it; the SysML v2 language server runs in the visitor's browser, which sends the server only the translated OXSTS.

## The Abstractions Between Them

- A **semantic library** is plain OXSTS: a frontend emits a model that instantiates its classes, and the compiler never knows which language a model came from.
- A **verification case** is a class of the model with a property, so a model carries its own questions in every language.
- The **transition system** a backend receives is one model of variables, an initial transition, a main transition and a property, the same for every backend.
- A **verdict** carries the direction in which its backend is trusted, and a portfolio reports only an answer that direction backs.
- A **witness** is an OXSTS model of the one execution a backend found, from which every trace is made, and which can be verified again.

## The General Decisions

- The meaning of a language is written as a library, not built into a translator, so it can be read, varied and verified like any model.
- A frontend translates strictly: a construct it does not support is a diagnostic on the construct, never a model that means something else.
- Every backend implements the language as the specification defines it; what a backend cannot express is a refusal it states before it runs.
- What the site states of the tool is generated from the tool or checked against it where it can be: the command-line help, the construct tables, the standard library, the settings and the examples.
