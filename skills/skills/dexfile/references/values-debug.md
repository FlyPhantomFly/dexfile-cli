# Debug items

A `debug_info_item` is a line table plus local-variable ranges, modelled as an ordered
`List<DebugItem>` on a `MethodImplementation`. The list is the program: nothing is sorted, merged
or validated against the instructions. A wrong order still yields a valid file with a wrong table.

## The address model

A cursor starts at 0; only `AdvancePC` moves it, forward, in code units, never bytes. Every other
item is anchored at the current cursor value.

- `AdvancePC` is not written at once. The writer tracks the reached and the encoded address and
  emits `DBG_ADVANCE_PC` only when they differ at the moment an anchored item is written:
  consecutive same-address items cost no delta, several `AdvancePC` in a row merge into one, and a
  trailing `AdvancePC` writes nothing.
- `LineNumber` stores an **absolute** line. The writer derives both deltas and packs them into a
  special opcode (0x0a..0xff) when they fit; otherwise `DBG_ADVANCE_PC` (0x01) and/or
  `DBG_ADVANCE_LINE` (0x02) precede it.
- `line_start` of the header comes from the first `LineNumber`; a list without one uses 1.
- Deltas are code units: a 21c instruction occupies two, so the move to the next instruction is
  `AdvancePC.of(2)`. Sum addresses with `Instruction.getUnitCount()`, never with text lines.
- `DBG_END_SEQUENCE` (0x00) is appended by the writer and has no model item.
- A section is written only when the method has a body and either the list is non-empty or some
  parameter name is non-null; `WriteOptions.withDebugInfo(false)` drops the bytes, not the list.

| Item | Opcode | Payload |
|---|---|---|
| `AdvancePC` | `DBG_ADVANCE_PC` 0x01 | `uleb128` address delta |
| `LineNumber` | special 0x0a..0xff, after 0x01/0x02 if needed | address and line delta packed |
| `StartLocal` | 0x03, or 0x04 (extended) when the signature is set | `uleb128` register, name+1, type+1, signature+1 |
| `EndLocal` | 0x05 | `uleb128` register |
| `RestartLocal` | 0x06 | `uleb128` register |
| `SetPrologueEnd` | 0x07 | none |
| `SetEpilogueBegin` | 0x08 | none |
| `SetFile` | 0x09 | `uleb128` file name+1 |

The +1 offsets encode "no index": null is written as 0.

## com.v7878.dex.immutable.debug.DebugItem

`public sealed class DebugItem permits AdvancePC, EndLocal, LineNumber, RestartLocal, SetEpilogueBegin, SetFile, SetPrologueEnd, StartLocal`

Invariant: no state, no `Comparable`, no shared equals; equality and hashing are per subclass.

**Constructors** — `public DebugItem()`

## com.v7878.dex.immutable.debug.AdvancePC

`public final class AdvancePC extends DebugItem`

Invariant: `addr_diff` is greater than zero; `of(0)` throws `IllegalArgumentException`, so the
address sequence of a list never decreases.

**Methods**

| Signature | Note |
|---|---|
| `public static AdvancePC of(int addr_diff)` | forward move only |
| `public int getAddrDiff()` | code units |
| `public int hashCode()` | the delta |
| `public boolean equals(Object)` | the delta |

## com.v7878.dex.immutable.debug.LineNumber

`public final class LineNumber extends DebugItem`

Invariant: the line is absolute; only negatives are rejected
(`IllegalArgumentException`), so 0 is accepted.

**Methods**

| Signature | Note |
|---|---|
| `public static LineNumber of(int line)` | absolute, not a delta |
| `public int getLine()` | |
| `public int hashCode()` | the line |
| `public boolean equals(Object)` | the line |

## com.v7878.dex.immutable.debug.StartLocal

`public final class StartLocal extends DebugItem`

Invariant: the register must be 0..65535 (`IllegalArgumentException` for anything else);
name, type and signature are independently nullable, and a non-null signature selects the extended
opcode. A register that is already live is not tracked.

**Methods**

| Signature | Note |
|---|---|
| `public static StartLocal of(int register, String name, TypeId type, String signature)` | |
| `public int getRegister()` | |
| `public String getName()` | nullable |
| `public TypeId getType()` | nullable |
| `public String getSignature()` | nullable |
| `public int hashCode()` | all four components |
| `public boolean equals(Object)` | all four components |

## com.v7878.dex.immutable.debug.EndLocal

`public final class EndLocal extends DebugItem`

Invariant: register 0..65535; only the register is named, so an unmatched end is not rejected.

**Methods**

| Signature | Note |
|---|---|
| `public static EndLocal of(int register)` | |
| `public int getRegister()` | |
| `public int hashCode()` | the register |
| `public boolean equals(Object)` | the register |

## com.v7878.dex.immutable.debug.RestartLocal

`public final class RestartLocal extends DebugItem`

Invariant: name, type and signature come from the matching `StartLocal`; an unmatched restart is
written as-is.

**Methods**

| Signature | Note |
|---|---|
| `public static RestartLocal of(int register)` | |
| `public int getRegister()` | |
| `public int hashCode()` | the register |
| `public boolean equals(Object)` | the register |

## com.v7878.dex.immutable.debug.SetFile

`public final class SetFile extends DebugItem`

Invariant: applies to every following position; null means no file and is written as 0.

**Methods**

| Signature | Note |
|---|---|
| `public static SetFile of(String name)` | null allowed |
| `public String getName()` | nullable |
| `public int hashCode()` | 0 when the name is null |
| `public boolean equals(Object)` | null equals null |

## com.v7878.dex.immutable.debug.SetPrologueEnd

`public final class SetPrologueEnd extends DebugItem`

Invariant: a single instance; placing it twice yields two opcodes.

**Fields** — `public static final SetPrologueEnd`: `INSTANCE`

**Constructors** — `private SetPrologueEnd()`

**Methods**

| Signature | Note |
|---|---|
| `public int hashCode()` | always 0 |
| `public boolean equals(Object)` | any instance |

## com.v7878.dex.immutable.debug.SetEpilogueBegin

`public final class SetEpilogueBegin extends DebugItem`

Invariant: a different class from `SetPrologueEnd`, so the two are never equal.

**Fields** — `public static final SetEpilogueBegin`: `INSTANCE`

**Constructors** — `private SetEpilogueBegin()`

**Methods**

| Signature | Note |
|---|---|
| `public int hashCode()` | always 0 |
| `public boolean equals(Object)` | any instance |

## Example

```java
List<DebugItem> debug = List.of(
        LineNumber.of(14),                          // address 0 is line 14
        SetPrologueEnd.INSTANCE,
        AdvancePC.of(2),                            // cursor 2
        StartLocal.of(1, "total", TypeId.I, null),  // v1 live from address 2
        LineNumber.of(15),                          // address 2 is line 15
        AdvancePC.of(3),                            // cursor 5
        LineNumber.of(16));
MethodImplementation body = MethodImplementation.of(2, code, List.of(), debug);
System.out.println(body.getDebugItems().size());   // 7, stored order kept
```

Up: [SKILL.md](../SKILL.md)
