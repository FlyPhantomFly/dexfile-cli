# Instruction emitters

Each method appends at the cursor, advances past it and returns `this`; registers and operand widths are
checked, labels resolve at `finish()`.

## com.v7878.dex.builder.CodeBuilder

`public final class CodeBuilder`

Invariant: `fNNx` is the format escape hatch — the method name is the format; a `reg_or_pair` operand is a
pair exactly when `Opcode.isRegPair(index)` says so.

**Methods**

| Signature | Note |
|---|---|
| `public CodeBuilder f10x(Opcode)` |  |
| `public CodeBuilder f12x(Opcode, int, int)` |  |
| `public CodeBuilder f11n(Opcode, int, int)` |  |
| `public CodeBuilder f11p(Opcode, int, int)` | legacy m-const only |
| `public CodeBuilder f11x(Opcode, int)` |  |
| `public CodeBuilder f10t(Opcode, Object)` |  |
| `public CodeBuilder f20bc(Opcode, VerificationError, ReferenceType, Object)` |  |
| `public CodeBuilder f20t(Opcode, Object)` |  |
| `public CodeBuilder f20t_24(Opcode, Object)` |  |
| `public CodeBuilder f22x(Opcode, int, int)` |  |
| `public CodeBuilder f21t(Opcode, int, Object)` |  |
| `public CodeBuilder f21s(Opcode, int, int)` |  |
| `public CodeBuilder f21ih(Opcode, int, int)` |  |
| `public CodeBuilder f21lh(Opcode, int, long)` |  |
| `public CodeBuilder f21c(Opcode, int, Object)` |  |
| `public CodeBuilder f23x(Opcode, int, int, int)` |  |
| `public CodeBuilder f22b(Opcode, int, int, int)` |  |
| `public CodeBuilder f22t(Opcode, int, int, Object)` |  |
| `public CodeBuilder f22s(Opcode, int, int, int)` |  |
| `public CodeBuilder f22c(Opcode, int, int, Object)` |  |
| `public CodeBuilder f30t(Opcode, Object)` |  |
| `public CodeBuilder f32x(Opcode, int, int)` |  |
| `public CodeBuilder f31i(Opcode, int, int)` |  |
| `public CodeBuilder f31t(Opcode, int, Object)` |  |
| `public CodeBuilder f31c(Opcode, int, Object)` |  |
| `public CodeBuilder f34c(Opcode, Object, int...)` |  |
| `public CodeBuilder f35c(Opcode, Object, int...)` |  |
| `public CodeBuilder f35c_or_3rc(Opcode, Opcode, Object, int...)` |  |
| `public CodeBuilder f3rc(Opcode, Object, int, int)` |  |
| `public CodeBuilder f40sc(Opcode, VerificationError, ReferenceType, Object)` |  |
| `public CodeBuilder f41c(Opcode, int, Object)` |  |
| `public CodeBuilder f45cc(Opcode, Object, Object, int...)` |  |
| `public CodeBuilder f45cc_or_4rcc(Opcode, Opcode, Object, Object, int...)` |  |
| `public CodeBuilder f4rcc(Opcode, Object, Object, int, int)` |  |
| `public CodeBuilder f51l(Opcode, int, long)` |  |
| `public CodeBuilder f52c(Opcode, int, int, Object)` |  |
| `public CodeBuilder f5rc(Opcode, Object, int, int)` |  |
| `public CodeBuilder raw(Instruction)` | raw, wrapper, alignment |
| `public CodeBuilder raw(Opcode, short)` |  |
| `public CodeBuilder raw(short)` |  |
| `public CodeBuilder raw_aligned(short)` |  |
| `public CodeBuilder wrapper_raw(short)` |  |
| `public CodeBuilder wrapper_raw_aligned(short)` |  |
| `public CodeBuilder raw_ref(Opcode, ReferenceType, Object)` |  |
| `public CodeBuilder raw_ref(ReferenceType, Object)` |  |
| `public CodeBuilder raw_ref_jumbo(ReferenceType, Object)` |  |
| `public CodeBuilder wrapper_raw_ref(ReferenceType, Object)` |  |
| `public CodeBuilder wrapper_raw_ref_jumbo(ReferenceType, Object)` |  |
| `public CodeBuilder odd_spacer()` | no-op at an even address |
| `public CodeBuilder raw_nop()` | real `nop` |
| `public CodeBuilder nop()` | position only, no instruction |
| `public CodeBuilder raw_move(int, int)` | move |
| `public CodeBuilder raw_move_from16(int, int)` |  |
| `public CodeBuilder raw_move_16(int, int)` |  |
| `public CodeBuilder move(int, int)` |  |
| `public CodeBuilder raw_move_wide(int, int)` |  |
| `public CodeBuilder raw_move_wide_from16(int, int)` |  |
| `public CodeBuilder raw_move_wide_16(int, int)` |  |
| `public CodeBuilder move_wide(int, int)` |  |
| `public CodeBuilder raw_move_object(int, int)` |  |
| `public CodeBuilder raw_move_object_from16(int, int)` |  |
| `public CodeBuilder raw_move_object_16(int, int)` |  |
| `public CodeBuilder move_object(int, int)` |  |
| `public CodeBuilder move_shorty(char, int, int)` |  |
| `public CodeBuilder move_range(String, int, int)` |  |
| `public CodeBuilder move_result(int)` | results, exception, returns |
| `public CodeBuilder move_result_wide(int)` |  |
| `public CodeBuilder move_result_object(int)` |  |
| `public CodeBuilder move_result_shorty(char, int)` |  |
| `public CodeBuilder move_exception(int)` |  |
| `public CodeBuilder return_void()` |  |
| `public CodeBuilder return_(int)` |  |
| `public CodeBuilder return_wide(int)` |  |
| `public CodeBuilder return_object(int)` |  |
| `public CodeBuilder return_shorty(char, int)` |  |
| `public CodeBuilder raw_const_4(int, int)` | constants |
| `public CodeBuilder raw_const_16(int, int)` |  |
| `public CodeBuilder raw_const(int, int)` |  |
| `public CodeBuilder raw_const_high16(int, int)` |  |
| `public CodeBuilder const_(int, int)` |  |
| `public CodeBuilder raw_const_wide_16(int, int)` |  |
| `public CodeBuilder raw_const_wide_32(int, int)` |  |
| `public CodeBuilder raw_const_wide(int, long)` |  |
| `public CodeBuilder raw_const_wide_high16(int, long)` |  |
| `public CodeBuilder const_wide(int, long)` |  |
| `public CodeBuilder raw_const_string(int, String)` |  |
| `public CodeBuilder raw_const_string_jumbo(int, String)` |  |
| `public CodeBuilder const_string(int, String)` |  |
| `public CodeBuilder raw_const_class(int, TypeId)` |  |
| `public CodeBuilder const_class(int, TypeId)` |  |
| `public CodeBuilder const_method_handle(int, MethodHandleId)` |  |
| `public CodeBuilder const_method_type(int, ProtoId)` |  |
| `public CodeBuilder monitor_enter(int)` | objects, arrays, payloads |
| `public CodeBuilder monitor_exit(int)` |  |
| `public CodeBuilder check_cast(int, TypeId)` |  |
| `public CodeBuilder instance_of(int, int, TypeId)` |  |
| `public CodeBuilder array_length(int, int)` |  |
| `public CodeBuilder new_instance(int, TypeId)` |  |
| `public CodeBuilder new_array(int, int, TypeId)` |  |
| `public CodeBuilder filled_new_array(TypeId, int...)` |  |
| `public CodeBuilder filled_new_array_range(TypeId, int, int)` |  |
| `public CodeBuilder fill_array_data_raw(int, int, List<? extends Number>)` |  |
| `public CodeBuilder fill_array_data(int, int, Iterable<? extends Number>)` |  |
| `public CodeBuilder fill_array_data(int, boolean[])` |  |
| `public CodeBuilder fill_array_data(int, byte[])` |  |
| `public CodeBuilder fill_array_data(int, short[])` |  |
| `public CodeBuilder fill_array_data(int, char[])` |  |
| `public CodeBuilder fill_array_data(int, int[])` |  |
| `public CodeBuilder fill_array_data(int, float[])` |  |
| `public CodeBuilder fill_array_data(int, long[])` |  |
| `public CodeBuilder fill_array_data(int, double[])` |  |
| `public CodeBuilder throw_(int)` | branches, switch |
| `public CodeBuilder goto_(Object)` |  |
| `public CodeBuilder raw_goto(Object)` |  |
| `public CodeBuilder raw_goto_16(Object)` |  |
| `public CodeBuilder raw_goto_32(Object)` |  |
| `public CodeBuilder switch_(int, IntMap<?>)` |  |
| `public CodeBuilder switch_(int, Map<Integer, ?>)` |  |
| `public CodeBuilder cmp_kind(Cmp, int, int, int)` | comparisons |
| `public CodeBuilder if_test(Test, int, int, Consumer<CodeBuilder>, Consumer<CodeBuilder>)` |  |
| `public CodeBuilder if_test(Test, int, int, Consumer<CodeBuilder>)` |  |
| `public CodeBuilder if_test(Test, int, int, Object)` |  |
| `public CodeBuilder raw_if_test(Test, int, int, Object)` |  |
| `public CodeBuilder if_testz(Test, int, Consumer<CodeBuilder>, Consumer<CodeBuilder>)` |  |
| `public CodeBuilder if_testz(Test, int, Consumer<CodeBuilder>)` |  |
| `public CodeBuilder if_testz(Test, int, Object)` |  |
| `public CodeBuilder raw_if_testz(Test, int, Object)` |  |
| `public CodeBuilder aop(Op, int, int, int)` | field access |
| `public CodeBuilder iop(Op, int, int, FieldId)` |  |
| `public CodeBuilder sop(Op, int, FieldId)` |  |
| `public CodeBuilder aget(char, int, int, int)` |  |
| `public CodeBuilder aput(char, int, int, int)` |  |
| `public CodeBuilder iget(int, int, FieldId)` |  |
| `public CodeBuilder iput(int, int, FieldId)` |  |
| `public CodeBuilder sget(int, FieldId)` |  |
| `public CodeBuilder sput(int, FieldId)` |  |
| `public CodeBuilder invoke(InvokeKind, MethodId, int...)` | invokes |
| `public CodeBuilder invoke_range(InvokeKind, MethodId, int, int)` |  |
| `public CodeBuilder invoke_range(InvokeKind, MethodId, int)` |  |
| `public CodeBuilder invoke_polymorphic(MethodId, ProtoId, int...)` |  |
| `public CodeBuilder invoke_polymorphic_range(MethodId, ProtoId, int, int)` |  |
| `public CodeBuilder invoke_polymorphic_range(MethodId, ProtoId, int)` |  |
| `public CodeBuilder invoke_custom(CallSiteId, int...)` |  |
| `public CodeBuilder invoke_custom_range(CallSiteId, int, int)` |  |
| `public CodeBuilder invoke_custom_range(CallSiteId, int)` |  |
| `public CodeBuilder unop(UnOp, int, int)` | unary |
| `public CodeBuilder neg(char, int, int)` |  |
| `public CodeBuilder not(char, int, int)` |  |
| `public CodeBuilder cast_numeric(char, int, char, int)` |  |
| `public CodeBuilder raw_binop(BinOp, int, int, int)` | binary |
| `public CodeBuilder raw_binop_2addr(BinOp, int, int)` |  |
| `public CodeBuilder binop(BinOp, int, int, int)` | equal sources: `AND`/`OR` → `move`/`move_wide`, `XOR`/`SUB` → `0` |
| `public CodeBuilder binop_2addr(BinOp, int, int)` |  |
| `public CodeBuilder raw_binop_lit16(BinOp, int, int, int)` |  |
| `public CodeBuilder raw_binop_lit8(BinOp, int, int, int)` |  |
| `public CodeBuilder binop_lit(BinOp, int, int, int)` | literal s32; degenerate `0`/`-1` |
| `public CodeBuilder binop_lit_wide(BinOp, int, int, long)` | literal s64; degenerate `0`/`-1` |

## Choosing a form

- **Constants**: `const_` narrows to `const/4` (`dst` < 16, `s4` value), then `/16`, then `/high16`
  (low 16 bits zero), then `const`; `const_wide` mirrors it with `/16`, `/32`, `/high16`.
- **Jumbo**: `const_string` is always 16-bit; the writer rewrites it to `/jumbo` under `StringFix`
  (`FIX_JUMBO`/`FIX_ALL`; `NONE` fails above 65535). `raw_const_string_jumbo` forces the wide form.
- **`const_class`**: primitives become `sget` of the wrapper `TYPE` field (`Void`, `Boolean`, `Byte`,
  `Short`, `Character`, `Integer`, `Float`, `Long`, `Double`), anything else `const-class`.
- **Literals**: `binop_lit` masks shift distances (`0x1f`/`0x3f`) and rewrites `SUB_INT` as `ADD_INT`
  with a negated literal, then folds degenerate values: identity (`0` for `ADD`/`OR`/`XOR`/shifts,
  `-1` for `AND`) → `move`, `x & 0`/`x * 0` → `0`, `0 - x` → `neg`, `x | -1` → `-1`, `x ^ -1` → `not`;
  `x * -1` → `neg` only for u4 registers, and `dst == src` emits nothing for the identity folds.
  `binop_lit_wide` mirrors it with `move_wide`, `const_wide` and `NEG_LONG`/`NOT_LONG`, and `binop`
  itself gives `move`/`move_wide` for equal-source `AND`/`OR` and `0` for `XOR`/`SUB`. Otherwise prefer
  `lit8`, then `lit16`, else materialise the constant, which needs different registers.
- **Branches**: `goto_`, `if_test`, `if_testz` choose from the final distance — nothing for the next
  position, `/32` for a zero or wide offset, an inverted condition plus `goto` otherwise; `raw_*` keeps
  the requested width.
- **Ranges**: a call keeps the regular form only while all registers are consecutive, so zero arguments
  select the range form with count `0`; the short `*_range` overloads add a receiver unless `STATIC`
  (never for `invoke-custom`).
- **Limits**: `f34c` 0..4 registers, `f35c`/`f45cc` 0..5, `f3rc` count `u8`, `f5rc` count `u16`,
  `f20t_24` 24-bit offsets, `f21ih`/`f21lh` zeroed low 16/48 bits.
- **Payloads**: `fill_array_data` takes a `null` array or iterable as empty and emits an empty
  payload; `fill_array_data_raw` still throws `NullPointerException` (model-invariants.md).
- **Fields**: `aop`/`iop`/`sop` use the `Op` array/instance/static opcode; `aget`/`aput` take the
  element shorty, `iget`/`iput`/`sget`/`sput` read it from the field id.
- **`switch_`**: empty → `nop`, one key `0` → `if_testz(EQ, …)`, dense keys → `packed-switch`, else
  `sparse-switch`; the payload goes behind an `odd_spacer`.
- **Shorties**: `neg`/`not`/`cast_numeric` expand `B`/`S`/`C` (and `Z` in `not`) into two
  instructions; `move_shorty`, `move_range`, `move_result_shorty`, `return_shorty` dispatch on the shorty
  and emit nothing for `V`.

Up: [SKILL.md](../SKILL.md)
