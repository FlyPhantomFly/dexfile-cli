# Registers: the value lattice

Each register holds one immutable value; the hierarchy is sealed.
Shapes: undefined, constant (kind only), primitive (a `Z B S C I F` value or one half of
`J`/`D`), reference, conflict, wide piece. Values are equal only when kind/type, flags and origin
match.

## com.v7878.dex.analysis.Register

`public sealed abstract class Register`

Invariant: `is*()` means "representable as", so predicates overlap: constant `0` is bool, byte, short,
char, int, float and ref at once. A constant answers by kind, a primitive by base shorty.

**Constructors** — `public Register()`

**Methods**

| Signature | Note |
|---|---|
| `public abstract Identifier getSource()` | null only for Undefined |
| `public static ConstantKind intKind(int value)` | narrowest kind; `2..0x7f` is POSITIVE_BYTE |
| `public static Register merge(TypeResolver resolver, int address, int slot, Register a, Register b)` | lattice join, see merge |
| `public final boolean isUndefined()` | |
| `public final boolean isConflict()` | |
| `public final boolean isWidePiece()` | |
| `public final boolean hasContent()` | not undefined, conflict or piece |
| `public final boolean isConstant()` | |
| `public final ConstantKind getConstantKind()` | null unless constant |
| `public final boolean isZero()` | |
| `public final boolean isNull()` | |
| `public final boolean isZeroOrNull()` | |
| `public final boolean isBool()` | |
| `public final boolean isByte()` | |
| `public final boolean isShort()` | |
| `public final boolean isChar()` | |
| `public final boolean isInt()` | |
| `public final boolean isFloat()` | |
| `public final boolean isIntOrFloat()` | |
| `public final boolean isWide()` | |
| `public final boolean isWideLo()` | |
| `public final boolean isWideHi()` | |
| `public final boolean isLong()` | |
| `public final boolean isLongLo()` | |
| `public final boolean isLongHi()` | |
| `public final boolean isDouble()` | |
| `public final boolean isDoubleLo()` | |
| `public final boolean isDoubleHi()` | |
| `public final boolean isRef()` | ZERO, NULL, a reference kind or a Reference |
| `public final boolean isRuntimeNonNullRef()` | |
| `public final boolean isArray()` | |
| `public final boolean isInitializedRef()` | |
| `public final boolean isUninitializedRef()` | |
| `public final boolean isUninitializedThis()` | |
| `public final TypeInfo getTypeInfo()` | |
| `public final boolean isExactRef()` | |
| `public final boolean isAssignable(TypeResolver resolver, TypeId type, boolean allowUninitialized, boolean unknownMatches, boolean strict)` | see isAssignable |
| `public final boolean isAssignable(TypeResolver resolver, TypeId type, boolean allowUninitialized, boolean unknownMatches)` | strict == true |

`isFloat` is false for a Primitive of `I`; `isLong`/`isDouble` are both true for a wide constant;
`isRuntimeNonNullRef` excludes ZERO, NULL and the `*_OR_NULL` kinds; `getTypeInfo` is null except
for a TypedRegister and the STRING, CLASS, METHOD_TYPE, METHOD_HANDLE and REFERENCE kinds; a REFERENCE kind gives `TypeInfo.of(Object)`. `isExactRef`
is true for string, class and method type constants (nullable kinds too) and for exactly typed
references (`new-instance`, `new-array`, a `filled-new-array` result), false for a method handle
constant, `check-cast`, a field read, an invoke result and a merge of unlike types.

## com.v7878.dex.analysis.Register.Identifier

`public record Register.Identifier(int address, int slot, boolean produced)`

Invariant: address is the code-unit address of the defining instruction, `-1` for a parameter; slot is a
real register number, never a pseudo-register; `produced` is true only when that instruction wrote the
value. A negative address other than `-1`, or `produced` with a negative address, throws
`IllegalArgumentException`.

**Constructors** — `public Identifier(int address, int slot, boolean produced)`

**Constructors** — `public Identifier(int address, int slot)` (`produced = address >= 0`)

**Methods**

| Signature | Note |
|---|---|
| `public int address()` | |
| `public int slot()` | |
| `public boolean produced()` | the instruction wrote the value; false at a merge point or a refinement |
| `public boolean isParameter()` | address is -1 |
| `public String toString()` | parameter[slot] or hex address[slot], plus `<incoming>` when not produced |
| `public boolean equals(Object)` | all three components |
| `public int hashCode()` | all three components |

## com.v7878.dex.analysis.Register.Undefined

`public static final class Register.Undefined extends Register`

**Fields** — `public static final Undefined`: `INSTANCE`

**Methods**

| Signature | Note |
|---|---|
| `public Identifier getSource()` | always null |
| `public String toString()` | undefined |
| `public boolean equals(Object)` | true for every Undefined |
| `public int hashCode()` | class hash |

## com.v7878.dex.analysis.Register.DynamicRegister

`public static sealed abstract class Register.DynamicRegister extends Register`

**Constructors** — `public DynamicRegister(Identifier source)`

**Methods**

| Signature | Note |
|---|---|
| `public Identifier getSource()` | |
| `public boolean equals(Object)` | equal origin |
| `public int hashCode()` | origin hash |

## com.v7878.dex.analysis.Register.Conflict

`public static final class Register.Conflict extends DynamicRegister`

Incompatible values merged from different paths; merging is not an error, but a `move`/`move-object`
reading a conflict register is rejected in every mode (ART android17 parity; the move kind checks
are unconditional).

**Methods**

| Signature | Note |
|---|---|
| `public static Conflict of(Identifier source)` | |
| `public String toString()` | conflict:source |
| `public boolean equals(Object)` | |
| `public int hashCode()` | |

## com.v7878.dex.analysis.Register.WidePiece

`public static final class Register.WidePiece extends DynamicRegister`

Half of a broken wide pair; `hasContent()` and `RegisterPair.isWidePair()` are false, so it never acts
as long/double.

**Methods**

| Signature | Note |
|---|---|
| `public static WidePiece of(Identifier source)` | |
| `public String toString()` | W:source |
| `public boolean equals(Object)` | |
| `public int hashCode()` | |

## com.v7878.dex.analysis.Register.ConstantKind

`public enum Register.ConstantKind`

Invariant: a range or a non-numeric family; `is*` widens ranges and overlaps.

**Fields** — `public static final ConstantKind`: `ZERO` `BOOL` `POSITIVE_BYTE` `POSITIVE_SHORT` `CHAR` `POSITIVE_INT` `BYTE` `SHORT` `INT` `WIDE_LO` `WIDE_HI` `STRING` `STRING_OR_NULL` `CLASS` `CLASS_OR_NULL` `METHOD_TYPE` `METHOD_TYPE_OR_NULL` `METHOD_HANDLE` `METHOD_HANDLE_OR_NULL` `REFERENCE` `REFERENCE_OR_NULL` `NULL`

**Methods**

| Signature | Note |
|---|---|
| `public static ConstantKind[] values()` | declaration order |
| `public static ConstantKind valueOf(String name)` | |
| `public boolean isZero()` | only ZERO |
| `public boolean isBool()` | ZERO or BOOL |
| `public boolean isPositiveByte()` | 0..0x7f |
| `public boolean isPositiveShort()` | 0..0x7fff |
| `public boolean isChar()` | 0..0xffff |
| `public boolean isPositiveInt()` | 0..0x7fffffff |
| `public boolean isByte()` | -0x80..0x7f |
| `public boolean isShort()` | -0x8000..0x7fff |
| `public boolean isInt()` | any integer kind |
| `public boolean isWideLo()` | only WIDE_LO |
| `public boolean isWideHi()` | only WIDE_HI |
| `public boolean isWide()` | WIDE_LO or WIDE_HI |
| `public boolean isString()` | STRING or STRING_OR_NULL |
| `public boolean isClass()` | CLASS or CLASS_OR_NULL |
| `public boolean isMethodType()` | METHOD_TYPE or METHOD_TYPE_OR_NULL |
| `public boolean isMethodHandle()` | METHOD_HANDLE or METHOD_HANDLE_OR_NULL |
| `public boolean isNonZeroOrNullRef()` | STRING, CLASS, METHOD_TYPE, METHOD_HANDLE or REFERENCE |
| `public boolean isNullable()` | a `*_OR_NULL` kind |
| `public boolean isMaybeNullRef()` | ZERO, NULL or a nullable kind |
| `public ConstantKind withNull()` | base kind → `*_OR_NULL`; others unchanged |
| `public ConstantKind withoutNull()` | `*_OR_NULL` → base; others unchanged |
| `public boolean isExactType()` | STRING, CLASS, METHOD_TYPE and their nullable forms |
| `public boolean isNull()` | only NULL |
| `public boolean isZeroOrNull()` | ZERO or NULL |
| `public boolean isRef()` | `isMaybeNullRef()` or `isNonZeroOrNullRef()` |
| `public String toString()` | printed name; nullable prints `string\|null` |

Standalone ranges: ZERO `[0]`, BOOL `[1]`, POSITIVE_BYTE `[2..0x7f]`, POSITIVE_SHORT
`[0x80..0x7fff]`, CHAR `[0x8000..0xffff]`, POSITIVE_INT `[0x10000..0x7fffffff]`, BYTE `[-0x80..-1]`,
SHORT `[-0x8000..-0x81]`, INT `[-0x80000000..-0x8001]`. After a merge a kind is its whole widened
range (BOOL is `0` or `1`); a nullable kind also covers null and `if-nez` sharpens it back.

## com.v7878.dex.analysis.Register.Constant

`public static final class Register.Constant extends DynamicRegister`

Invariant: the kind only; the literal lives in the defining instruction.

**Methods**

| Signature | Note |
|---|---|
| `public static Constant of(Identifier source, ConstantKind kind)` | null kind throws |
| `public static Constant of(Identifier source, int value)` | kind from intKind |
| `public static Constant of(Identifier source, boolean lo)` | true is WIDE_LO |
| `public boolean equals(Object)` | origin plus kind |
| `public int hashCode()` | |
| `public String toString()` | ~kind:source |

## com.v7878.dex.analysis.Register.TypedRegister

`public static abstract sealed class Register.TypedRegister extends DynamicRegister`

**Methods**

| Signature | Note |
|---|---|
| `public boolean equals(Object)` | origin plus type |
| `public int hashCode()` | |

## com.v7878.dex.analysis.Register.Primitive

`public static final class Register.Primitive extends TypedRegister`

One of `Z B S C I F`; an array or `void` fails the shorty check.

**Methods**

| Signature | Note |
|---|---|
| `public static Primitive of(Identifier source, TypeId type)` | `IllegalArgumentException` for an invalid shorty |
| `public String toString()` | type:source |
| `public boolean equals(Object)` | |
| `public int hashCode()` | |

## com.v7878.dex.analysis.Register.WidePrimitive

`public static final class Register.WidePrimitive extends TypedRegister`

**Methods**

| Signature | Note |
|---|---|
| `public static WidePrimitive of(Identifier source, TypeId type, boolean lo)` | shorty must be J or D |
| `public String toString()` | `<long:source` for the low half, `>double:source` for the high half |
| `public boolean equals(Object)` | origin, type, half |
| `public int hashCode()` | |

## com.v7878.dex.analysis.Register.Reference

`public sealed class Register.Reference extends TypedRegister`

A reference with a possibly unresolved type, a non-null flag and an exact flag.

**Methods**

| Signature | Note |
|---|---|
| `public static Reference of(Identifier source, TypeInfo type, boolean non_null, boolean exact)` | a primitive, or `exact` with an unresolved type, throws `IllegalArgumentException` |
| `public static Reference of(Identifier source, TypeInfo type, boolean non_null)` | exact false |
| `public static Reference of(Identifier source, TypeId type, boolean non_null, boolean exact)` | null type means unresolved; `exact` must be false |
| `public static Reference of(Identifier source, TypeId type, boolean non_null)` | exact false |
| `public String toString()` | type:source, `!` when non-null, `*` when exact |
| `public boolean equals(Object)` | origin, type, non-null, exact and the exact class |
| `public int hashCode()` | |

A primitive argument throws `IllegalArgumentException`; `of(source, (TypeId) null, true)` is a legal
non-null reference of unknown type. `equals` compares the exact class, so Reference and
UninitializedRef never compare equal.

## com.v7878.dex.analysis.Register.UninitializedRef

`public static final class Register.UninitializedRef extends Reference`

A `new-instance` result no constructor has initialised; type known, non-null.

**Methods**

| Signature | Note |
|---|---|
| `public static UninitializedRef of(Identifier source, TypeId type, boolean thiz, boolean exact)` | thiz marks the constructor receiver; null type throws `NullPointerException`, or `IllegalArgumentException` when `exact` |
| `public static UninitializedRef of(Identifier source, TypeId type, boolean thiz)` | exact false |
| `public String toString()` | @type:source! plus .this, with `*` when exact |
| `public boolean equals(Object)` | origin, type, thiz, non-null, exact |
| `public int hashCode()` | |

## com.v7878.dex.analysis.RegisterLine

`public final class RegisterLine`

Invariant: element i is register i; no public constructor or mutator; no slot is ever null.

**Methods**

| Signature | Note |
|---|---|
| `public List<Register> registers()` | unmodifiable view, stable once the analysis completes |
| `public int registerCount()` | |
| `public Register at(int index)` | unchecked; a bad index throws ArrayIndexOutOfBoundsException |
| `public boolean isThisInitialized()` | |
| `public RegisterPair pairAt(int index)` | at(index) and at(index + 1) |
| `public String describe()` | bar-separated, in brackets |

`isThisInitialized()`: false for static methods and for `<init>` until the receiver is initialised,
true otherwise; a merge of true and false is false. `describe()` of an empty line prints `[]`.

## com.v7878.dex.analysis.RegisterLine.RegisterPair

`public record RegisterLine.RegisterPair(Register lo, Register hi)`

Two consecutive registers as a wide operation sees them; two wide constants satisfy all three
predicates, because a constant half does not know J from D.

**Constructors** — `public RegisterPair(Register lo, Register hi)`

**Methods**

| Signature | Note |
|---|---|
| `public Register lo()` | |
| `public Register hi()` | |
| `public boolean isWidePair()` | lo is wide-lo and hi is wide-hi |
| `public boolean isLongPair()` | both halves are long |
| `public boolean isDoublePair()` | both halves are double |
| `public boolean equals(Object)` | both components |
| `public int hashCode()` | both components |
| `public String toString()` | RegisterPair[lo=..., hi=...] |

## merge

`merge` is the join, applied per slot and incoming edge; first match wins:

1. equal → `a`; either side Undefined → Undefined, so a slot defined on one path only is lost.
2. Conflict → Conflict at `(address, slot)`; WidePiece → WidePiece if both are, else Conflict;
   UninitializedRef → Conflict.
3. both constants: same kind → it; a wide side → WidePiece if both are wide, else Conflict; ZERO →
   the other non-wide kind with `withNull()` (`ZERO + SHORT` → `SHORT`); NULL → the other reference
   kind with `withNull()`; a reference
   kind and a primitive → Conflict; different reference bases → REFERENCE, nullable when either side
   is; both integer → `mergeInt`.
4. both int-ish → Primitive of the narrowest of `Z B S C I` that both satisfy; float-ish → `F`;
   matching long halves → `J`; matching double halves → `D`.
5. both ref-ish → Reference of `resolver.join`, non-null only when both are, exact only when both are
   exact and share the type; a low half with a high half → WidePiece; anything else → Conflict.

Steps 4 and 5 also fire for a constant plus a primitive (step 3 needs both constant). The result is a
fresh Identifier at the merge point with `produced == false`; `resolver` is used by step 5 only. `merge`
never returns null and never mutates its arguments.

`mergeInt` for unequal integer kinds: both bool-ish → BOOL; positive-byte → POSITIVE_BYTE; positive-
short → POSITIVE_SHORT; char → CHAR; positive-int → POSITIVE_INT; byte → BYTE; short → SHORT; else
INT. Every line widens, never narrows.

## isAssignable

- null type → `NullPointerException`; a primitive or `void` type → false.
- constant ZERO or NULL → true for every reference type; a non-null reference constant or any other
  Reference → `resolver.isAssignable(getTypeInfo(), type, unknownMatches, strict)`; else false.
- UninitializedRef with `allowUninitialized == false` → false; the flag affects nothing else.

The analyzer uses `allowUninitialized = false`, `unknownMatches = true`, `strict = false`; use
`strict = true` to require the implementation.

Up: [SKILL.md](../SKILL.md)
