---
title: Packages and Imports
slug: /learn/language/reference/packages-and-imports
sidebar_position: 1
description: How a Semantifyr file is written, the package it belongs to, and how imports make the declarations of other packages available.
---

# Packages and Imports

A Semantifyr model is organized using packages and imports. A package is a container for one or more files, and each file names the package it belongs to. An import makes the declarations of another package available in the current file.

## Writing a File

Write a line comment after `//`, and a block comment between `/*` and `*/`. Block comments do not nest. A block comment right before a declaration documents it, and editors show it on hover:

```oxsts
// A line comment.

/* A block comment. */

/* A lamp lights a room. */
class Lamp
```

Names are made of ASCII letters, digits, and `_`, and cannot start with a digit. To use other characters, such as spaces, or to use a keyword as a name, put the name in single quotes. The quotes are not part of the name:

```oxsts
class 'Traffic light'

class 'choice' // choice is a keyword
```

The [grammar](https://github.com/ftsrg/semantifyr/blob/main/subprojects/oxsts.lang/src/main/java/hu/bme/mit/semantifyr/oxsts/lang/Oxsts.xtext) gives the exact syntax.

## Packages

A file can start with a package declaration, which names the package the file belongs to:

```oxsts
package example::house

class Lamp

class Room {
  contains lamp: Lamp
}
```

All contents of the file, such as classes and records, belong to this package. Their fully qualified name combines the package name with their own name. In this example, the fully qualified name of `Lamp` is `example::house::Lamp`, and that of `Room` is `example::house::Room`.

Declarations of the same package use each other by their simple names, whichever file they are in.

If a file has no package declaration, it belongs to an unnamed package of its own, and no other file can use its contents.

## Imports

To use the declarations of another package by their simple names, import the package. Imports come right after the package declaration:

```oxsts
package example::street

import example::house

class Street {
  contains room: Room // example::house::Room
}
```

Without the import, write the fully qualified name, `example::house::Room`. An import always names a whole package, never a single declaration.

If two imported packages declare the same name, or the file's own package and an imported package do, the simple name is an error. Write the fully qualified name of the one you mean:

```oxsts hidden
package example::lights

class Lamp
```

```oxsts hidden
package example::signals

class Lamp
```

```oxsts
package example::corner

import example::lights
import example::signals

class Corner {
  contains lamp: example::lights::Lamp // both packages declare a Lamp
}
```

## Default Imports

Every file imports the [standard library](../library-and-output/standard-library.md), `semantifyr`, by default, so datatypes such as `int` need no import. The file's own package and its explicit imports take priority over the standard library.
