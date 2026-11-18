Up: [SKILL.md](../SKILL.md)

# Rewriter

`DexRewriter` is the traversal engine of the immutable model: for every node it calls one hook,
takes the returned object and puts it into a rebuilt parent. The base class implements the recursion,
so a subclass supplies only the transformation. `LegacyRewriter` converts dex009 and dex013 bodies.

## com.v7878.dex.rewriter.DexRewriter

`public class DexRewriter`

Invariant: hooks never mutate - they return a replacement, and returning the argument keeps a
subtree; `rewriteTypeId` is the only hook with a non-structural default.

**Constructors** — `public DexRewriter()`

**Methods**

| Signature | Note |
|---|---|
| `public Dex rewriteDex(Dex value)` | classes, order kept |
| `public ClassDef rewriteClassDef(ClassDef value)` | type, superclass, interfaces, members, annotations |
| `public TypeId rewriteTypeId(TypeId value)` | identity; the single type hook |
| `public FieldId rewriteFieldId(FieldId value)` | declaring class and field type |
| `public ProtoId rewriteProtoId(ProtoId value)` | return type and parameter types |
| `public MethodId rewriteMethodId(MethodId value)` | declaring class and proto |
| `public MethodHandleId rewriteMethodHandleId(MethodHandleId value)` | member via the id hooks |
| `public CallSiteId rewriteCallSiteId(CallSiteId value)` | handle, proto, extra arguments |
| `public Annotation rewriteAnnotation(Annotation value)` | visibility kept, elements re-sorted |
| `public AnnotationElement rewriteAnnotationElement(AnnotationElement value)` | name kept, value rewritten |
| `public EncodedValue rewriteEncodedValue(EncodedValue value)` | dispatch by value tag |
| `public FieldDef rewriteFieldDef(FieldDef value)` | type, initial value, annotations |
| `public MethodDef rewriteMethodDef(MethodDef value)` | return type, parameters, body, annotations |
| `public MethodImplementation rewriteMethodImplementation(MethodImplementation value)` | instructions, try blocks, debug items |
| `public TypeId rewriteClassDefReference(TypeId value)` | delegates to rewriteTypeId |
| `public Object rewriteReference(ReferenceType type, Object value)` | operand dispatch |
| `public Instruction rewriteInstruction(Instruction value)` | rebuilds the seven reference formats |
| `public TryBlock rewriteTryBlock(TryBlock value)` | addresses kept, handlers rewritten |
| `public ExceptionHandler rewriteExceptionHandler(ExceptionHandler value)` | type rewritten, address kept |
| `public DebugItem rewriteDebugItem(DebugItem value)` | only StartLocal is rebuilt |
| `public Parameter rewriteParameter(Parameter value)` | type and annotations |

Traversal order, with the argument each hook receives (`nullable` = not passed when absent):

```text
rewriteDex(Dex)
└─ per ClassDef, list order: rewriteClassDef(ClassDef)
   ├─ rewriteTypeId(type), rewriteTypeId(superclass, nullable), rewriteTypeId(interfaces)
   ├─ per field, set order: rewriteFieldDef(FieldDef)
   │  ├─ rewriteTypeId(field type), rewriteEncodedValue(initial value, nullable)
   │  └─ rewriteAnnotation(each annotation)
   ├─ per method, set order: rewriteMethodDef(MethodDef)
   │  ├─ rewriteTypeId(return type), rewriteParameter(each parameter)
   │  ├─ rewriteMethodImplementation(body, nullable)
   │  │  ├─ rewriteInstruction(each) -> rewriteReference(kind, value) per operand
   │  │  ├─ rewriteTryBlock(each) -> rewriteExceptionHandler(each) -> rewriteTypeId(caught type)
   │  │  └─ rewriteDebugItem(each)
   │  └─ rewriteAnnotation(each annotation)
   └─ rewriteAnnotation(each class annotation) -> rewriteAnnotationElement -> rewriteEncodedValue
```

Flags, names, the source file, registers, addresses, line numbers and element names are copied.
`rewriteTypeId` never sees a member name, a `const-string` operand or an encoded string, and a
definition bypasses the id hooks, so a rename must change both sides.

`rewriteReference` maps the operand kind to a hook; the base casts, so a hook that returns the wrong
class fails with `ClassCastException` at the instruction factory:

| ReferenceType | Operand | Hook |
|---|---|---|
| TYPE | TypeId | rewriteTypeId |
| FIELD | FieldId | rewriteFieldId |
| METHOD | MethodId | rewriteMethodId |
| PROTO | ProtoId | rewriteProtoId |
| CLASS_DEF | TypeId | rewriteClassDefReference |
| CALLSITE | CallSiteId | rewriteCallSiteId |
| METHOD_HANDLE | MethodHandleId | rewriteMethodHandleId |
| STRING | String | returned unchanged |
| RAW_INDEX | Integer | returned unchanged |

`rewriteInstruction` handles exactly these classes and keeps the opcode, the registers and the unit
count, so addresses and branch offsets stay valid:

| Class | Formats | Rewritten operands |
|---|---|---|
| InstructionN1c | 21c 31c 41c | reference1 |
| InstructionN2c | 22c 52c | reference1 |
| InstructionNv4c | 34c | reference1 |
| InstructionNv5c | 35c | reference1 |
| InstructionNrc | 3rc 5rc | reference1 |
| InstructionNv5cc | 45cc | reference1, reference2 (proto) |
| InstructionNrcc | 4rcc | reference1, reference2 (proto) |

Everything else is returned unchanged, including payloads and raw classes; `InstructionRaw0c`
carries a reference the base never inspects, so raw operands keep the old pool entries.

`rewriteEncodedValue` rebuilds TYPE, FIELD, ENUM, METHOD, METHOD_TYPE and METHOD_HANDLE through
`EncodedType.of`, `EncodedField.of`, `EncodedEnum.of`, `EncodedMethod.of`, `EncodedMethodType.of` and
`EncodedMethodHandle.of`; ARRAY with `EncodedArray.raw` keeping element order; ANNOTATION with
`EncodedAnnotation.raw` from its type and element set, not through `rewriteAnnotation`, because it has
no visibility. Primitive, STRING and NULL values return the same instance.

Every `Dex`, `ClassDef`, `FieldDef`, `MethodDef`, `MethodImplementation`, `Parameter`, `Annotation`,
`AnnotationElement`, `TryBlock` and `ExceptionHandler` comes back as a new instance from its raw
factory; every `List` is a new unmodifiable list in the same order and every `NavigableSet` a new
unmodifiable sorted set re-derived from `compareTo`; ids are whatever the hook returned; instructions,
values and debug items are new only for a rewritten kind.

`rewriteDex` always rebuilds, so a no-op pass returns an `equals` `Dex` that is not `==` the input;
use `equals`, or return the argument from `rewriteClassDef` to skip a class. Sets can shrink: two
methods that become equal on directness, name, return type and parameters collapse, two annotations
of one type collapse (the type is the whole key), two try blocks with the same start and length
collapse - silently, where a model factory would throw `IllegalArgumentException`. The interface list
is not deduplicated, superclass rules are re-checked (so mapping `Ljava/lang/Object;` fails every
class), and `MethodImplementation.raw` re-checks registers and try ranges. The base is stateless and
thread-safe; a subclass that counts or caches is not.

## com.v7878.dex.rewriter.LegacyRewriter

`public class LegacyRewriter extends DexRewriter`

Invariant: it overrides `rewriteMethodImplementation` and nothing else, so definitions, annotations
and call sites keep their references; bodies are re-emitted through `CodeBuilder`, so the output is
equivalent but not byte-identical.

**Constructors** — `public LegacyRewriter()`

**Methods**

| Signature | Note |
|---|---|
| `public MethodImplementation rewriteMethodImplementation(MethodImplementation value)` | rebuilds the body with CodeBuilder |

`DexIO.read` accepts a `dex\n009`/`dex\n013` graph (API -1 or 0 and the Dalvik flag),
`LegacyRewriter` converts the bodies, and the result can be written at a modern version. Conversion
table, left = legacy input:

| Legacy instruction (format) | Replacement |
|---|---|
| m-filled-new-array (34c) | filled-new-array (35c), register 5 = 0, same type |
| m-invoke-virtual, -super, -direct, -static, -interface (34c) | same modern invoke (35c), register 5 = 0 |
| m-execute-inline (34c) | execute-inline (35c), raw index copied, register 5 = 0 |
| m-invoke-direct-empty, m-invoke-virtual-quick, m-invoke-super-quick (34c) | same odex opcode (35c), operand copied |
| m-const/special (11p) | const_ with the constant-table value from getLiteral() |
| m-const-wide/special (11p) | const_wide with getWideLiteral() |
| m-new-array (22c, operand is the component type) | new-array (22c) with array() applied |
| m-new-array-boolean ... -double (12x) | new-array of [Z [B [C [S [I [J [F [D |
| goto (10t), m-goto/24 (20t) | goto_ to the target label, form chosen from the distance |
| m-packed-switch, m-sparse-switch (21t) | switch_ with absolute labels from the switch payload |
| if-eq ... if-le (22t) | if_test to the target label |
| if-eqz ... if-lez (21t) | if_testz to the target label |
| nop (10x), m-packed-switch-payload, m-sparse-switch-payload | nothing is emitted |
| any other instruction | CodeBuilder.raw, a verbatim copy |

The 34c rows keep registers 1-4 and the register count, and set register 5 to `0`, because 34c has
four slots. `m-const/special` emits the table value, not the index: entry `0x8` (`0x3f800000`,
`1.0f`) becomes `const/high16 v0, 0x3f800000`, and `const_` picks the narrowest const form. `goto` and
`if-*` are modern but re-emitted through labels so offsets resolve; `goto/16`, `goto/32` and
unhandled ones take the raw copy.

A body is rebuilt in one pass: an `IntMap<Instruction>` maps every address to its instruction, each
instruction gets `label(absoluteOffset)` and its replacement, a trailing `label(totalSize)` serves
branches to the end, and `CodeBuilder.build(registerCount, ...)` keeps the register file, so
`ins_size` is 0, no hidden `this` appears and `v`/`p` numbering is untouched. Try blocks become
`try_catch`/`try_catch_all` with the original types and addresses. A debug item attaches to the label
of the greatest instruction offset not above its own, with `AdvancePC` only accumulating. `finish()`
resolves labels, appends switch and array payloads, merges adjacent try ranges and re-encodes the
debug list.

`m-const/special` grows to up to three units, `goto` and `m-goto/24` resize by distance and vanish
when the target is the next instruction, a legacy `if` can become a `goto`, an inverted test plus
`goto` or nothing, a switch can change kind (single key `0` becomes `if-eqz`, a consecutive range
becomes packed) and its payload moves to the end, every resized instruction shifts later addresses,
and try ranges with an identical handler composition merge while the debug list is re-encoded. A
body without a legacy instruction can come back equal; compare behaviour, not bytes.

Pool references inside the code are copied, never passed to `rewriteReference`, and handler and local
types are copied directly, so a rename needs its own pass. Converted odex opcodes stay odex
(`execute-inline`, the quick invokes, `invoke-direct-empty`) and need odex-enabled write options and a
compatible API; the library does not translate them.

## Recipe: rename a package

`rewriteTypeId` alone reaches class types, superclasses, interfaces, field and method types,
declaring classes, annotation types and values, caught exceptions, local types and every instruction
operand. `baseType()` strips and `getArrayDepth()` restores array dimensions; the prefix ends with
`/`, so `com.acme.oldstuff` is not captured.

```java
final class PackageRenamer extends DexRewriter {
    private final String from;
    private final String to;

    PackageRenamer(String fromPackage, String toPackage) {
        this.from = "L" + fromPackage.replace('.', '/') + "/";
        this.to = "L" + toPackage.replace('.', '/') + "/";
    }

    @Override
    public TypeId rewriteTypeId(TypeId value) {
        String descriptor = value.baseType().getDescriptor();
        if (!descriptor.startsWith(from)) {
            return value;
        }
        return TypeId.of(to + descriptor.substring(from.length())).array(value.getArrayDepth());
    }
}

Dex renamed = new PackageRenamer("com.acme.old", "com.acme.new").rewriteDex(dex);
```

## Recipe: chain rewriters

Separate passes are the safe composition: each sees a complete model. Order matters - a retarget
that matches the old descriptor must run before the rename.

```java
static Dex rewriteAll(Dex dex, List<? extends DexRewriter> passes) {
    Dex current = dex;
    for (DexRewriter pass : passes) {
        current = pass.rewriteDex(current);
    }
    return current;
}

final class ConnectToOpen extends DexRewriter {
    private final MethodId connect = MethodId.of("Lcom/acme/old/Api;->connect()V");
    private final MethodId open = MethodId.of("Lcom/acme/old/Api;->open()V");

    @Override
    public MethodId rewriteMethodId(MethodId value) {
        return super.rewriteMethodId(connect.equals(value) ? open : value);
    }
}

Dex out = rewriteAll(dex, List.of(new ConnectToOpen(),
        new PackageRenamer("com.acme.old", "com.acme.new")));
```

Inside one hook, `super.rewriteMethodId(target)` applies your own `rewriteTypeId` and
`rewriteProtoId` to the chosen id; another rewriter's hook called directly runs no traversal and
applies no mapping, so use two passes. Adding, removing or reordering instructions is not a visitor
operation: `rewriteInstruction` must keep the unit count - rebuild the body with the code builder.
