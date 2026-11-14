---
title: What Live's Server Keeps and Logs
sidebar_position: 2
description: What Semantifyr Live's server keeps of your model, what it logs and who can see it.
---

# What Live's Server Keeps and Logs

[Semantifyr Live](https://live.semantifyr.org) verifies your model on its server. This page says what the server keeps of it, what its log holds and who can see what. It describes the server's code and its deployment as the repository holds them.

## Your model

The editor sends your model to the server over your session's connection. The server holds it, and what its verifications write, such as the compiled model, the witness and the trace, in a working folder of your session. It deletes the folder when the session ends: when you close or reload the page, or when the connection ends. A folder left behind by a crash is deleted when the server starts again. Nothing of your model is kept anywhere else.

For a SysML v2 model, the translation into OXSTS runs in your browser, and the server receives the OXSTS alone.

## Your browser

Live keeps your settings in your browser: the theme, the chosen portfolios, automatic validation and the sizes of the panels. It caches its own files so that it loads fast. It keeps no model and sets no cookies.

A shared link carries the model, the selected case and the verdicts in the part of the link after `#`, which the browser does not send to the server. The server stores nothing for a link.

## The log

The server writes one log. Each line carries its time, and a line written for a session carries the random identifier of that session.

- Each session: its network address, its start and its end, the library, the language and the kind of view (the full editor, an embedded one, or one that only edits), and the errors of its connection.
- Each verification: the qualified name of the case and of the classes the compiler works on, the portfolio, the time limit and the other settings it ran with, the backends that ran with their configurations, their verdicts and the final verdict, the durations of each phase, and the paths of the files it wrote in the session's folder.
- The compiler's warnings, each with its line, its column and its message, which can name the classes, the features and the words of your model.
- Errors, with their messages and the program's stack: a syntax error of the model, with its line, its column and the word it found; an error of the compiler or of a backend, which can name parts of your model or quote the lines a backend printed about the model it was given; and the addresses and paths of the documents an error concerns.

The text of your model as a whole, and the models the backends are given, are not logged. The host keeps at most five log files of 20 MB and drops the oldest.

## Operators

The project's operators sign in through GitHub. They see the open sessions with their network address, library, language, kind of view, working folder and running verifications with their portfolio, and can end a session or a verification. That view shows no model. The operators who run the host can read the log and the working folders while they exist.

## Metrics

The server counts the sessions, the verifications by backend and verdict, their durations and waits, the time of the editor's requests, and the memory, processor time and processes it uses. No metric carries a model, an address or anything a visitor sends.

## The way in

Requests reach the server through a Cloudflare tunnel and a proxy in front of the server. The proxy writes no access log. What Cloudflare records of a request is subject to Cloudflare's own terms.
