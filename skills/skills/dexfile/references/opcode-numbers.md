Up: [SKILL.md](../SKILL.md)

# Formats and opcode numbers

`Format` is the width and operand shape of an encoding; `Opcodes` turns an `Opcode` into the
integer a dex file stores. Neither is a number table: the number depends on `DexVersion` and on the
options, so it is resolved per configuration and never stored next to a constant.

## com.v7878.dex.Format

`public enum Format`

Invariant: a format is a fixed width of 1..5 code units, or a payload whose `getUnitCount()` is -1;
`hasReferenceType1()` is true for the 16 formats with a reference slot and `hasReferenceType2()`
only for `Format45cc` and `Format4rcc`.

**Fields** — `public static final Format`: 1 unit - `Format10t` `Format10x` `Format11n` `Format11p` `Format11x` `Format12x` `FormatRaw10x` `FormatRaw10c`

**Fields** — `public static final Format`: 2 units - `Format20bc` `Format20t` `Format20t_24` `Format21c` `Format21ih` `Format21lh` `Format21s` `Format21t` `Format22b` `Format22c` `Format22s` `Format22t` `Format22x` `Format23x` `FormatRaw20c` `FormatWrapper20x`

**Fields** — `public static final Format`: 3 units - `Format30t` `Format31c` `Format31i` `Format31t` `Format32x` `Format34c` `Format35c` `Format3rc`

**Fields** — `public static final Format`: 4 units - `Format40sc` `Format41c` `Format45cc` `Format4rcc` `FormatWrapper40ci`

**Fields** — `public static final Format`: 5 units - `Format51l` `Format52c` `Format5rc`

**Fields** — `public static final Format`: payload, -1 units - `ArrayPayload` `PackedSwitchPayload` `SparseSwitchPayload` `MPackedSwitchPayload` `MSparseSwitchPayload`

`Format.ArrayPayload` and the class `com.v7878.dex.immutable.bytecode.ArrayPayload` share a name:
qualify the constant when both are imported. `Format20bc` and `Format40sc` are the odex
verification-failure pair - `throw-verification-error` (`0xed`, Dalvik and odex only, API 5+) and
its `/jumbo` form (stored as the first unit `0xffff`, expanded API 14-15 plus odex) - and share the
`InstructionN0ci` carrier.

**Methods**

| Signature | Note |
|---|---|
| `public static Format[] values()` | declaration order |
| `public static Format valueOf(java.lang.String name)` | IllegalArgumentException when absent |
| `public int getUnitCount()` | 1..5, or -1 for a payload |
| `public boolean isPayload()` | the five payload constants |
| `public boolean hasReferenceType1()` | one reference slot |
| `public boolean hasReferenceType2()` | `Format45cc`, `Format4rcc` |

`isPayload()` never coexists with a reference: a payload carries data, not references. The two
slots of `Format45cc`/`Format4rcc` are a method and a proto, and `Format` says nothing about which
pool a slot points into - that is the opcode's `getReferenceType1()`.

## com.v7878.dex.Opcodes

`public final class Opcodes`

Invariant: one immutable table per `(DexVersion, options)` pair; a constant whose constraint rejects
the configuration is simply absent, so a miss is a normal outcome.

**Methods**

| Signature | Note |
|---|---|
| `public static Opcodes of(DexVersion dex, DexOptions<?> options)` | reads only api, art, odex, expanded |
| `public Opcode getOpcodeByValue(int value)` | IllegalArgumentException when nothing matches |
| `public int getOpcodeValue(Opcode opcode)` | IllegalArgumentException when the constraint rejects |

Both directions agree: `getOpcodeByValue(getOpcodeValue(op)) == op` for every opcode the
configuration allows.

## The constraint model

An `Opcode` constant carries a **constraint**, not a number: a predicate over the dex version, the
api level and the `odex`/`expanded` flags, plus the integer it maps to. `Opcodes.of` walks every
constant, keeps the ones the constraint accepts and stores value -> constant. A miss throws with the
constraint and the configuration:

> Constraints for opcode INVOKE_VIRTUAL ((cdex || dex >= DEX035) -> {6e}) do not allow
> obtaining int value, options={dex=DEX009, api=26, art=false, odex=false, expanded=false}

`getOpcodeByValue` also lists constants whose constraint *contains* the requested number, which is
how you learn that a value exists in another configuration.

Four independent inputs, in order of effect:

| Input | Effect |
|---|---|
| dex version | modern needs dex035+ or any compact dex; legacy needs dex009/dex013 exactly |
| api | polymorphic/custom calls at 26, method-handle/type constants at 28, /jumbo at 14-15 |
| art | a few odex quick-field and quick-invoke numbers differ between Dalvik and ART |
| odex, expanded | odex-only and expanded constants are absent unless their own flag is on |

`odex` and `EXPANDED` are **independent**: a /jumbo constant that is also odex-only needs both
flags, and turning `odex` on enables no /jumbo constant. `odexOnly()` means
`withOdexInstructions(true)`, not "inside an .odex container". `isExpanded()` means
`withExpandedInstructions(true)` with api 14-15, whatever the dex version.

`0x15` is `CONST_WIDE_16` in dex009/dex013 and `CONST_HIGH16` from dex035 on; `0x1a` is
`M_CONST_SPECIAL` against `CONST_STRING`; `THROW` is `0x32`, `0x33` or `0x27`; only
`ARRAY_PAYLOAD` (`0x300`) keeps one number everywhere.

Two shapes that are easy to misread. `11p` exists only in dex009/dex013 and holds the two legacy
`m-const*` instructions, which are **not** odex (`odexOnly()` is false for both). And `sget`/`sput`
with all their typed forms are `21c` - one register plus a field index - so they need
`InstructionN1c`, never a `22c` carrier. `34c` is legacy-only, and the `RAW*` constants have no
number at all: their first code unit is the stored value.

Read a number through the table, never from a constant:

```java
Opcodes table = Opcodes.of(DexVersion.DEX038, ReadOptions.defaultOptions());
int value = table.getOpcodeValue(Opcode.INVOKE_VIRTUAL);   // 0x6e
Opcode back = table.getOpcodeByValue(0x6e);                // Opcode.INVOKE_VIRTUAL
```
