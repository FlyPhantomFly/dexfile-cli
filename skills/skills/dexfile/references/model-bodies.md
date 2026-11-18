# Method bodies and exception tables

`MethodImplementation` is the `code_item`: register count, instructions, try blocks, debug items.
`TryBlock` is one try item with its handlers, `ExceptionHandler` one typed `(type, address)` pair.
Instruction addresses are implicit: the running sum of `getUnitCount()`. A `null` collection
argument to `of` is empty (model-invariants.md).

## com.v7878.dex.immutable.MethodImplementation

`public final class MethodImplementation`

Invariant: `0 <= register_count <= 65535`; try blocks may not overlap and, with their handler
addresses, must fit the code; a body is detached from its `MethodDef` and may be shared.

**Constructors** — `private MethodImplementation(int register_count, List<Instruction> instructions, NavigableSet<TryBlock> try_blocks, List<DebugItem> debug_items)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static MethodImplementation raw(int register_count, List<Instruction> instructions, NavigableSet<TryBlock> try_blocks, List<DebugItem> debug_items)` | no copy; set already sorted |
| `public static MethodImplementation of(int register_count, Iterable<Instruction> instructions, Iterable<TryBlock> try_blocks, Iterable<DebugItem> debug_items)` | copies, sorts try blocks; duplicate range → `IllegalArgumentException` |
| `public int getRegisterCount()` | `registers_size`, parameters included |
| `public List<Instruction> getInstructions()` | |
| `public NavigableSet<TryBlock> getTryBlocks()` | |
| `public TryBlock getTryBlock(int address)` | covering block or `null`; end exclusive |
| `public List<DebugItem> getDebugItems()` | stored order, addresses unchecked |
| `public int hashCode()` | |
| `public boolean equals(Object)` | try-block set element by element |
| `public String toString()` | `MethodImplementation{registers=N instructions=N try-blocks=N debug-items=N}` |

## com.v7878.dex.immutable.TryBlock

`public final class TryBlock implements Comparable<TryBlock>`

Invariant: sort order is start ascending, then longer-first; `unit_count` may be `0`; without a
handler and without a catch-all the constructor throws `IllegalArgumentException` (an empty try
block).

**Constructors** — `private TryBlock(int start_address, int unit_count, Integer catch_all_address, List<ExceptionHandler> handlers)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static TryBlock raw(int start_address, int unit_count, Integer catch_all_address, List<ExceptionHandler> handlers)` | no copy |
| `public static TryBlock of(int start_address, int unit_count, Integer catch_all_address, Iterable<ExceptionHandler> handlers)` | copies, order kept |
| `public static TryBlock of(int start_address, int unit_count, Integer catch_all_address, ExceptionHandler... handlers)` | |
| `public int getStartAddress()` | |
| `public int getEndAddress()` | `start + unit_count`, exclusive |
| `public int getUnitCount()` | |
| `public Integer getCatchAllAddress()` | `null` when absent |
| `public List<ExceptionHandler> getHandlers()` | `encoded_catch_handler` order |
| `public int hashCode()` | |
| `public boolean equals(Object)` | same, handler list in order |
| `public String toString()` | `TryBlock{start=0 end=4 handlers=[...] catch-all=5}`; catch-all omitted when `null` |
| `public int compareTo(TryBlock)` | start ascending, then longer-first |

## com.v7878.dex.immutable.ExceptionHandler

`public final class ExceptionHandler`

Invariant: the type is not checked to be a `Throwable`; `of` only rejects a negative address -
`MethodImplementation` bounds it against the code.

**Constructors** — `private ExceptionHandler(TypeId exception_type, int address)`

**Methods**

| Signature | Note |
|---|---|
| `public static ExceptionHandler of(TypeId exception_type, int address)` | `address < 0` → `IllegalArgumentException` |
| `public TypeId getExceptionType()` | |
| `public int getAddress()` | code units |
| `public int hashCode()` | address, type |
| `public boolean equals(Object)` | address, type |
| `public String toString()` | `ExceptionHandler{type=Lfoo; address=3}` |

## Ordering, overlap and adjacency

The constructor walks the sorted set and throws as soon as
`curr.getStartAddress() < prev.getEndAddress()`; `of(...)` sorts first, so only a real overlap fails:

| Blocks | Result |
|---|---|
| `[0,2)` and `[4,6)` | accepted, gap |
| `[0,2)` and `[2,4)` | accepted, adjacent (`2 < 2` is false) |
| `[0,4)` with `[2,6)` or `[0,2)` | `IllegalArgumentException` (out of order / overlap) |
| `[0,4)` twice, different handlers | `of` throws `IllegalArgumentException` (duplicate block); `raw` + a plain `TreeSet` keeps the first |
| `[3,3)` with a catch-all | accepted, covers nothing |

| Check | Exception |
|---|---|
| `register_count` outside `0..65535` | `IllegalArgumentException` |
| `start_address < 0`, `catch_all_address < 0`, overflowed range | `IllegalArgumentException` |
| `unit_count` outside `0..65535` | `IllegalArgumentException` |
| try-block end or handler address beyond the code | `IllegalArgumentException` |

The code size is the running sum of `getUnitCount()`, payload instructions included; an empty try
block starting exactly at the end stays legal. `CodeBuilder.finish()` emits adjacent ranges with a
fully identical handler composition as one block, then splits a range longer than `0xFFFF` units
into parts with the same handlers (code-builder.md).

`MethodDef` additionally requires `register_count >= countFullInputRegisters()`, i.e. parameter
registers plus one for `this` on an instance method.

## Example

```java
TryBlock tryIt = TryBlock.of(0, 2, 4,
        ExceptionHandler.of(TypeId.of("Ljava/lang/IllegalStateException;"), 3));
MethodImplementation body = MethodImplementation.of(1, List.of(
        InstructionN1c.of(Opcode.CONST_STRING, 0, "x"),  // 0, 2 units -> [0,2)
        InstructionN0x.of(Opcode.RETURN_VOID),          // 2
        InstructionN1x.of(Opcode.MOVE_EXCEPTION, 0),    // 3, the typed handler
        InstructionN1x.of(Opcode.THROW, 0)),            // 4, the catch-all
        List.of(tryIt), List.of());

System.out.println(body.getTryBlock(1).getUnitCount()); // 2
System.out.println(body.getTryBlock(2));                // null: [0,2) is half-open
```

Up: [SKILL.md](../SKILL.md)
