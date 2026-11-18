# Operand families of `CodeBuilder`

Six public enums carry the operand families of the emitters: which opcodes belong to an operation, which
types it consumes and produces, and which literal form exists. `of(Opcode)` normalises any form and throws
`IllegalArgumentException` when the opcode belongs to another family; getters return `null` where a form
does not exist.

## com.v7878.dex.builder.CodeBuilder.BinOp

`public enum CodeBuilder.BinOp`

Invariant: four stored opcodes — regular, `/2addr`, `lit16`, `lit8` — each `null` where the form does not exist.

**Fields** — `public static final BinOp`: `ADD_INT` `RSUB_INT` `SUB_INT` `MUL_INT` `DIV_INT` `REM_INT` `AND_INT` `OR_INT` `XOR_INT` `SHL_INT` `SHR_INT` `USHR_INT` `ADD_LONG` `RSUB_LONG` `SUB_LONG` `MUL_LONG` `DIV_LONG` `REM_LONG` `AND_LONG` `OR_LONG` `XOR_LONG` `SHL_LONG` `SHR_LONG` `USHR_LONG` `ADD_FLOAT` `RSUB_FLOAT` `SUB_FLOAT` `MUL_FLOAT` `DIV_FLOAT` `REM_FLOAT` `ADD_DOUBLE` `RSUB_DOUBLE` `SUB_DOUBLE` `MUL_DOUBLE` `DIV_DOUBLE` `REM_DOUBLE`

**Methods**

| Signature | Note |
|---|---|
| `public static BinOp[] values()` |  |
| `public static BinOp valueOf(String)` |  |
| `public static BinOp of(Opcode)` | any form |
| `public Opcode regular()` |  |
| `public Opcode _2addr()` |  |
| `public Opcode lit16()` |  |
| `public Opcode lit8()` |  |
| `public TypeId getDstAndSrc1Id()` |  |
| `public boolean isDstAndSrc1Wide()` |  |
| `public TypeId getSrc2Id()` |  |
| `public boolean isSrc2Wide()` |  |
| `public boolean canThrow()` |  |

The mnemonic is the lower-cased name with `_` → `-` (`ADD_INT` → `add-int`); other forms append `/2addr`,
`/lit16`, `/lit8`. Exceptions: `RSUB_INT` has only `rsub-int` and `rsub-int/lit8`; `SUB_INT` has no literal
form; the three `*_INT` shifts have only `lit8`; long, float and double operations have only the regular and
`/2addr` forms, and `RSUB_LONG`/`RSUB_FLOAT`/`RSUB_DOUBLE` have no opcode and are never returned by `of`. The
type is the suffix (`I`, `J`, `F`, `D`) except that the long shifts take an `int` second source; `canThrow()`
is true only for `DIV_INT`, `REM_INT`, `DIV_LONG`, `REM_LONG`.

## com.v7878.dex.builder.CodeBuilder.Cmp

`public enum CodeBuilder.Cmp`

Invariant: `cmpl-*` returns `-1` and `cmpg-*` returns `1` for `NaN`; the builder never chooses between them.

**Fields** — `public static final Cmp`: `CMPL_FLOAT` `CMPG_FLOAT` `CMPL_DOUBLE` `CMPG_DOUBLE` `CMP_LONG`

**Methods**

| Signature | Note |
|---|---|
| `public static Cmp[] values()` |  |
| `public static Cmp valueOf(String)` |  |
| `public static Cmp of(Opcode)` |  |
| `public Opcode opcode()` |  |
| `public TypeId getSrcId()` |  |
| `public boolean isWide()` |  |

`CMPL_FLOAT` → `cmpl-float` and `CMPG_FLOAT` → `cmpg-float` (src `F`), the double pair (src `D`), and
`CMP_LONG` → `cmp-long` (src `J`); `isWide()` is true for `D` and `J`, and `cmp_kind` emits `f23x`.

## com.v7878.dex.builder.CodeBuilder.InvokeKind

`public enum CodeBuilder.InvokeKind`

Invariant: each constant stores a regular and a range opcode; the kind is never checked against the target.

**Fields** — `public static final InvokeKind`: `VIRTUAL` `SUPER` `DIRECT` `STATIC` `INTERFACE`

**Methods**

| Signature | Note |
|---|---|
| `public static InvokeKind[] values()` |  |
| `public static InvokeKind valueOf(String)` |  |
| `public static InvokeKind of(Opcode)` |  |
| `public Opcode regular()` |  |
| `public Opcode range()` |  |

`VIRTUAL` → `invoke-virtual` / `invoke-virtual/range`, likewise `super`, `direct`, `static`, `interface`.

## com.v7878.dex.builder.CodeBuilder.Op

`public enum CodeBuilder.Op`

Invariant: each constant stores the array, instance and static opcode of one access; the direction comes from the constant, the shorty from the caller.

**Fields** — `public static final Op`: `GET` `GET_WIDE` `GET_OBJECT` `GET_BOOLEAN` `GET_BYTE` `GET_CHAR` `GET_SHORT` `PUT` `PUT_WIDE` `PUT_OBJECT` `PUT_BOOLEAN` `PUT_BYTE` `PUT_CHAR` `PUT_SHORT`

**Methods**

| Signature | Note |
|---|---|
| `public static Op[] values()` |  |
| `public static Op valueOf(String)` |  |
| `public static Op of(Opcode)` | all three forms |
| `public Opcode aop()` |  |
| `public Opcode iop()` |  |
| `public Opcode sop()` |  |
| `public static Op op_get(char)` |  |
| `public static Op op_put(char)` |  |

The name maps to `a`/`i`/`s` plus a suffix: `GET` → `aget`/`iget`/`sget`, `GET_WIDE` → `aget-wide`/…,
`GET_OBJECT` → `-object`, `GET_BOOLEAN` → `-boolean`; `PUT` is the same. `op_get`/`op_put` map the nine
shorties: `Z`→`*_BOOLEAN`, `B`→`*_BYTE`, `S`→`*_SHORT`, `C`→`*_CHAR`, `I`/`F`→plain, `J`/`D`→`*_WIDE`,
`L`→`*_OBJECT`, else `IllegalArgumentException` (an invalid shorty).

## com.v7878.dex.builder.CodeBuilder.Test

`public enum CodeBuilder.Test`

Invariant: `inverse()` is an involution; `if-lt`/`if-ge`/`if-gt`/`if-le` are only valid for integers, which the builder does not check.

**Fields** — `public static final Test`: `EQ` `NE` `LT` `GE` `GT` `LE`

**Methods**

| Signature | Note |
|---|---|
| `public static Test[] values()` |  |
| `public static Test valueOf(String)` |  |
| `public Test inverse()` |  |
| `public static Test of(Opcode)` |  |
| `public Opcode test()` |  |
| `public Opcode testz()` |  |

`EQ` → `if-eq` and `if-eqz`, likewise `ne`, `lt`, `ge`, `gt`, `le`; `inverse()` swaps `EQ`↔`NE`,
`LT`↔`GE`, `GT`↔`LE`, and the one-consumer and wide-offset branch forms rely on it.

## com.v7878.dex.builder.CodeBuilder.UnOp

`public enum CodeBuilder.UnOp`

Invariant: one opcode, a destination type and a source type per constant; either side may be a pair.

**Fields** — `public static final UnOp`: `NEG_INT` `NOT_INT` `NEG_LONG` `NOT_LONG` `NEG_FLOAT` `NEG_DOUBLE` `INT_TO_LONG` `INT_TO_FLOAT` `INT_TO_DOUBLE` `LONG_TO_INT` `LONG_TO_FLOAT` `LONG_TO_DOUBLE` `FLOAT_TO_INT` `FLOAT_TO_LONG` `FLOAT_TO_DOUBLE` `DOUBLE_TO_INT` `DOUBLE_TO_LONG` `DOUBLE_TO_FLOAT` `INT_TO_BYTE` `INT_TO_CHAR` `INT_TO_SHORT`

**Methods**

| Signature | Note |
|---|---|
| `public static UnOp[] values()` |  |
| `public static UnOp valueOf(String)` |  |
| `public static UnOp of(Opcode)` |  |
| `public Opcode opcode()` |  |
| `public TypeId getDstId()` |  |
| `public boolean isDstWide()` |  |
| `public TypeId getSrcId()` |  |
| `public boolean isSrcWide()` |  |

The mnemonic is the lower-cased name with `_` → `-` (`NEG_INT` → `neg-int`, `INT_TO_LONG` → `int-to-long`).
`getDstId` is the part after `NEG_`/`NOT_` or before `_TO_`, `getSrcId` the part after `_TO_` (equal to the
destination for `NEG_*`/`NOT_*`); both wide flags are true for `J` and `D`.

Up: [SKILL.md](../SKILL.md)
