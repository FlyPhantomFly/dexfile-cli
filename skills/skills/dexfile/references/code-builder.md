# Method bodies: layout, registers, blocks and handlers

`CodeBuilder` assembles one method body: a linear chain of positions, each carrying a node, mutable in
the middle and resolved by `finish()` into a `MethodImplementation`.

## com.v7878.dex.builder.CodeBuilder

`public final class CodeBuilder`

Invariant: the constructor checks `ins_size <= regs_size` before adding the implicit `this`, so
`newInstance(0, 1, true)` throws; `finish()` is terminal and mutates the chain it reads.

**Methods**

| Signature | Note |
|---|---|
| `public MethodImplementation finish()` | attaches leftovers, merges, wraps |
| `public static MethodImplementation build(int, int, boolean, Consumer<CodeBuilder>)` |  |
| `public static MethodImplementation build(int, int, Consumer<CodeBuilder>)` |  |
| `public static MethodImplementation build(int, Consumer<CodeBuilder>)` |  |
| `public static CodeBuilder newInstance(int, int, boolean)` | adds one register to both counts |
| `public static CodeBuilder newInstance(int, int)` |  |
| `public static CodeBuilder newInstance(int)` |  |
| `public CodeBuilder if_(boolean, Consumer<CodeBuilder>, Consumer<CodeBuilder>)` | one branch |
| `public CodeBuilder if_(boolean, Consumer<CodeBuilder>)` |  |
| `public CodeBuilder commit(Consumer<CodeBuilder>)` |  |
| `public int registers()` | total |
| `public int locals()` | registers minus full parameters |
| `public int parameters()` | without this |
| `public int full_parameters()` | with this |
| `public int v(int)` |  |
| `public int l(int)` |  |
| `public int p(int)` |  |
| `public int this_()` | throws when there is none |
| `public static Object new_label()` |  |
| `public Object current_label()` |  |
| `public CodeBuilder insert_block(Object)` | cursor after the block |
| `public CodeBuilder attach_block(Object)` | cursor at its head |
| `public Object split_block_before(Object)` |  |
| `public Object split_block_after(Object)` |  |
| `public CodeBuilder remove_next_node()` |  |
| `public CodeBuilder remove_prev_node()` |  |
| `public Object head_of_block(Object)` |  |
| `public Object tail_of_block(Object)` |  |
| `public Object next_position(Object)` |  |
| `public Object prev_position(Object)` |  |
| `public CodeBuilder append_position(Object)` | unknown label starts a block |
| `public CodeBuilder label(Object)` | fails if already bound |
| `public CodeBuilder remove_label(Object)` |  |
| `public CodeBuilder replace_label(Object)` |  |
| `public CodeBuilder try_catch(Object, Object, TypeId, Object)` |  |
| `public CodeBuilder try_catch(Object, Object, TypeId)` |  |
| `public CodeBuilder try_catch_all(Object, Object, Object)` |  |
| `public CodeBuilder try_catch_all(Object, Object)` |  |
| `public CodeBuilder try_catch(Object, Object, Map<TypeId, ?>)` |  |
| `public CodeBuilder try_catch(Object, Object, Object, Map<TypeId, ?>)` |  |
| `public CodeBuilder remove_try_catch(Object, Object, TypeId)` |  |
| `public CodeBuilder remove_all_try_catch(Object, Object)` |  |
| `public CodeBuilder generate_lines(boolean)` |  |
| `public CodeBuilder generate_lines()` |  |
| `public CodeBuilder line(Object, int)` |  |
| `public CodeBuilder prologue(Object)` |  |
| `public CodeBuilder epilogue(Object)` |  |
| `public CodeBuilder source(Object, String)` |  |
| `public CodeBuilder local(Object, int, String, TypeId, String)` |  |
| `public CodeBuilder local(Object, int, String, TypeId)` |  |
| `public CodeBuilder end_local(Object, int)` |  |
| `public CodeBuilder restart_local(Object, int)` |  |
| `public CodeBuilder line(int)` |  |
| `public CodeBuilder prologue()` |  |
| `public CodeBuilder epilogue()` |  |
| `public CodeBuilder source(String)` |  |
| `public CodeBuilder local(int, String, TypeId, String)` |  |
| `public CodeBuilder local(int, String, TypeId)` |  |
| `public CodeBuilder end_local(int)` |  |
| `public CodeBuilder restart_local(int)` |  |
| `public CodeBuilder put_metadata(Object)` | zero-size node |
| `public Object get_metadata(Object)` | reads forward |

## The layout model

A **position** is a point between instructions: the one before the first instruction is the block *head*,
the one after the last is the *tail*. A **node** is what follows a position — an instruction, an
unresolved branch, a payload, a metadata value or an empty placeholder; emitters insert after the cursor
and move it past the new node. A **label** is any `Object` naming a position: `new_label()` returns an
unbound one, and the positions returned by `current_label()`, `split_block_*`, `head_of_block`,
`tail_of_block`, `next_position`, `prev_position` and `insert_block` are labels too. A **detached block**
is a chain outside the root chain, created by `append_position` on an unbound label or by
`split_block_*`; leftovers are appended at `finish()` in detachment order, the payload block last.

`nop()`, `put_metadata`, `odd_spacer` on an even address and a `move` between equal registers add a
position but no instruction - the same rule everywhere, so a label can target a point where nothing is
emitted (`raw_nop()` emits a real `nop`). `remove_next_node`/`remove_prev_node` leave placeholders so
labels stay valid; navigation and generation skip them. Register operands are absolute indices into the `v0`-based
register file: every emitter takes a frame-absolute register, never a `p`/`l` number.

## The two-phase `finish()`

Instruction sizes depend on final addresses — a `goto` grows from one to three units, a payload may need
an alignment `nop`, a branch to its own position becomes two instructions — so `finish()` resolves in
passes:

1. **Attach the leftovers**: each detached block is appended in detachment order, then the payload block.
2. **Fix nodes to positions**: a node captures its position, the position gets its offset, the offset
   advances by its units. Lazy nodes (branches, payloads, `odd_spacer`) capture positions here.
3. **Resolve sizes to a fixpoint**: while any `units()` differs from its recorded size, update it and
   shift all following positions; then generate the instructions into an unmodifiable list.
4. **Merge the handlers** into an unmodifiable `NavigableSet<TryBlock>` and **the debug items** into a
   list with `AdvancePC` filled in (below).
5. **Wrap**: `MethodImplementation.raw(registers(), instructions, try_blocks, debug_items)` checks the
   16-bit register count and that the merged try blocks do not overlap.

## Register file

| Call | Meaning | Exception |
|---|---|---|
| `newInstance(regs, ins, add_this)` | add_this adds one to both counts | IndexOutOfBoundsException, value out of range |
| `registers()`, `locals()` | total; total minus input registers | |
| `parameters()`, `full_parameters()` | input registers without / with this | |
| `v(i)`, `l(i)`, `p(i)`, `this_()` | validated index, returned absolute | IndexOutOfBoundsException; this_() throws IllegalArgumentException |

```text
registers  0 ........ locals()-1 | locals() | locals()+1 ..... registers()-1
           |<-- locals -->|  this   |<---- parameters ---->|
l(i)=i   this_()=locals()   p(i)=locals()+1+i   v(i)=i
```

Without `this`, `p(i) = locals() + i` and `this_()` throws. Widths are a second check: the builder only
verifies that an index is inside the file, the instruction factory that it fits the operand field (`u4`
0..15, `u8` 0..255, `u16` 0..65535) and throws `IllegalArgumentException`; for a pair the builder also
requires `reg + 1`, and pair-ness follows `Opcode.isRegPair(index)`.

## Labels, blocks and branches

`label` binds the cursor, `replace_label` rebinds, `remove_label` unbinds, `append_position` moves the
cursor (an unknown label starts a detached block there). `goto_(label)` is lazy: nothing when the target
is the next position, `goto/32` for a zero offset or a distance beyond `s16`, `goto/16` beyond `s8`, and
`goto` otherwise.

`insert_block` splices a detached block after the cursor and leaves the cursor after it; `attach_block`
splices it and leaves the cursor at its head, so the next emitter prepends to it. `split_block_before`
detaches the code from a position and returns the new tail of the left part; `split_block_after` detaches
the code after a position and returns the head of the detached part. Neither moves the cursor.

An unknown label throws `IllegalStateException` everywhere except in `append_position`, which creates
blocks; `append_position(null)` throws `NullPointerException`. A label that is already bound, or a
position that is already present in `label`, and a position in `replace_label`/`remove_label`, throw
`IllegalArgumentException`; bad splicing (attaching or splitting a block) throws it too.

## Exception handlers

Registration only queues an item; the queue is resolved in call order at `finish()`:

1. Both labels become addresses, the smaller one is the start; `start == end` is dropped.
2. All borders are collected and the code is cut into segments between them.
3. Each item applies to every segment it covers: a typed item stores `type -> handler` in insertion
   order, `null` type is the catch-all slot, and a `null` handler is a removal that clears that type or,
   for a catch-all removal, the whole map plus the slot.
4. A duplicate type or a second catch-all throws `IllegalArgumentException`; a later
   removal cannot cancel an earlier conflict.
5. Every segment with a handler becomes a `TryBlock`: runs of adjacent segments with a fully
   identical handler composition - the same handler types in the same order with the same
   addresses, and the same catch-all address (both absent or equal) - coalesce into one block,
   while a gap or any difference keeps the segments separate. The set is ordered by
   `TryBlock.compareTo` (start, then longer-first), so `[0,2)` plus `[1,3)` yields `[0,1)`,
   `[1,2)`, `[2,3)` with the middle segment carrying both handler sets, and `[0,1)` plus `[1,2)`
   with identical handlers yields a single `[0,2)`. A merged range longer than `0xFFFF` units is
   split into consecutive parts with the same handlers: `try_item.insn_count` is a 16-bit field.

## Debug information and metadata

Each directive builds its item immediately (`LineNumber.of` rejects a negative line,
`StartLocal`/`EndLocal`/`RestartLocal` a register outside `0..65535`) and queues `(label, item)`. At
`finish()` the pairs are sorted by address with registration order breaking ties, a gap becomes one
`AdvancePC(gap)` before the first item at the later address, and no `AdvancePC` is emitted at address `0`
or after the last item. `generate_lines(true)` is a switch on the insertion path: every inserted node
records `line(++synthetic_line)`, numbered from `1` per node, so zero-size nodes consume a number and
directives do not.

`put_metadata(value)` inserts a zero-size node and rejects `null`; `get_metadata(label)` returns the value
of the node that *follows* the position, so capture `current_label()` before writing and read it back
through that position; any other position yields `null`.

## Example

```java
MethodImplementation body = CodeBuilder.build(3, 1, false, code -> {
    int arg = code.p(0), sum = code.l(0);
    Object loop = CodeBuilder.new_label();
    code.const_(sum, 0);
    code.label(loop);
    code.binop(CodeBuilder.BinOp.ADD_INT, sum, sum, arg);  // add-int/2addr
    code.if_testz(CodeBuilder.Test.LE, sum, loop);         // if-lez, backwards
    code.return_(sum);
});

System.out.println(body.getRegisterCount());        // 3
System.out.println(body.getInstructions().size());  // 5
```

Up: [SKILL.md](../SKILL.md)
