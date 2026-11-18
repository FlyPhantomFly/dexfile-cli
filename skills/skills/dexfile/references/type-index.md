# Type index

130 exported types with the file that documents each one; nested types are listed by owner at the end.

## dex - 17

| Type | File | Role |
|---|---|---|
| AccessFlag | constants-flags.md | flags, masks |
| AnnotationVisibility | constants-flags.md | build, runtime, system |
| DexConstants | constants-flags.md | numeric vocabulary |
| DexIO | io.md | read/write entry |
| DexOffsets | io-layout.md | layout constants |
| DexOptions | io-options.md | shared option base |
| DexVersion | constants-versions.md | versions and API |
| Format | opcode-numbers.md | width, slots, payloads |
| Internal | architecture.md | factory marker |
| MethodHandleType | constants-versions.md | nine handle kinds |
| Opcode | opcodes.md | instruction constants |
| Opcodes | opcode-numbers.md | opcode numbers |
| ReadOptions | io-options.md | parse switches |
| ReferenceType | constants-versions.md | nine reference kinds |
| ValueType | constants-versions.md | value tags |
| VerificationError | constants-versions.md | odex verification-failure kind |
| WriteOptions | io-options.md | output options |

## dex.immutable - 20

| Type | File | Role |
|---|---|---|
| Annotatable | values-annotations.md | annotation carrier |
| CommonAnnotation | values-annotations.md | shared annotation view |
| Annotation | values-annotations.md | visibility and elements |
| AnnotationElement | values-annotations.md | name and value |
| CallSiteId | model-ids.md | call-site identity |
| ClassDef | model-types.md | one class definition |
| Dex | model-types.md | class container |
| ExceptionHandler | model-bodies.md | catch entry |
| MemberDef | model-types.md | definition base |
| FieldDef | model-types.md | field definition |
| MemberId | model-ids.md | identity base |
| FieldId | model-ids.md | field identity |
| MethodDef | model-types.md | method definition |
| MethodHandleId | model-ids.md | handle identity |
| MethodId | model-ids.md | method identity |
| MethodImplementation | model-bodies.md | method body |
| Parameter | model-types.md | type and name |
| ProtoId | model-ids.md | return and params |
| TryBlock | model-bodies.md | protected range |
| TypeId | model-ids.md | type descriptor |

## dex.immutable.value - 19

| Type | File | Role |
|---|---|---|
| EncodedValue | values.md | value base |
| EncodedAnnotation | values.md | nested annotation |
| EncodedArray | values.md | value array |
| EncodedBoolean | values.md | TRUE and FALSE |
| EncodedByte | values.md | cached byte |
| EncodedChar | values.md | char value |
| EncodedDouble | values.md | double by bits |
| EncodedEnum | values.md | enum reference |
| EncodedField | values.md | field reference |
| EncodedFloat | values.md | float by bits |
| EncodedInt | values.md | cached int |
| EncodedLong | values.md | cached long |
| EncodedMethod | values.md | method reference |
| EncodedMethodHandle | values.md | handle reference |
| EncodedMethodType | values.md | prototype reference |
| EncodedNull | values.md | singleton null |
| EncodedShort | values.md | short value |
| EncodedString | values.md | shared empty string |
| EncodedType | values.md | type reference |

## dex.immutable.debug - 9

| Type | File | Role |
|---|---|---|
| DebugItem | values-debug.md | event base |
| AdvancePC | values-debug.md | advance address |
| EndLocal | values-debug.md | end local scope |
| LineNumber | values-debug.md | source line |
| RestartLocal | values-debug.md | restart local |
| SetEpilogueBegin | values-debug.md | epilogue marker |
| SetFile | values-debug.md | source file |
| SetPrologueEnd | values-debug.md | prologue marker |
| StartLocal | values-debug.md | start local |

## dex.immutable.bytecode - 25

| Type | File | Role |
|---|---|---|
| Instruction | instructions.md | carrier base |
| ArrayPayload | instructions.md | array payload |
| InstructionN0t | instructions.md | 10t/20t/30t |
| InstructionN0x | instructions.md | 10x no operands |
| InstructionN0ci | instructions.md | 20bc/40sc verification error |
| InstructionN1c | instructions.md | 21c/31c/41c |
| InstructionN1i | instructions.md | 11n/21s/31i |
| InstructionN1l | instructions.md | 21lh/51l |
| InstructionN1p | instructions.md | 11p legacy index |
| InstructionN1t | instructions.md | 21t/31t |
| InstructionN1x | instructions.md | 11x one register |
| InstructionN2c | instructions.md | 22c/52c |
| InstructionN2i | instructions.md | 22b/22s |
| InstructionN2t | instructions.md | 22t |
| InstructionN2x | instructions.md | 12x/22x/32x |
| InstructionN3x | instructions.md | 23x three registers |
| InstructionNrc | instructions.md | 3rc/5rc range |
| InstructionNrcc | instructions.md | 4rcc |
| InstructionNv4c | instructions.md | 34c four registers |
| InstructionNv5c | instructions.md | 35c five registers |
| InstructionNv5cc | instructions.md | 45cc |
| InstructionRaw0c | instructions.md | raw reference |
| InstructionRaw0x | instructions.md | raw opcode |
| SwitchElement | instructions.md | key-target pair |
| SwitchPayload | instructions.md | switch payload |

## dex.immutable.bytecode.iface - 18

| Type | File | Role |
|---|---|---|
| InstructionI | instruction-shapes.md | root shape |
| BranchOffsetInstruction | instruction-shapes.md | branch offset |
| IndexInstruction | instruction-shapes.md | pool index |
| LiteralInstruction | instruction-shapes.md | 32-bit literal |
| OneRegisterInstruction | instruction-shapes.md | one register |
| VariableRegisterInstruction | instruction-shapes.md | register array |
| PayloadInstruction | instruction-shapes.md | payload marker |
| RawInstruction | instruction-shapes.md | raw opcode access |
| RegisterRangeInstruction | instruction-shapes.md | range and count |
| SingleReferenceInstruction | instruction-shapes.md | one reference |
| DualReferenceInstruction | instruction-shapes.md | two references |
| SwitchPayloadInstruction | instruction-shapes.md | switch shape |
| ArrayPayloadInstruction | instruction-shapes.md | array shape |
| TwoRegisterInstruction | instruction-shapes.md | two registers |
| ThreeRegisterInstruction | instruction-shapes.md | three registers |
| VariableFourRegisterInstruction | instruction-shapes.md | four registers |
| VariableFiveRegisterInstruction | instruction-shapes.md | five registers |
| WideLiteralInstruction | instruction-shapes.md | 64-bit literal |

## dex.builder - 4

| Type | File | Role |
|---|---|---|
| ClassBuilder | builder-class.md | class builder |
| CodeBuilder | code-builder.md | bytecode emitter |
| FieldBuilder | builder-class.md | field builder |
| MethodBuilder | builder-class.md | method builder |

## dex.rewriter - 2

| Type | File | Role |
|---|---|---|
| DexRewriter | rewriter.md | reference visitor |
| LegacyRewriter | rewriter.md | legacy conversion |

## dex.analysis - 9

| Type | File | Role |
|---|---|---|
| AnalysisException | analysis-dataflow.md | analysis failure |
| AnalysisOptions | analysis-dataflow.md | analysis settings |
| AnalyzedMethod | analysis-dataflow.md | dataflow result |
| ClassPath | analysis-classpath.md | resolution and dispatch |
| Position | analysis-dataflow.md | analysed instruction |
| Register | analysis-registers.md | register value |
| RegisterLine | analysis-registers.md | register file |
| TypeInfo | analysis-types.md | type and depth |
| TypeResolver | analysis-types.md | assignability |

## collections - 4

| Type | File | Role |
|---|---|---|
| IntMap | collections.md | sorted int map |
| IntSet | collections.md | sorted int set |
| LongMap | collections.md | sorted long map |
| LongSet | collections.md | sorted long set |

## dex.smali - 3

| Type | File | Role |
|---|---|---|
| SmaliIO | smali.md | smali parser and printer entry |
| SmaliOptions | smali.md | printer options |
| SmaliSyntaxException | smali.md | parse error with position |

## Nested types - 40

| File | Nested |
|---|---|
| collections.md | IntMap.Entry, LongMap.Entry |
| io.md | DexIO.DexMapEntry, DexIO.ClassHeader, DexIO.DexReaderCache, DexIO.InvalidDexFile, DexIO.NotADexFile |
| io-options.md | WriteOptions.StringFix, WriteOptions.RawFix |
| analysis-dataflow.md | Position.Transition, AnalyzedMethod.ValueUsage |
| analysis-registers.md | Register.Identifier, Register.Undefined, Register.DynamicRegister, Register.Conflict, Register.WidePiece, Register.Constant, Register.ConstantKind, Register.TypedRegister, Register.Primitive, Register.WidePrimitive, Register.Reference, Register.UninitializedRef, RegisterLine.RegisterPair |
| analysis-classpath.md | ClassPath.ResolutionError, ClassPath.InvocationError, ClassPath.Result, ClassPath.ResolvedField, ClassPath.ResolvedMethod, ClassPath.InvokeType, ClassPath.FieldAccess, ClassPath.CallTarget, ClassPath.CallTarget.Method, ClassPath.CallTarget.DefaultConflict |
| code-builder-enums.md | CodeBuilder.Op, CodeBuilder.BinOp, CodeBuilder.UnOp, CodeBuilder.Cmp, CodeBuilder.Test, CodeBuilder.InvokeKind |

Up: [SKILL.md](../SKILL.md)
