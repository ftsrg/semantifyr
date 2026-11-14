---
title: Packages and Imports
sidebar_position: 1
---

# Packages and Imports

## Package Declaration

A Semantifyr file may begin with a `package` declaration. Fully qualified names use `::` as a separator. Two declarations of one file cannot share a name (`OXSTS.DUPLICATE_NAME`).

```oxsts
package example::library
```

```oxsts
package example::deep::namespace
```

A package is a namespace, not a file. Every file that declares the same package contributes to it: a declaration of one file is visible by its simple name in every other file of the package, and is reached from outside by the package's qualified name, whichever file holds it.

```oxsts
package example::house

class Lamp { }
```

```oxsts
package example::house

class Room {
    contains lamp: Lamp
}
```

A name declared in two files of one package is a warning at the declaration that does not win (`OXSTS.DECLARED_IN_ANOTHER_FILE`): within each file its own declaration wins, and every other file resolves the name to the first file declaring it.

```oxsts issue=OXSTS.DECLARED_IN_ANOTHER_FILE
package example::house

class Lamp { }
```

## Files Without a Package

A file without a `package` declaration lives in an unnamed package private to that file. Its declarations are visible within the file and are named by their simple names, but no other file can import or reference them, and two such files never see each other.

```oxsts
class Lamp { }

@VerificationCase
class LampCase {
    contains lamp: Lamp

    prop {
        return AG true
    }
}
```

## Imports

`import` brings the symbols of another package into scope. Imports come after the package declaration, if there is one, and before every other declaration.

```oxsts
package example::application

import example::library
```

Without an `import`, symbols from another package can still be referenced by their fully qualified name (for example, `example::library::Foo`).

A simple name declared by two imported packages, or by an imported package and the current one, is ambiguous: the reference is an error that names both declarations (`OXSTS.AMBIGUOUS_REFERENCE`). Write the qualified name of the one you mean. In a file without a package, the file's own declarations take precedence over imported ones.

```oxsts
package example::lights

class Lamp { }
```

```oxsts
package example::signals

class Lamp { }
```

```oxsts
package example::street

import example::lights
import example::signals

class Street {
    contains lamp: example::lights::Lamp
}
```

```oxsts issue=OXSTS.AMBIGUOUS_REFERENCE
package example::corner

import example::lights
import example::signals

class Corner {
    contains lamp: Lamp
}
```

## Implicit Imports

The [`semantifyr` standard library](standard-library.md) is always in scope, with lower precedence than the current package and the explicit imports.

```oxsts
package example::application

import semantifyr // implicit
```
