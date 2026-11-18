# Types: TypeInfo and TypeResolver

`TypeInfo` is a type as the verifier sees it: a non-array base plus the array depth, with a null base
for "unresolved". `TypeResolver` answers assignability, least common supertype and "is an interface".

## com.v7878.dex.analysis.TypeInfo

`public record TypeInfo(TypeId base, int array_depth)`

Invariant: `of` normalises, so `int[][]` is base `int` with depth 2.

**Fields** — `public static final TypeInfo`: `UNKNOWN`

**Constructors** — `public TypeInfo(TypeId base, int array_depth)`

**Methods**

| Signature | Note |
|---|---|
| `public static TypeInfo of(TypeId type)` | splits base and depth; null becomes UNKNOWN |
| `public TypeId base()` | the non-array base, or null |
| `public int array_depth()` | never negative |
| `public TypeId exactType()` | base with the depth applied, or null |
| `public boolean isUnresolved()` | base is null |
| `public boolean isArray()` | depth above zero |
| `public boolean isBaseReference()` | true when unresolved, fail-open |
| `public boolean isBasePrimitive()` | negation of the above |
| `public char getBaseShorty()` | L when unresolved |
| `public boolean isComponentReference()` | depth above one, or a reference base |
| `public boolean isComponentPrimitive()` | negation |
| `public char getComponentShorty()` | L when unresolved or depth above one |
| `public TypeInfo getComponentType()` | same base, depth minus one |
| `public boolean isReference()` | an array or a reference base |
| `public boolean isPrimitive()` | negation |
| `public char getShorty()` | L for an array or an unresolved type |
| `public String toString()` | dotted base name plus [] per dimension |
| `public boolean equals(Object)` | both components |
| `public int hashCode()` | both components |

An array built over a primitive base throws `IllegalArgumentException`, as does a negative depth;
component accessors throw `IllegalStateException` at depth zero; an unresolved type prints as
`<unresolved reference>` plus [] per dimension.

## com.v7878.dex.analysis.TypeResolver

`public abstract sealed class TypeResolver`

Invariant: sealed; behaviour comes from the mapper given to `simple`, not from subclassing.

**Fields** — `public static final TypeResolver`: `DEFAULT`

**Constructors** — `public TypeResolver()`

**Methods**

| Signature | Note |
|---|---|
| `public static TypeResolver simple(Function<TypeId, ? extends ClassHeader> mapper)` | null mapper throws |
| `protected abstract TypeId resolveJoin(TypeId a, TypeId b)` | null means unknown |
| `protected abstract Boolean resolveInstanceOf(TypeId a, TypeId b)` | null means unknown |
| `protected abstract Boolean resolveIsInterface(TypeId type)` | null means unknown |
| `protected abstract Boolean resolveIsFinal(TypeId type)` | class has no proper subtypes; null means unknown |
| `public TypeInfo join(TypeInfo a, TypeInfo b)` | see join |
| `public TypeInfo join(TypeInfo a, TypeId b)` | converts the second argument |
| `public TypeInfo join(TypeId a, TypeInfo b)` | converts the first |
| `public TypeInfo join(TypeId a, TypeId b)` | converts both |
| `public boolean isAssignable(TypeInfo a, TypeInfo b, boolean unknownMatches, boolean strict)` | see isAssignable |
| `public boolean isAssignable(TypeInfo a, TypeId b, boolean unknownMatches, boolean strict)` | converts the target |
| `public boolean isAssignable(TypeId a, TypeInfo b, boolean unknownMatches, boolean strict)` | converts the source |
| `public boolean isAssignable(TypeId a, TypeId b, boolean unknownMatches, boolean strict)` | converts both |
| `public boolean isInterface(TypeInfo type, boolean defaultValue)` | defaultValue when unknown |
| `public boolean isInterface(TypeId type, boolean defaultValue)` | null type throws NullPointerException |
| `public boolean isFinal(TypeInfo type, boolean defaultValue)` | see isFinal |
| `public boolean isFinal(TypeId type, boolean defaultValue)` | converts; null throws |

`DEFAULT` answers null for every class relation, so only equal types, `Object` and unresolved cases are
decided.

## join

1. A primitive type throws `IllegalArgumentException`; a primitive *array* is a reference and is
   accepted.
2. Equal types return `a`.
3. `depth` is the smaller array depth; if the side at `depth` has a primitive base, `depth` decreases
   by one, so `join(int[], float[])` continues at depth 0; both it and `join(int[], int[][])` are
   `Object`.
4. If either side is deeper, the side sitting at `depth` wins unchanged when its base is unresolved,
   `java.io.Serializable` or `java.lang.Cloneable`; otherwise the answer is `Object` at `depth`.
5. Otherwise the result is `joinFlat(a, b)` at `depth`: equal bases give that base, `Object` on either
   side gives `Object`, an unresolved side gives unresolved, anything else gives `resolveJoin(a, b)`.

Only one side can qualify in step 4, because `depth` is the minimum; argument order is observable only
in step 5, where the bases reach `resolveJoin` as `(a, b)`. `join` never returns null. Interfaces are
lost: `join(C, I)` where `C implements I` is `Object`; `join(Serializable[], C[][])` is
`Serializable[]`.

## isAssignable

1. A primitive type throws the same `IllegalArgumentException`.
2. Equal types return `!unresolved || unknownMatches`.
3. A deeper target returns false: a shallower array never fits a deeper one.
4. A shallower target is true when `strict` is false and its base is an interface; otherwise an
   unresolved base gives `unknownMatches` and a resolved one is accepted only for `Object`,
   `Serializable` or `Cloneable`.
5. If either base is primitive the answer is false.
6. Otherwise: an `Object` target is true; `strict` with an `Object` source is false; an unresolved base
   gives `unknownMatches`; equal bases are true; with `strict` false an interface target is true and an
   `Object` source is false; finally `resolveInstanceOf(a, b)`, with null mapped to `unknownMatches`.

`strict` affects only reference-to-interface checks: false is the Android verifier's rule, true requires
the class to really implement the interface. `unknownMatches` answers only undeterminable relations and
never overrides `Object` as a target. `isInterface` rejects a primitive the same way, returns
`defaultValue` for an unresolved type, false for an array, and `resolveIsInterface` otherwise.

## isFinal

An array is final exactly when its component is final; a primitive at depth 0 throws
`IllegalArgumentException`; an unresolved type gives `defaultValue`; `simple` reads `ACC_FINAL`.

## check-cast

Dead only when no runtime class can be a subtype of both types; unresolved types fail open and the
value must be runtime-non-null. Unrelated classes, a final class vs an unrelated interface, `A[]` vs
an interface other than `Serializable`/`Cloneable` and `int[]` vs `A[]` are sound kills; finality and
`exact` decide the rest. Class/interface in both directions, two interfaces and array components
follow the same rule.

## simple(mapper)

`resolveJoin` walks superclass chains only: two ordered sets from the class up to `Object`, then both
iterators in lockstep, the first class found in the other chain winning. No intersection gives `Object`
for two complete chains and null when a link was unknown; a null superclass is `Object`.
`resolveInstanceOf` is a breadth-first walk from the source over superclasses and interfaces, each type
once: the target reached is true, otherwise false when all was known and null when a class was missing.
`resolveIsInterface` tests `ACC_INTERFACE` and gives null for an unknown class. There is no cache, only
the superclass, interfaces and flag are read, and package identity is the descriptor prefix.

Up: [SKILL.md](../SKILL.md)
