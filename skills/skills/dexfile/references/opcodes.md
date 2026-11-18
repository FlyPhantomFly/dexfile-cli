Up: [SKILL.md](../SKILL.md)

# Opcode

`Opcode` is the instruction catalogue: 351 constants. Each fixes a format, up to two reference kinds
and the behaviour predicates; none fixes a number -
`Opcodes` resolves that per dex version and option set (opcode-numbers.md).

## com.v7878.dex.Opcode

`public enum Opcode`

Invariant: `format()` selects the carrier class, and the carrier rejects every other format with
`IllegalArgumentException` before it reads an operand.

**Fields** — `public static final Opcode`, one row per (format, first reference type) below:

Legend: odex = `odexOnly()`, needs `withOdexInstructions(true)`; expanded = `isExpanded()`, a
dex036 /jumbo form for API 14-15; legacy = `isLegacy()`, a dex009/dex013 `m-*` form.

| Format (ref) | Constants |
|---|---|
| 10t | `GOTO` |
| 10x | `NOP` `RETURN_VOID` `RETURN_VOID_BARRIER` (odex) `RETURN_VOID_NO_BARRIER` (odex) |
| 11n | `CONST_4` |
| 11p | `M_CONST_SPECIAL` (dex009/013 only, not odex) `M_CONST_WIDE_SPECIAL` (dex009/013 only, not odex) |
| 11x | `MOVE_RESULT` `MOVE_RESULT_WIDE` `MOVE_RESULT_OBJECT` `MOVE_EXCEPTION` `RETURN` `RETURN_WIDE` `RETURN_OBJECT` `MONITOR_ENTER` `MONITOR_EXIT` `THROW` |
| 12x | `MOVE` `MOVE_WIDE` `MOVE_OBJECT` `ARRAY_LENGTH` `NEG_INT` `NOT_INT` `NEG_LONG` `NOT_LONG` `NEG_FLOAT` `NEG_DOUBLE` `INT_TO_LONG` `INT_TO_FLOAT` `INT_TO_DOUBLE` `LONG_TO_INT` `LONG_TO_FLOAT` `LONG_TO_DOUBLE` `FLOAT_TO_INT` `FLOAT_TO_LONG` `FLOAT_TO_DOUBLE` `DOUBLE_TO_INT` `DOUBLE_TO_LONG` `DOUBLE_TO_FLOAT` `INT_TO_BYTE` `INT_TO_CHAR` `INT_TO_SHORT` `ADD_INT_2ADDR` `SUB_INT_2ADDR` `MUL_INT_2ADDR` `DIV_INT_2ADDR` `REM_INT_2ADDR` `AND_INT_2ADDR` `OR_INT_2ADDR` `XOR_INT_2ADDR` `SHL_INT_2ADDR` `SHR_INT_2ADDR` `USHR_INT_2ADDR` `ADD_LONG_2ADDR` `SUB_LONG_2ADDR` `MUL_LONG_2ADDR` `DIV_LONG_2ADDR` `REM_LONG_2ADDR` `AND_LONG_2ADDR` `OR_LONG_2ADDR` `XOR_LONG_2ADDR` `SHL_LONG_2ADDR` `SHR_LONG_2ADDR` `USHR_LONG_2ADDR` `ADD_FLOAT_2ADDR` `SUB_FLOAT_2ADDR` `MUL_FLOAT_2ADDR` `DIV_FLOAT_2ADDR` `REM_FLOAT_2ADDR` `ADD_DOUBLE_2ADDR` `SUB_DOUBLE_2ADDR` `MUL_DOUBLE_2ADDR` `DIV_DOUBLE_2ADDR` `REM_DOUBLE_2ADDR` `M_NEW_ARRAY_BOOLEAN` (legacy) `M_NEW_ARRAY_BYTE` (legacy) `M_NEW_ARRAY_CHAR` (legacy) `M_NEW_ARRAY_SHORT` (legacy) `M_NEW_ARRAY_INT` (legacy) `M_NEW_ARRAY_LONG` (legacy) `M_NEW_ARRAY_FLOAT` (legacy) `M_NEW_ARRAY_DOUBLE` (legacy) |
| 20bc (odex) | `THROW_VERIFICATION_ERROR` |
| 20t | `GOTO_16` |
| 20t_24 (legacy) | `M_GOTO_24` |
| 21c (STRING) | `CONST_STRING` |
| 21c (TYPE) | `CONST_CLASS` `CHECK_CAST` `NEW_INSTANCE` |
| 21c (FIELD) | `SGET` `SGET_WIDE` `SGET_OBJECT` `SGET_BOOLEAN` `SGET_BYTE` `SGET_CHAR` `SGET_SHORT` `SPUT` `SPUT_WIDE` `SPUT_OBJECT` `SPUT_BOOLEAN` `SPUT_BYTE` `SPUT_CHAR` `SPUT_SHORT` `SGET_VOLATILE` (odex) `SPUT_VOLATILE` (odex) `SGET_WIDE_VOLATILE` (odex) `SPUT_WIDE_VOLATILE` (odex) `SGET_OBJECT_VOLATILE` (odex) `SPUT_OBJECT_VOLATILE` (odex) |
| 21c (PROTO) | `CONST_METHOD_TYPE` |
| 21c (METHOD_HANDLE) | `CONST_METHOD_HANDLE` |
| 21ih | `CONST_HIGH16` |
| 21lh | `CONST_WIDE_HIGH16` |
| 21s | `CONST_16` `CONST_WIDE_16` |
| 21t | `IF_EQZ` `IF_NEZ` `IF_LTZ` `IF_GEZ` `IF_GTZ` `IF_LEZ` `M_PACKED_SWITCH` (legacy) `M_SPARSE_SWITCH` (legacy) |
| 22b | `ADD_INT_LIT8` `RSUB_INT_LIT8` `MUL_INT_LIT8` `DIV_INT_LIT8` `REM_INT_LIT8` `AND_INT_LIT8` `OR_INT_LIT8` `XOR_INT_LIT8` `SHL_INT_LIT8` `SHR_INT_LIT8` `USHR_INT_LIT8` |
| 22c (TYPE) | `INSTANCE_OF` `NEW_ARRAY` `M_NEW_ARRAY` (legacy) |
| 22c (FIELD) | `IGET` `IGET_WIDE` `IGET_OBJECT` `IGET_BOOLEAN` `IGET_BYTE` `IGET_CHAR` `IGET_SHORT` `IPUT` `IPUT_WIDE` `IPUT_OBJECT` `IPUT_BOOLEAN` `IPUT_BYTE` `IPUT_CHAR` `IPUT_SHORT` `IGET_VOLATILE` (odex) `IPUT_VOLATILE` (odex) `IGET_WIDE_VOLATILE` (odex) `IPUT_WIDE_VOLATILE` (odex) `IGET_OBJECT_VOLATILE` (odex) `IPUT_OBJECT_VOLATILE` (odex) |
| 22c (RAW_INDEX, odex) | `IGET_QUICK` `IGET_WIDE_QUICK` `IGET_OBJECT_QUICK` `IPUT_QUICK` `IPUT_WIDE_QUICK` `IPUT_OBJECT_QUICK` `IPUT_BOOLEAN_QUICK` `IPUT_BYTE_QUICK` `IPUT_CHAR_QUICK` `IPUT_SHORT_QUICK` `IGET_BOOLEAN_QUICK` `IGET_BYTE_QUICK` `IGET_CHAR_QUICK` `IGET_SHORT_QUICK` |
| 22s | `ADD_INT_LIT16` `RSUB_INT` `MUL_INT_LIT16` `DIV_INT_LIT16` `REM_INT_LIT16` `AND_INT_LIT16` `OR_INT_LIT16` `XOR_INT_LIT16` |
| 22t | `IF_EQ` `IF_NE` `IF_LT` `IF_GE` `IF_GT` `IF_LE` |
| 22x | `MOVE_FROM16` `MOVE_WIDE_FROM16` `MOVE_OBJECT_FROM16` |
| 23x | `CMPL_FLOAT` `CMPG_FLOAT` `CMPL_DOUBLE` `CMPG_DOUBLE` `CMP_LONG` `AGET` `AGET_WIDE` `AGET_OBJECT` `AGET_BOOLEAN` `AGET_BYTE` `AGET_CHAR` `AGET_SHORT` `APUT` `APUT_WIDE` `APUT_OBJECT` `APUT_BOOLEAN` `APUT_BYTE` `APUT_CHAR` `APUT_SHORT` `ADD_INT` `SUB_INT` `MUL_INT` `DIV_INT` `REM_INT` `AND_INT` `OR_INT` `XOR_INT` `SHL_INT` `SHR_INT` `USHR_INT` `ADD_LONG` `SUB_LONG` `MUL_LONG` `DIV_LONG` `REM_LONG` `AND_LONG` `OR_LONG` `XOR_LONG` `SHL_LONG` `SHR_LONG` `USHR_LONG` `ADD_FLOAT` `SUB_FLOAT` `MUL_FLOAT` `DIV_FLOAT` `REM_FLOAT` `ADD_DOUBLE` `SUB_DOUBLE` `MUL_DOUBLE` `DIV_DOUBLE` `REM_DOUBLE` |
| 30t | `GOTO_32` |
| 31c (STRING) | `CONST_STRING_JUMBO` |
| 31i | `CONST` `CONST_WIDE_32` |
| 31t | `FILL_ARRAY_DATA` `PACKED_SWITCH` `SPARSE_SWITCH` |
| 32x | `MOVE_16` `MOVE_WIDE_16` `MOVE_OBJECT_16` |
| 34c (TYPE, legacy) | `M_FILLED_NEW_ARRAY` |
| 34c (METHOD, legacy) | `M_INVOKE_VIRTUAL` `M_INVOKE_SUPER` `M_INVOKE_DIRECT` `M_INVOKE_STATIC` `M_INVOKE_INTERFACE` `M_INVOKE_DIRECT_EMPTY` (odex) |
| 34c (RAW_INDEX, odex, legacy) | `M_EXECUTE_INLINE` `M_INVOKE_VIRTUAL_QUICK` `M_INVOKE_SUPER_QUICK` |
| 35c (TYPE) | `FILLED_NEW_ARRAY` |
| 35c (METHOD) | `INVOKE_VIRTUAL` `INVOKE_SUPER` `INVOKE_DIRECT` `INVOKE_STATIC` `INVOKE_INTERFACE` `INVOKE_DIRECT_EMPTY` (odex) |
| 35c (CALLSITE) | `INVOKE_CUSTOM` |
| 35c (RAW_INDEX, odex) | `EXECUTE_INLINE` `INVOKE_VIRTUAL_QUICK` `INVOKE_SUPER_QUICK` |
| 3rc (TYPE) | `FILLED_NEW_ARRAY_RANGE` |
| 3rc (METHOD) | `INVOKE_VIRTUAL_RANGE` `INVOKE_SUPER_RANGE` `INVOKE_DIRECT_RANGE` `INVOKE_STATIC_RANGE` `INVOKE_INTERFACE_RANGE` `INVOKE_OBJECT_INIT_RANGE` (odex) |
| 3rc (CALLSITE) | `INVOKE_CUSTOM_RANGE` |
| 3rc (RAW_INDEX, odex) | `EXECUTE_INLINE_RANGE` `INVOKE_VIRTUAL_QUICK_RANGE` `INVOKE_SUPER_QUICK_RANGE` |
| 40sc (/jumbo, expanded, API 14-15, odex) | `THROW_VERIFICATION_ERROR_JUMBO` |
| 41c (TYPE, /jumbo, expanded, API 14-15) | `CONST_CLASS_JUMBO` `CHECK_CAST_JUMBO` `NEW_INSTANCE_JUMBO` |
| 41c (FIELD, /jumbo, expanded, API 14-15) | `SGET_JUMBO` `SGET_WIDE_JUMBO` `SGET_OBJECT_JUMBO` `SGET_BOOLEAN_JUMBO` `SGET_BYTE_JUMBO` `SGET_CHAR_JUMBO` `SGET_SHORT_JUMBO` `SPUT_JUMBO` `SPUT_WIDE_JUMBO` `SPUT_OBJECT_JUMBO` `SPUT_BOOLEAN_JUMBO` `SPUT_BYTE_JUMBO` `SPUT_CHAR_JUMBO` `SPUT_SHORT_JUMBO` `SGET_VOLATILE_JUMBO` (odex) `SGET_WIDE_VOLATILE_JUMBO` (odex) `SGET_OBJECT_VOLATILE_JUMBO` (odex) `SPUT_VOLATILE_JUMBO` (odex) `SPUT_WIDE_VOLATILE_JUMBO` (odex) `SPUT_OBJECT_VOLATILE_JUMBO` (odex) |
| 45cc (METHOD) | `INVOKE_POLYMORPHIC` |
| 4rcc (METHOD) | `INVOKE_POLYMORPHIC_RANGE` |
| 51l | `CONST_WIDE` |
| 52c (TYPE, /jumbo, expanded, API 14-15) | `INSTANCE_OF_JUMBO` `NEW_ARRAY_JUMBO` |
| 52c (FIELD, /jumbo, expanded, API 14-15) | `IGET_JUMBO` `IGET_WIDE_JUMBO` `IGET_OBJECT_JUMBO` `IGET_BOOLEAN_JUMBO` `IGET_BYTE_JUMBO` `IGET_CHAR_JUMBO` `IGET_SHORT_JUMBO` `IPUT_JUMBO` `IPUT_WIDE_JUMBO` `IPUT_OBJECT_JUMBO` `IPUT_BOOLEAN_JUMBO` `IPUT_BYTE_JUMBO` `IPUT_CHAR_JUMBO` `IPUT_SHORT_JUMBO` `IGET_VOLATILE_JUMBO` (odex) `IGET_WIDE_VOLATILE_JUMBO` (odex) `IGET_OBJECT_VOLATILE_JUMBO` (odex) `IPUT_VOLATILE_JUMBO` (odex) `IPUT_WIDE_VOLATILE_JUMBO` (odex) `IPUT_OBJECT_VOLATILE_JUMBO` (odex) |
| 5rc (TYPE, /jumbo, expanded, API 14-15) | `FILLED_NEW_ARRAY_JUMBO` |
| 5rc (METHOD, /jumbo, expanded, API 14-15) | `INVOKE_VIRTUAL_JUMBO` `INVOKE_SUPER_JUMBO` `INVOKE_DIRECT_JUMBO` `INVOKE_STATIC_JUMBO` `INVOKE_INTERFACE_JUMBO` `INVOKE_OBJECT_INIT_JUMBO` (odex) |
| ArrayPayload | `ARRAY_PAYLOAD` |
| PackedSwitchPayload | `PACKED_SWITCH_PAYLOAD` |
| SparseSwitchPayload | `SPARSE_SWITCH_PAYLOAD` |
| MPackedSwitchPayload (legacy) | `M_PACKED_SWITCH_PAYLOAD` |
| MSparseSwitchPayload (legacy) | `M_SPARSE_SWITCH_PAYLOAD` |
| Raw10x (raw) | `RAW` `RAW_ALIGNED` |
| Raw10c | `RAW_REF` |
| Raw20c | `RAW_REF_JUMBO` |
| Wrapper20x (raw) | `WRAPPER_RAW` `WRAPPER_RAW_ALIGNED` |
| Wrapper40ci | `WRAPPER_RAW_REF` `WRAPPER_RAW_REF_JUMBO` |

**Methods**

| Signature | Note |
|---|---|
| `public static Opcode[] values()` | declaration order |
| `public static Opcode valueOf(java.lang.String name)` | Java name; IllegalArgumentException when absent |
| `public static Opcode choose_raw_opcode(Opcode opcode, boolean wrapper)` | the raw or wrapper spelling of a raw family; AssertionError for any other opcode |
| `public String opname()` | DEX spelling: const/4, invoke-virtual/range, filled-new-array/jumbo |
| `public Format format()` | |
| `public int getUnitCount()` | format().getUnitCount(); -1 for the payload opcodes |
| `public ReferenceType getReferenceType1()` | null when the format has no slot, and for all RAW_REF opcodes |
| `public ReferenceType getReferenceType2()` | 45cc and 4rcc only |
| `public ReferenceType referenceType(int index)` | 0 = first, 1 = second, anything else null |
| `public boolean isRegPair(int index)` | operand 0..4 occupies a register pair |
| `public final boolean canThrow()` | every instruction whose execution can raise: array and field access, every invoke form (odex `quick`, `empty` and `execute-inline` included), integer and long `div`/`rem`, monitors, `new-instance`, every `new-array`/`filled-new-array` form (legacy `m-*` included), `fill-array-data`, `check-cast`, `instance-of`, `array-length`, `const-string`, `const-string/jumbo`, `const-class`, `const-method-handle`, `const-method-type`, `throw`, `throw-verification-error*` |
| `public final boolean odexOnly()` | needs withOdexInstructions(true), not an .odex container |
| `public final boolean isLegacy()` | the 26 m-* constants of dex009/dex013; four of them are odex too |
| `public final boolean endsFlow()` | returns, throw, the three GOTO, M_GOTO_24, throw-verification-error* |
| `public final boolean canContinue()` | !endsFlow() |
| `public final boolean canInitializeReference()` | invoke-direct*, invoke-object-init* |
| `public final boolean isInvoke()` | every call family, including the quick and legacy ones |
| `public final boolean setsResult()` | invokes plus every filled-new-array form |
| `public final boolean isReturn()` | RETURN_VOID, RETURN, RETURN_WIDE, RETURN_OBJECT |
| `public final boolean isSwitch()` | packed-switch, sparse-switch and their m- forms |
| `public final boolean isBranch()` | the twelve if-*, GOTO*, M_GOTO_24; switches are not branches |
| `public final boolean isConditionalBranch()` | the twelve if-*, flow continues on a failed test |
| `public final boolean isUnconditionalBranch()` | GOTO, GOTO_16, GOTO_32, M_GOTO_24 |
| `public final boolean hasPayload()` | FILL_ARRAY_DATA and the four switch forms |
| `public final boolean isPayload()` | format().isPayload() |
| `public final boolean isVariableRegister()` | formats 34c, 35c, 3rc, 45cc, 4rcc, 5rc |
| `public final boolean isExpanded()` | a dex036 /jumbo form; needs withExpandedInstructions(true) |
| `public final boolean isRaw()` | the four RAW* and four WRAPPER_* opcodes |
| `public final boolean isRawWrapper()` | the four WRAPPER_* opcodes |
| `public final boolean isAligned()` | must start on a 4-byte boundary; true for the five payload opcodes and `RAW_ALIGNED`, false for `WRAPPER_RAW_ALIGNED` |

`isConditionalBranch()` and `isUnconditionalBranch()` partition `isBranch()`; a switch satisfies
`isSwitch()` and none of the branch predicates. Payload opcodes have `getUnitCount() == -1`.

```java
static void describe(Opcode opcode) {
    System.out.printf("%-24s %-6s units=%d ref1=%s%n",
            opcode.opname(), opcode.format(), opcode.getUnitCount(), opcode.getReferenceType1());
    if (opcode.canThrow()) System.out.println("  may throw");
    if (opcode.endsFlow()) System.out.println("  ends the flow");
}
// describe(Opcode.INVOKE_VIRTUAL); -> "invoke-virtual 35c units=3 ref1=METHOD" and "may throw"
```
