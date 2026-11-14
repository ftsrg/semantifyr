---
title: Inlined Models
sidebar_position: 13.5
---

# Inlined Models

An inlined model is the form in which the compiler writes a case after each of its phases and in which a witness is written ([Artifacts](../artifacts/index.mdx)): one file that names the class it comes from and holds the whole model a backend needs, with no class of its own. It is OXSTS: the editors open it, and it validates, compiles and verifies as any other model. It is written by the tools, not by hand.

## Syntax

An inlined model opens with `inlined oxsts of`, or `witness of` for a [witness](../artifacts/index.mdx#witnessoxsts), and the qualified name of the class. Then come, in this order, its variables, optionally a feature that holds the instance of the class, the `init` transition, the main transition, each with its branches separated by `or`, and the property, an expression in `prop { }` without `return`. It has no package and no imports. The feature is named `root` in the instantiated and the inlined model and in the witness, and the members of instances are named by their paths from it, such as `root.counter.value`; the flattened model has no feature left.

```oxsts
inlined oxsts of semantifyr::Anything

var counter: int := 0

init {
    counter := 0
}

tran {
    choice {
        counter := counter + 1
    } or {
        counter := 0
    }
}

prop {
    AG counter >= 0
}
```

## Static Rules

The rules of the rest of the reference hold, with these differences:

- A variable carries a type and an initializer, as everywhere (`OXSTS.VARIABLE_WITH_IMPLICIT_TYPE`, `OXSTS.MISSING_INITIALIZER`).
- The class is found among the declarations the editor or the compiler has loaded, and a class of a file without a package by its simple name.
- The validator gives no hint in an inlined model (`OXSTS.COULD_BE_INLINE`, `OXSTS.CONSTANT_EXPRESSION`).
- A temporal operator stands at the root of the property, as it does at the root of a property's `return`.

## Meaning

Its variables are the state, `init` gives the initial states from the variables' initializers, the main transition relates a state to the next, and the property is checked on the reachable states. An inlined model the compiler writes for a case has the case's meaning; a witness has only the execution it records, so its property passes when the execution shows the verdict.
