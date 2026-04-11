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
