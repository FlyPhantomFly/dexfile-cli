Up: [SKILL.md](../SKILL.md)

# Instruction shapes

The eighteen interfaces of `com.v7878.dex.immutable.bytecode.iface` describe which operands an
instruction has, independently of the class that stores them; use them to read an instruction, a
concrete carrier to build one. Every interface is sealed, so `instanceof` is exhaustive and nothing
in the library converts between shapes.

```text
InstructionI
├── Instruction (sealed; the carriers of instructions.md)
├── BranchOffsetInstruction   N0t N1t N2t
├── IndexInstruction          N1p
├── LiteralInstruction        N1i N1p N2i
├── WideLiteralInstruction    N1p N1l
├── OneRegisterInstruction ── TwoRegisterInstruction ── ThreeRegisterInstruction
│   └── VariableFourRegisterInstruction ── VariableFiveRegisterInstruction
├── VariableRegisterInstruction ── RegisterRangeInstruction
├── SingleReferenceInstruction  N0ci N1c N2c Nrc Nv4c Nv5c Raw0c
│   └── DualReferenceInstruction  Nv5cc Nrcc
├── RawInstruction<I>         Raw0x Raw0c
└── PayloadInstruction ── SwitchPayloadInstruction, ArrayPayloadInstruction
```

The register shapes form a chain, so check the most specific interface first:
`VariableFiveRegisterInstruction` before `VariableFourRegisterInstruction` before
`ThreeRegisterInstruction`, `VariableRegisterInstruction` before `OneRegisterInstruction`,
`DualReferenceInstruction` before `SingleReferenceInstruction`, `WideLiteralInstruction` before
`LiteralInstruction`, and a payload shape before `PayloadInstruction`. The general shape first loses
operands.

Three numeric operands are distinct: a reference (`SingleReferenceInstruction`) is a resolved
object; an index (`IndexInstruction`) stays a number selecting a fixed constant table; a literal is
the value itself, already shifted for the `high16` forms.

## com.v7878.dex.immutable.bytecode.iface.InstructionI

`public sealed interface InstructionI`

Invariant: the root of every shape, payloads included; `getUnitCount()` asserts `!isPayload()`.

**Methods**

| Signature |
|---|
| `public abstract Opcode getOpcode()` |
| `public abstract String getName()` |
| `public default int getUnitCount()` |

## com.v7878.dex.immutable.bytecode.iface.BranchOffsetInstruction

`public sealed interface BranchOffsetInstruction extends InstructionI`

Invariant: signed code units from the instruction address: `10t` -128..127, `20t`/`21t`/`22t` -32768..32767, `20t_24` -8388608..8388607, `30t`/`31t` any int.

**Methods**

| Signature |
|---|
| `public abstract int getBranchOffset()` |

## com.v7878.dex.immutable.bytecode.iface.IndexInstruction

`public sealed interface IndexInstruction extends InstructionI`

Invariant: `InstructionN1p` only; the index is never resolved into an object.

**Methods**

| Signature |
|---|
| `public abstract int getIndex()` |

## com.v7878.dex.immutable.bytecode.iface.LiteralInstruction

`public sealed interface LiteralInstruction extends InstructionI`

Invariant: signed for `11n`/`21s`/`22b`/`22s`, low 16 bits zero for `21ih`, any int for `31i`; on `11p` it is the table value.

**Methods**

| Signature |
|---|
| `public abstract int getLiteral()` |

## com.v7878.dex.immutable.bytecode.iface.WideLiteralInstruction

`public sealed interface WideLiteralInstruction extends InstructionI`

Invariant: already shifted for `21lh` (low 48 bits zero); on `11p` it is the table `double`.

**Methods**

| Signature |
|---|
| `public abstract long getWideLiteral()` |

## com.v7878.dex.immutable.bytecode.iface.OneRegisterInstruction

`public sealed interface OneRegisterInstruction extends InstructionI`

Invariant: `v0..v15` for `11n`/`11p`/`12x`/`22c`/`22s`/`22t`/`34c`/`35c`/`45cc`, `v0..v255` for `11x`/`21c`/`21ih`/`21lh`/`21s`/`21t`/`22b`/`23x`/`31c`/`31i`/`31t`, `v0..v65535` for `32x`/`41c`/`52c`.

**Methods**

| Signature |
|---|
| `public abstract int getRegister1()` |

## com.v7878.dex.immutable.bytecode.iface.TwoRegisterInstruction

`public sealed interface TwoRegisterInstruction extends OneRegisterInstruction`

Invariant: `22x` mixes widths - narrow first, wide second.

**Methods**

| Signature |
|---|
| `public abstract int getRegister2()` |

## com.v7878.dex.immutable.bytecode.iface.ThreeRegisterInstruction

`public sealed interface ThreeRegisterInstruction extends TwoRegisterInstruction`

Invariant: `23x`, all three `v0..v255`.

**Methods**

| Signature |
|---|
| `public abstract int getRegister3()` |

## com.v7878.dex.immutable.bytecode.iface.VariableRegisterInstruction

`public sealed interface VariableRegisterInstruction extends InstructionI`

Invariant: count 0..4 for `34c` and `execute-inline*`, 0..5 for `35c`/`45cc`, 0..255 for `3rc`/`4rcc`, 0..65535 for `5rc`; `getRegisters()` is a fresh copy.

**Methods**

| Signature |
|---|
| `public abstract int getRegisterCount()` |
| `public abstract int[] getRegisters()` |

## com.v7878.dex.immutable.bytecode.iface.RegisterRangeInstruction

`public sealed interface RegisterRangeInstruction extends VariableRegisterInstruction`

Invariant: start and start+count are `v0..v65535`; a zero count is legal.

**Methods**

| Signature |
|---|
| `public abstract int getStartRegister()` |
| `public default int[] getRegisters()` |

## com.v7878.dex.immutable.bytecode.iface.VariableFourRegisterInstruction

`public sealed interface VariableFourRegisterInstruction extends ThreeRegisterInstruction, VariableRegisterInstruction`

Invariant: `34c`, legacy only; unused registers are stored as `0`.

**Methods**

| Signature |
|---|
| `public abstract int getRegister4()` |
| `public default int[] getRegisters()` |

## com.v7878.dex.immutable.bytecode.iface.VariableFiveRegisterInstruction

`public sealed interface VariableFiveRegisterInstruction extends VariableFourRegisterInstruction`

Invariant: `35c`/`45cc`; the count of an instance call includes the receiver.

**Methods**

| Signature |
|---|
| `public abstract int getRegister5()` |
| `public default int[] getRegisters()` |

## com.v7878.dex.immutable.bytecode.iface.SingleReferenceInstruction

`public sealed interface SingleReferenceInstruction extends InstructionI`

Invariant: `STRING`→String, `TYPE`→TypeId, `FIELD`→FieldId, `METHOD`→MethodId, `PROTO`→ProtoId, `CALLSITE`→CallSiteId, `METHOD_HANDLE`→MethodHandleId, `RAW_INDEX`→Integer; `CLASS_DEF` never on an instruction.

**Methods**

| Signature |
|---|
| `public abstract Object getReference1()` |
| `public default ReferenceType getReferenceType1()` |

## com.v7878.dex.immutable.bytecode.iface.DualReferenceInstruction

`public sealed interface DualReferenceInstruction extends SingleReferenceInstruction`

Invariant: `45cc`/`4rcc` only; both references are validated independently.

**Methods**

| Signature |
|---|
| `public abstract Object getReference2()` |
| `public default ReferenceType getReferenceType2()` |

## com.v7878.dex.immutable.bytecode.iface.RawInstruction

`public sealed interface RawInstruction<I extends Instruction & RawInstruction<I>> extends InstructionI`

Invariant: both return a new object and keep the operands, so `wrapper().raw()` equals the original.

**Methods**

| Signature |
|---|
| `public abstract I wrapper()` |
| `public abstract I raw()` |

## com.v7878.dex.immutable.bytecode.iface.PayloadInstruction

`public sealed interface PayloadInstruction extends InstructionI`

Invariant: -1 units on the opcode; the width comes from the data; the address must be even.

**Methods**

| Signature |
|---|
| `public abstract int getUnitCount()` |

## com.v7878.dex.immutable.bytecode.iface.SwitchPayloadInstruction

`public sealed interface SwitchPayloadInstruction extends PayloadInstruction`

Invariant: elements ascend by key, a key cannot repeat, packed keys must be consecutive.

**Methods**

| Signature |
|---|
| `public abstract java.util.NavigableSet<SwitchElement> getSwitchElements()` |

## com.v7878.dex.immutable.bytecode.iface.ArrayPayloadInstruction

`public sealed interface ArrayPayloadInstruction extends PayloadInstruction`

Invariant: width 1/2/4/8 with `Byte`/`Short`/`Integer`/`Long`; the count is elements, not units.

**Methods**

| Signature |
|---|
| `public abstract int getElementWidth()` |
| `public abstract java.util.List<? extends Number> getArrayElements()` |
| `public default int getElementsCount()` |

