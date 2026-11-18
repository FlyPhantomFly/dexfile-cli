# Dataflow: analysing one method

`AnalyzedMethod.analyze` turns a `MethodDef` with a body into an ordered `IntMap<Position>`: per
instruction the register file before and after, the registers read and written, the CFG edges and four
flags. Preconditions: non-null class and def, `getImplementation() != null` (else
`IllegalArgumentException`), non-empty code (else `AnalysisException`). Only the modern standard
opcode set is analysed: an odex, legacy, expanded or raw opcode fails with `AnalysisException` on
the scan stage, dead or not.

The eager factory runs four stages. **Scan** visits every position, dead included, and fills
`accessProto()` and `payload()`, so both are non-null for a dead call or payload instruction.
**Init** builds the CFG edges and register sets; a payload reached by execution fails here. **Resolve**
makes one pass over the structurally reachable positions, caching `resultType()` and
`exceptionType()` and validating the move-result/move-exception producer. **Analyze** is the
register-line fixpoint reading those caches; it ends in `freeze()`.

## Pseudo-registers and `after()`

`inputs()` and `outputs()` may contain numbers outside `[0, registerCount)`: `RESULT_REGISTER` (`-2`)
and `RESULT_REGISTER + 1` (`-1`, the high half of a wide result), written by every `invoke*` and
`filled-new-array*` and read by `move-result*`; `EXCEPTION_REGISTER` (`-3`), read by `move-exception`.
They never appear in `before()`/`after()`, which hold real registers only, so `RegisterLine.at(-2)`
throws `ArrayIndexOutOfBoundsException`. Any other register must satisfy `0 <= reg && reg + count <=
registerCount`.

`before()` merges all executable incoming edges; for an ordinary instruction `after()` copies it and
applies the writes. For `throw` and every `return*` the copy is **not** made, because the instruction
never completes normally: `after()` is a fresh line, undefined in every slot, not the state at exit —
read `before()`. A write to one half of a wide pair turns the other half into a `WidePiece`.

## Inputs, outputs, call proto

Both sets come from the opcode and its operands, not from values: a same-shape replacement must read
and write them. A wide operand contributes two consecutive numbers: the inputs of
`add-long v0, v2, v4` are `{v2, v3, v4, v5}`. An `invoke` inputs the receiver (unless static) and all
parameter registers; `filled-new-array*` inputs its arguments and outputs `RESULT_REGISTER`; a `/2addr`
form inputs dst and src2 and outputs dst. `accessProto()` is the call signature, null for non-call
opcodes: `getProto()` for static, `instanceProto()` with the class first for the virtual kinds, the
call-site proto with `MethodHandle` inserted as the receiver for `invoke-polymorphic*`, the
call-site method proto for `invoke-custom*`, a synthetic proto for `filled-new-array`.

## Flags

`isRuntimeReachable()` is entry or a merge over an executable edge. The value pass proves linked
edges impossible (null receiver or array, incompatible `check-cast`, `fill-array-data` on null, a
branch contradicted by constants) and drops reachability without unlinking; such an edge is still
merged although it cannot be entered, so the result is unobservable. `isStrictNop()` marks an
instruction that changes neither the register contents nor the verifier's knowledge (`const` of zero
into a zero register, a self-`move`, a `goto`/`if`/`switch` to the next instruction, a cast to the
exact same type). `isLossyNop()` also leaves every register content unchanged and cannot
throw, but the verifier knows less afterwards. Two cases: a `check-cast` that cannot fail (a zero
constant — every non-wide constant kind and any reference become one reference type — or a widening
cast to a supertype) and a narrowing `int-to-byte/char/short` of a fitting constant. `describe()`
prints `strict nop` in preference to `lossy nop`.

## Constant-origin queries

All three walk `value_sources`; a source without a literal adds nothing (empty set).

- `constant_values(Register)`: kinds with `isInt()`, `LiteralInstruction.getLiteral()`, frozen `IntSet`
- `wide_constant_values(Register)`: kinds with `isWide()`, `WideLiteralInstruction.getWideLiteral()` or
  `LiteralInstruction.getLiteral()` for `const-wide/16` and `const-wide/32`; frozen `LongSet`.
- `ref_constant_values(Register)`: kinds with `isRef()`, `InstructionN1c.getReference1()`, unmodifiable
  `Set<Object>` mixing null with the `String`, `TypeId`, `ProtoId` or `MethodHandleId` behind
  `const-string`, `const-class`, `const-method-type`, `const-method-handle`.

A non-constant register or a constant of the wrong kind throws `IllegalArgumentException` (an
integral, `wide` or `reference` kind); an `aget*` on a known null array has the kind — `INT` for
`aget`, `BOOL`/`BYTE`/`CHAR`/`SHORT` for the typed narrow flavors, a wide constant pair for
`aget-wide`, `NULL` for `aget-object` — but no literal, so the set is empty.

## Value usages

`value_usages(start, slot)` lists every usage of the value held in register `slot` when the
instruction at address `start` is reached, sorted by instruction address, then register; the
start instruction itself counts when it reads the slot. Only a move transfers the value to
another register (`move-wide` carries the lo+hi pair), so the un-overwritten register is the
only other continuation; a usage is the tracked register in `inputs()`. A merge is passed by
register, a refinement keeps it, a `check-cast` reads the original but writes a new value.
An overwritten register ends normal-edge paths; catch edges carry the state before the
instruction; a register without content (undefined, conflict, wide piece) cannot be used.

`is_used_in_branch(start, slot)` is the register-level form: whether `slot` is read on some
execution path from `start`. Any `inputs()` read counts, so a move is a use of its source;
value copies are not followed; the same overwrite, catch-edge and contentless rules apply.
Both throw `AnalysisException` when no instruction sits at `start` and
`ArrayIndexOutOfBoundsException` when `slot` is not a valid register index.

## com.v7878.dex.analysis.AnalysisOptions

`public final class AnalysisOptions`

Invariant: immutable.

**Constructors** — `private AnalysisOptions(boolean verify)`

**Methods**

| Signature | Note |
|---|---|
| `public static AnalysisOptions defaultOptions()` | verification on |
| `public boolean hasVerification()` | |
| `public AnalysisOptions withVerification(boolean verify)` | copy; receiver unchanged |

Verification off keeps the structural checks and drops the value-type ones: return types, invoke
arguments, field kinds, the super-call rule, cast operand kinds, `if-eq`/`if-ne` comparability
(the `null` constant is comparable with references and zero only; zero stays comparable with
integrals). Contradictions then surface as
`IllegalArgumentException` from the value factories. The move family keeps its operand-kind checks
in every mode: a conflict source is rejected (ART android17 parity) and a lone wide half cannot be
moved by a narrow `move`, so a wide pair is always category-matched — a wide constant half matches
either category — and is broken only into a `WidePiece`; the match is asserted where the analyzer
writes a pair (`RegisterLine.copyWide`).

## com.v7878.dex.analysis.AnalysisException

`public class AnalysisException extends RuntimeException`

Unchecked: the body is not analysable, not verifiable, or the model contradicts itself. Structural
failures stay on with verification off (a bad branch target, an unjoinable handler set, a bad register
index, the odex, legacy, expanded and raw opcode guards) and are always `AnalysisException`; match the type, not
the message.

**Constructors** — `public AnalysisException()`

**Constructors** — `public AnalysisException(String message)`

**Constructors** — `public AnalysisException(String message, Throwable cause)`

**Constructors** — `public AnalysisException(Throwable cause)`

## com.v7878.dex.analysis.AnalyzedMethod

`public final class AnalyzedMethod`

Invariant: one position per instruction, ascending by address, frozen; `getLastOffset()` is the sum of
every `getUnitCount()`.

**Methods**

| Signature | Note |
|---|---|
| `public TypeId getDeclaringClass()` | |
| `public MethodDef getMethod()` | |
| `public IntMap<Position> getPositions()` | keyAt is the address; get returns null, valueAt throws |
| `public int getLastOffset()` | |
| `public ProtoId getCallProto()` | class inserted first unless static |
| `public int getRegisterCount()` | |
| `public String describe()` | |
| `public Set<Register> value_sources(Register reg)` | |
| `public IntSet constant_values(Register reg)` | |
| `public LongSet wide_constant_values(Register reg)` | |
| `public Set<Object> ref_constant_values(Register reg)` | |
| `public List<ValueUsage> value_usages(int start, int slot)` | |
| `public boolean is_used_in_branch(int start, int slot)` | |
| `public static AnalyzedMethod analyze(TypeId declaring_class, MethodDef def, AnalysisOptions options)` | uses `TypeResolver.DEFAULT` |
| `public static AnalyzedMethod analyze(TypeResolver resolver, TypeId declaring_class, MethodDef def, AnalysisOptions options)` | |

`value_sources` returns the unmodifiable set of values the slot can come from: a parameter
(`address == -1`) or a written value. A merged value belongs to its join point's predecessors; a
refinement and `markInitialized` create a `produced == false` identifier at the refining
instruction's address, naming the new value from the checked operand's slot, so the walk descends to
the original producer through the incoming edges; a catch edge contributes the predecessor's
`before()` line, not `after()`; only values of the same full origin are replaced, so aliases stay
equal; `check-cast` reports itself. Passing `Register.Undefined` throws `IllegalArgumentException`.

## com.v7878.dex.analysis.AnalyzedMethod.ValueUsage

`public record AnalyzedMethod.ValueUsage(Position position, int slot)`

One usage of a tracked value: the position reading it and the register read.

**Constructors** — `public ValueUsage(Position position, int slot)`

**Methods**

| Signature | Note |
|---|---|
| `public Position position()` | |
| `public int slot()` | |
| `public Register value()` | `position.before().at(slot)`, possibly a moved or refined descendant of the original |
| `public boolean equals(Object)` | both components |
| `public int hashCode()` | both components |
| `public String toString()` | |

## com.v7878.dex.analysis.Position

`public final class Position`

Invariant: no public constructor; `before()` and `after()` have length `getRegisterCount()`.

**Fields** — `public static final int`: `RESULT_REGISTER` `(-2)` `EXCEPTION_REGISTER` `(-3)`

**Methods**

| Signature | Note |
|---|---|
| `public int getRegisterCount()` | |
| `public <I extends Instruction> I instruction()` | unchecked cast; wrong carrier gives ClassCastException |
| `public Opcode opcode()` | |
| `public PayloadInstruction payload()` | only for `fill-array-data` and switches |
| `public ProtoId accessProto()` | null for non-call opcodes |
| `public TypeId resultType()` | move-result* only; null for another opcode or an unentered position |
| `public TypeInfo exceptionType()` | move-exception only; null for another opcode or an unentered position |
| `public RegisterLine before()` | |
| `public RegisterLine after()` | undefined everywhere for throw and `return*` |
| `public int index()` | |
| `public int address()` | |
| `public int nextAddress()` | |
| `public NavigableSet<Transition> predecessors()` | unmodifiable, Transition order |
| `public NavigableSet<Transition> successors()` | |
| `public IntSet inputs()` | frozen, opcode-derived |
| `public IntSet outputs()` | |
| `public boolean isFirst()` | |
| `public boolean isStructurallyReachable()` | |
| `public boolean isBranchTarget()` | one non-fallthrough predecessor, or more than one |
| `public boolean isRuntimeReachable()` | false on a structurally unreachable position |
| `public boolean isStrictNop()` | |
| `public boolean isLossyNop()` | |
| `public boolean isInsideTryBlock()` | |
| `public String describe()` | |

## com.v7878.dex.analysis.Position.Transition

`public static final class Position.Transition implements Comparable<Transition>`

One CFG edge; instances are created by the analyzer.

**Methods**

| Signature | Note |
|---|---|
| `public int address()` | the other end |
| `public TypeId exception()` | caught type of a catch edge, null on a normal edge |
| `public boolean isCatch()` | non-null exception |
| `public boolean isNormal()` | null exception |
| `public boolean isFallThrough()` | plain sequential execution |
| `public int compareTo(Transition other)` | fallthrough first, then exception (null first), then address |
| `public String toString()` | |
| `public boolean equals(Object obj)` | |
| `public int hashCode()` | |

An edge exists for every branch or switch target, for the fallthrough of every continuing instruction
and for every handler of a try block covering a throwing instruction; a catch-all handler gets
`Ljava/lang/Throwable;`, and a zero-offset branch is a normal edge, not a fallthrough. Edges go
through a `TreeSet`, so equal edges appear once.

## Example

```java
AnalyzedMethod analyzed = AnalyzedMethod.analyze(cls.getType(), method,
        AnalysisOptions.defaultOptions());
for (int i = 0; i < analyzed.getPositions().size(); i++) {
    Position p = analyzed.getPositions().valueAt(i);
    if (p.isStructurallyReachable() && !p.isRuntimeReachable()) {
        System.out.printf("dead %04x %s%n", p.address(), p.instruction());
    }
}
```

Up: [SKILL.md](../SKILL.md)
