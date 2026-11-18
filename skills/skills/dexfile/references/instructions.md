Up: [SKILL.md](../SKILL.md)

# Instructions and payloads

The carrier is chosen by the format of the opcode, never by the mnemonic. Every carrier is immutable,
has a private constructor and one `of(...)` factory taking the `Opcode` first; the format is checked
first, a wrong reference type throws `ClassCastException`, a `null` `NullPointerException`. `getUnitCount()` is the format width (addresses are its running sum);
`hashCode`/`equals` cover the opcode and all operands; `toString` is not parser input.

| Carrier | Formats |
|---|---|
| InstructionN0t | 10t, 20t, 20t_24, 30t |
| InstructionN0x | 10x |
| InstructionN1i | 11n, 21ih, 21s, 31i |
| InstructionN1p | 11p |
| InstructionN1x | 11x |
| InstructionN2x | 12x, 22x, 32x |
| InstructionN0ci | 20bc, 40sc |
| InstructionN1c | 21c, 31c, 41c |
| InstructionN1l | 21lh, 51l |
| InstructionN1t | 21t, 31t |
| InstructionN2i | 22b, 22s |
| InstructionN2c | 22c, 52c |
| InstructionN2t | 22t |
| InstructionN3x | 23x |
| InstructionNv4c | 34c |
| InstructionNv5c | 35c |
| InstructionNrc | 3rc, 5rc |
| InstructionNv5cc | 45cc |
| InstructionNrcc | 4rcc |
| InstructionRaw0x | FormatRaw10x, FormatWrapper20x |
| InstructionRaw0c | FormatRaw10c, FormatRaw20c, FormatWrapper40ci |
| ArrayPayload | ArrayPayload |
| SwitchPayload | PackedSwitchPayload, SparseSwitchPayload, MPackedSwitchPayload, MSparseSwitchPayload |

## com.v7878.dex.immutable.bytecode.Instruction

`public abstract sealed class Instruction implements InstructionI`

Invariant: sealed - the 23 subclasses below (21 carriers and 2 payload classes); `getOpcode()` is
final; `equals` is per subclass.

**Constructors** — `public Instruction(Opcode opcode)`

**Methods**

| Signature |
|---|
| `public final Opcode getOpcode()` |
| `public String getName()` |
| `public abstract String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN0t

`public final class InstructionN0t extends Instruction implements BranchOffsetInstruction`

Invariant: `10t` -128..127, `20t` -32768..32767, `20t_24` -8388608..8388607, `30t` any int.

**Methods**

| Signature |
|---|
| `public static InstructionN0t of(Opcode opcode, int offset)` |
| `public int getBranchOffset()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN0x

`public final class InstructionN0x extends Instruction`

Invariant: `10x` has no operands, so objects with the same opcode are equal.

**Methods**

| Signature |
|---|
| `public static InstructionN0x of(Opcode opcode)` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN0ci

`public final class InstructionN0ci extends Instruction implements SingleReferenceInstruction`

Invariant: both verification-failure formats in one carrier - `20bc` (2 units) and `40sc` (4,
`throw-verification-error/jumbo`); the reference kind is stored per instruction (`TYPE`,
`FIELD` or `METHOD` only, like `InstructionRaw0c`), not on the opcode; the second operand is a
`VerificationError`, so this is no `LiteralInstruction`.

**Methods**

| Signature |
|---|
| `public static InstructionN0ci of(Opcode opcode, VerificationError error, ReferenceType type, Object ref1)` |
| `public VerificationError getError()` |
| `public ReferenceType getReferenceType1()` |
| `public Object getReference1()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN1c

`public final class InstructionN1c extends Instruction implements OneRegisterInstruction, SingleReferenceInstruction`

Invariant: `21c`/`31c` `v0..v255`, `41c` `v0..v65535`; a wrong reference type throws.

**Methods**

| Signature |
|---|
| `public static InstructionN1c of(Opcode opcode, int reg1, Object ref1)` |
| `public int getRegister1()` |
| `public Object getReference1()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN1i

`public final class InstructionN1i extends Instruction implements OneRegisterInstruction, LiteralInstruction`

Invariant: `11n` `v0..v15` -8..7; `21ih` low 16 bits zero; `21s` -32768..32767; `31i` any int; all but `11n` `v0..v255`.

**Methods**

| Signature |
|---|
| `public static InstructionN1i of(Opcode opcode, int reg1, int literal)` |
| `public int getRegister1()` |
| `public int getLiteral()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN1l

`public final class InstructionN1l extends Instruction implements OneRegisterInstruction, WideLiteralInstruction`

Invariant: `v0..v255`, register is the low half of the pair; `21lh` low 48 bits zero; `51l` any long.

**Methods**

| Signature |
|---|
| `public static InstructionN1l of(Opcode opcode, int reg1, long literal)` |
| `public int getRegister1()` |
| `public long getWideLiteral()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN1p

`public final class InstructionN1p extends Instruction implements OneRegisterInstruction, IndexInstruction, LiteralInstruction, WideLiteralInstruction`

Invariant: `11p` only (dex009/013, not odex); `v0..v15`, index 0..15; `equals` compares the index, not the literal.

**Methods**

| Signature |
|---|
| `public static InstructionN1p of(Opcode opcode, int reg1, int index)` |
| `public int getRegister1()` |
| `public int getIndex()` |
| `public int getLiteral()` |
| `public long getWideLiteral()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN1t

`public final class InstructionN1t extends Instruction implements OneRegisterInstruction, BranchOffsetInstruction`

Invariant: `21t` -32768..32767, `31t` any int, both `v0..v255`; `hasPayload()` marks a payload offset.

**Methods**

| Signature |
|---|
| `public static InstructionN1t of(Opcode opcode, int reg1, int offset)` |
| `public int getRegister1()` |
| `public int getBranchOffset()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN1x

`public final class InstructionN1x extends Instruction implements OneRegisterInstruction`

Invariant: `11x`, `v0..v255`; the register is the low half of the pair for `-wide`.

**Methods**

| Signature |
|---|
| `public static InstructionN1x of(Opcode opcode, int reg1)` |
| `public int getRegister1()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN2c

`public final class InstructionN2c extends Instruction implements TwoRegisterInstruction, SingleReferenceInstruction`

Invariant: `22c` both `v0..v15`, `52c` both `v0..v65535`; `FIELD` takes a `FieldId`, `TYPE` a `TypeId`.

**Methods**

| Signature |
|---|
| `public static InstructionN2c of(Opcode opcode, int reg1, int reg2, Object ref1)` |
| `public int getRegister1()` |
| `public int getRegister2()` |
| `public Object getReference1()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN2i

`public final class InstructionN2i extends Instruction implements TwoRegisterInstruction, LiteralInstruction`

Invariant: inverted - `22b` `v0..v255` and -128..127, `22s` `v0..v15` and -32768..32767.

**Methods**

| Signature |
|---|
| `public static InstructionN2i of(Opcode opcode, int reg1, int reg2, int literal)` |
| `public int getRegister1()` |
| `public int getRegister2()` |
| `public int getLiteral()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN2t

`public final class InstructionN2t extends Instruction implements TwoRegisterInstruction, BranchOffsetInstruction`

Invariant: `22t`, both `v0..v15`, offset -32768..32767; the flow continues when the test fails.

**Methods**

| Signature |
|---|
| `public static InstructionN2t of(Opcode opcode, int reg1, int reg2, int offset)` |
| `public int getRegister1()` |
| `public int getRegister2()` |
| `public int getBranchOffset()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN2x

`public final class InstructionN2x extends Instruction implements TwoRegisterInstruction`

Invariant: `12x` both `v0..v15`; `22x` `v0..v255` then `v0..v65535`; `32x` both `v0..v65535`.

**Methods**

| Signature |
|---|
| `public static InstructionN2x of(Opcode opcode, int reg1, int reg2)` |
| `public int getRegister1()` |
| `public int getRegister2()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionN3x

`public final class InstructionN3x extends Instruction implements ThreeRegisterInstruction`

Invariant: `23x`, all three `v0..v255`; a wide operand is a pair from the reported register.

**Methods**

| Signature |
|---|
| `public static InstructionN3x of(Opcode opcode, int reg1, int reg2, int reg3)` |
| `public int getRegister1()` |
| `public int getRegister2()` |
| `public int getRegister3()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionNrc

`public final class InstructionNrc extends Instruction implements RegisterRangeInstruction, SingleReferenceInstruction`

Invariant: `3rc` count 0..255 (`0..4` for `EXECUTE_INLINE_RANGE`), `5rc` 0..65535; start+count `v0..v65535`; `toString` prints an inclusive range - `{v3 .. v4}` for `start=3, count=2`, `{v0 .. v0}` for one register, `{}` for zero.

**Methods**

| Signature |
|---|
| `public static InstructionNrc of(Opcode opcode, int count, int start, Object ref1)` |
| `public int getRegisterCount()` |
| `public int getStartRegister()` |
| `public Object getReference1()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionNrcc

`public final class InstructionNrcc extends Instruction implements RegisterRangeInstruction, DualReferenceInstruction`

Invariant: `4rcc`, count 0..255, same range rule and the same inclusive `toString`; reference 2 is the call proto.

**Methods**

| Signature |
|---|
| `public static InstructionNrcc of(Opcode opcode, int count, int start, Object ref1, Object ref2)` |
| `public int getRegisterCount()` |
| `public int getStartRegister()` |
| `public Object getReference1()` |
| `public Object getReference2()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionNv4c

`public final class InstructionNv4c extends Instruction implements VariableFourRegisterInstruction, SingleReferenceInstruction`

Invariant: `34c` only (dex009/013), count 0..4, each `v0..v15`; unused slots are `0`, ignored by `equals`.

**Methods**

| Signature |
|---|
| `public static InstructionNv4c of(Opcode opcode, int count, int reg1, int reg2, int reg3, int reg4, Object ref1)` |
| `public int getRegisterCount()` |
| `public int getRegister1()` |
| `public int getRegister2()` |
| `public int getRegister3()` |
| `public int getRegister4()` |
| `public Object getReference1()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionNv5c

`public final class InstructionNv5c extends Instruction implements VariableFiveRegisterInstruction, SingleReferenceInstruction`

Invariant: `35c`, count 0..5 (`0..4` for `EXECUTE_INLINE`), each `v0..v15`; an instance call counts the receiver.

**Methods**

| Signature |
|---|
| `public static InstructionNv5c of(Opcode opcode, int count, int reg1, int reg2, int reg3, int reg4, int reg5, Object ref1)` |
| `public int getRegisterCount()` |
| `public int getRegister1()` |
| `public int getRegister2()` |
| `public int getRegister3()` |
| `public int getRegister4()` |
| `public int getRegister5()` |
| `public Object getReference1()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionNv5cc

`public final class InstructionNv5cc extends Instruction implements VariableFiveRegisterInstruction, DualReferenceInstruction`

Invariant: `45cc`, count 0..5, each `v0..v15`; two different protos are two instructions.

**Methods**

| Signature |
|---|
| `public static InstructionNv5cc of(Opcode opcode, int count, int reg1, int reg2, int reg3, int reg4, int reg5, Object ref1, Object ref2)` |
| `public int getRegisterCount()` |
| `public int getRegister1()` |
| `public int getRegister2()` |
| `public int getRegister3()` |
| `public int getRegister4()` |
| `public int getRegister5()` |
| `public Object getReference1()` |
| `public Object getReference2()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.InstructionRaw0x

`public final class InstructionRaw0x extends Instruction implements RawInstruction<InstructionRaw0x>`

Invariant: `FormatRaw10x` 1 unit, `FormatWrapper20x` 2; `short` value, low byte is the original opcode.

**Methods**

| Signature |
|---|
| `public static InstructionRaw0x of(Opcode opcode, short value)` |
| `public short getValue()` |
| `public boolean isAligned()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |
| `public InstructionRaw0x wrapper()` |
| `public InstructionRaw0x raw()` |

## com.v7878.dex.immutable.bytecode.InstructionRaw0c

`public final class InstructionRaw0c extends Instruction implements SingleReferenceInstruction, RawInstruction<InstructionRaw0c>`

Invariant: `FormatRaw10c`/`FormatRaw20c`/`FormatWrapper40ci`; the reference kind is stored here, not on the opcode.

**Methods**

| Signature |
|---|
| `public static InstructionRaw0c of(Opcode opcode, ReferenceType type, Object reference)` |
| `public ReferenceType getReferenceType1()` |
| `public Object getReference1()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |
| `public InstructionRaw0c wrapper()` |
| `public InstructionRaw0c raw()` |

## com.v7878.dex.immutable.bytecode.ArrayPayload

`public final class ArrayPayload extends Instruction implements ArrayPayloadInstruction`

Invariant: width 1/2/4/8 fixes `Byte`/`Short`/`Integer`/`Long`; `getUnitCount()` = `(n * width + 1) / 2 + 4`.

**Methods**

| Signature |
|---|
| `public static ArrayPayload raw(int width, java.util.List<? extends Number> elements)` |
| `public static ArrayPayload of(int width, Iterable<? extends Number> elements)` |
| `public int getElementWidth()` |
| `public java.util.List<? extends Number> getArrayElements()` |
| `public int getUnitCount()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.SwitchPayload

`public final class SwitchPayload extends Instruction implements SwitchPayloadInstruction`

Invariant: four payload opcodes only; packed keys must be consecutive; a duplicate key throws.

**Methods**

| Signature |
|---|
| `public static SwitchPayload raw(Opcode opcode, java.util.NavigableSet<SwitchElement> elements)` |
| `public static SwitchPayload of(Opcode opcode, Iterable<SwitchElement> elements)` |
| `public java.util.NavigableSet<SwitchElement> getSwitchElements()` |
| `public int getUnitCount()` |
| `public int hashCode()` `public boolean equals(Object obj)` `public String toString()` |

## com.v7878.dex.immutable.bytecode.SwitchElement

`public final class SwitchElement implements Comparable<SwitchElement>`

Invariant: `compareTo` uses the key only, `equals` the key and offset; offsets are relative to the switch instruction.

**Methods**

| Signature |
|---|
| `public static SwitchElement of(int key, int offset)` |
| `public int getKey()` |
| `public int getOffset()` |
| `public int hashCode()` |
| `public boolean equals(Object obj)` |
| `public int compareTo(SwitchElement other)` |
| `public String toString()` |

