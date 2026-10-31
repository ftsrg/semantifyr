---
title: Packages and Imports
sidebar_position: 1
---

# Packages and Imports

## Package Declaration

Every Semantifyr file begins with a `package` declaration. Fully qualified names use `::` as a separator.

```oxsts
package example::library
```

```oxsts
package example::deep::namespace
```

## Imports

`import` brings the symbols of another package into scope. Imports may appear after the package declaration.

```oxsts
package example::application

import example::library
```

Without an `import`, symbols from another package can still be referenced by their fully qualified name (for example, `example::library::Foo`).

## Name Resolution

A name is looked up in the enclosing declarations first, then in the file's own package, then in the explicit imports in the order they are written, then in the implicit imports. When two imported packages declare the same name, the first import wins.

## Variants

A library ships a semantic variant as a package that imports the base package and subclasses the classes it changes under the same name:

```oxsts
package example::topdown

import example::library

class State : example::library::State {
    redefine tran fireTransitions() {
        inline fireLocalTransitions()
    }
}
```

A model selects the variant by importing it before the base package, so its `State` shadows the base one:

```oxsts
package example::application

import example::topdown
import example::library
```

The SysML v2 library's `semantifyr::sysml::topdown` package is such a variant: its `State` gives the transitions of an outer state priority over those of its inner states.

## Implicit Imports

The [`semantifyr` standard library](standard-library.md) is always in scope.

```oxsts
package example::application

import semantifyr // implicit
```

{/* TODO: this is not yet true, but should be
## Multiple Files per Package

Multiple files may contribute to the same package. Declaring the same `package` in two files merges their declarations into a single namespace.
*/}
