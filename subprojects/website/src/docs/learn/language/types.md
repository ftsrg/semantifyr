---
title: Types
sidebar_position: 3
---

# Types

Every expression has a type, which the validator computes and checks: a domain, which is a [datatype](datatypes.md), an enumeration, a record, a [class](classes.md) or a [feature](features.md), and a [multiplicity](features.md#multiplicities), which bounds how many values the expression holds, `[1]` unless one is written.

## The Type of an Expression

- A literal has its datatype: `int`, `real`, `bool` or `string`. `*` is of type `any`, and `nothing` has no domain and fits any [optional](#assignability) slot. An array literal of `n` values has the domain its values share and the multiplicity `[n]`, a record literal its record.
- A variable or a parameter has its declared type, and indexing an array gives one value of its domain.
- A feature named by itself has itself as its domain, with its multiplicity: its values are the instances it holds. A feature reached through a navigation, `a.f`, has itself as its domain with the multiplicity `[1]`. Where a feature stands for a value, its class or datatype stands for it, so `refers limit: int = 10` is used as an `int`.
- `self` has the class it is written in, and an enum literal its enumeration.
- `+`, `-`, `*` and `/` give an `int` for two `int` operands and a `real` when an operand is `real`; a unary `+` or `-` keeps its operand's type.
- A comparison, a Boolean operator, `!`, `AG` and `EF` give a `bool`, and a range an `int` range.
- A property call has the property's return type, a [cast](expressions.md#casts-as) the type it names, and an [if-then-else](expressions.md#if-then-else) the type both branches fit, as [below](#comparison-and-joining).

```oxsts
class Gauge {
  refers limit: int = 10
  var reading: int := 0
  var ratio: real := 0.5

  prop overLimit(): bool {
    return reading > limit
  }

  redefine tran {
    havoc(reading)
    ratio := reading * 0.1
  }
}
```

## Assignability

A value fits a slot, a variable, a parameter, a feature's binding or a property's return, when its domain and its multiplicity fit the slot's; one that does not is rejected (`OXSTS.TYPE_MISMATCH`).

- A domain fits itself and `any`. A class fits each of its superclasses. Where the slot's domain is a feature, a feature fits only if it [subsets](features.md#subsets) the slot's, directly or through others, and a class or a datatype fits if it fits the feature's own class or datatype. Where the slot's domain is a class or a datatype, a feature fits if its class or datatype does.
- A multiplicity fits when its lower bound is not below the slot's and its upper bound is not above it: a `[1]` value fits a `[0..1]` slot, while a `[0..1]` value does not fit a `[1]` slot. `nothing` fits a slot whose lower bound is 0.
- There is no conversion between datatypes: an `int` does not fit a `real` slot, and an enumeration literal fits only its own enumeration.

```oxsts
abstract class Shape

class Square : Shape

class Canvas {
  contains square: Square
  contains shapes: Shape[0..*]
  contains first: Square subsets shapes

  var picked: Shape[0..1] := nothing
  var placed: shapes[0..1] := nothing

  redefine tran {
    picked := square
    placed := first
    picked := nothing
  }
}
```

```oxsts issue=OXSTS.TYPE_MISMATCH
class Gauge {
  var ratio: real := 1
}
```

## Comparison and Joining

`==` and `!=` compare two values when the domain of one fits the other's, whatever their multiplicities; two features whose domains do not fit are compared by their classes, and `nothing` compares with every value. `<`, `<=`, `>` and `>=` take numeric operands, `int`, `real` or `any`, and the Boolean operators Boolean ones (`OXSTS.TYPE_MISMATCH`). A record compares only by `==` and `!=`, and not with `nothing`, and an array only by `==` and `!=`, with an array of the same size (`OXSTS.TYPE_MISMATCH`).

The two branches of an [if-then-else](expressions.md#if-then-else) join when they compare and the multiplicity of one fits the other's. The joined type is the wider domain, two features meeting at their classes, with a multiplicity from the smaller lower bound to the larger upper bound, and a lower bound of 0 when a branch is `nothing`.
