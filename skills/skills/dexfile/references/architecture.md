# Architecture

The library turns a dex byte buffer into an immutable Java object graph and back: parse bytes,
derive a new graph, serialise a graph.

## Package map

| Package | Types | Content |
|---|---|---|
| `com.v7878.dex` | 17 | Entry points, options, versions, opcodes, numeric constants |
| `com.v7878.dex.immutable` | 20 | The model: `Dex`, `ClassDef`, members, identifiers, annotations |
| `com.v7878.dex.immutable.value` | 19 | `EncodedValue` and its 18 final subclasses |
| `com.v7878.dex.immutable.debug` | 9 | `DebugItem` and its 8 final subclasses |
| `com.v7878.dex.immutable.bytecode` | 25 | `Instruction`, 21 carriers, `ArrayPayload`/`SwitchPayload`, `SwitchElement` |
| `com.v7878.dex.immutable.bytecode.iface` | 18 | Instruction shape interfaces |
| `com.v7878.dex.builder` | 4 | `ClassBuilder`, `FieldBuilder`, `MethodBuilder`, `CodeBuilder` |
| `com.v7878.dex.rewriter` | 2 | `DexRewriter`, `LegacyRewriter` |
| `com.v7878.dex.analysis` | 9 | Dataflow and linking |
| `com.v7878.collections` | 4 | `IntMap`, `IntSet`, `LongMap`, `LongSet` |
| `com.v7878.dex.smali` | 3 | `SmaliIO`, `SmaliOptions`, `SmaliSyntaxException` - parser and printer |

A third Gradle module, `:cli`, is an application wrapping `DexIO` and `SmaliIO` into a
command-line tool (dex to smali and back); it is a tool, not library API, and contributes no
types to this map.

## Three layers

```text
   bytes                        object graph                     code
+-------------+          +--------------------------+      +------------------+
| dex, cdex,  | -------> | Dex                      | ---> | MethodImpl       |
| container   |          |  ClassDef                |      |  Instruction     |
| byte[]      | <------- |   FieldDef / MethodDef   | <--- |  TryBlock        |
+-------------+          +--------------------------+      +------------------+
    DexIO                  immutable model                 bytecode package
```

- **Bytes to graph** is `DexIO` plus the read and write options; the writer always returns a fresh
  `byte[]`.
- **Graph to code** is `MethodImplementation`, which owns one method's instructions, try blocks and
  debug items.
- Values that are not code - annotation payloads, field initialisers, call-site arguments - hang
  off the graph as `EncodedValue` instances.

The layers are independent: headers can be read without bodies, and a graph can be built without a
buffer.

## The type graph

```text
Dex
 └─ ClassDef                 (type, flags, superclass, interfaces, source file)
     ├─ FieldDef             (name, type, flags, hiddenapi flags, initial value)
     ├─ MethodDef            (name, return type, parameters, flags, hiddenapi flags)
     │   ├─ Parameter        (type, optional name, annotations)
     │   └─ MethodImplementation
     │       ├─ Instruction  (sealed carrier family, plus payloads)
     │       ├─ TryBlock ── ExceptionHandler
     │       └─ DebugItem
     └─ Annotation ── AnnotationElement ── EncodedValue
```

`Dex` is the class list in your order; `ClassDef` is the only place a class is defined, and
annotations attach through `Annotatable`, not a side table. `MethodImplementation` is `null` for
`abstract` and `native` methods.

## Identity versus definition

| Identity type | Names | Appears in |
|---|---|---|
| `TypeId` | one type descriptor | class types, signatures, instruction operands |
| `ProtoId` | return type + parameter types | method identifiers |
| `FieldId` | declaring class + name + type | field instructions, `EncodedField` |
| `MethodId` | declaring class + name + proto | invoke instructions, method handles |
| `MethodHandleId` | handle kind + member id | handle instructions and values |
| `CallSiteId` | name + handle + method name + proto + arguments | `invoke-custom` |

A `MethodId` may name a method this dex does not define - framework calls are encoded that way.
`MethodDef` is the definition and lives only inside a `ClassDef`; use the `find*` lookups for one
file and `ClassPath` when it may live elsewhere. Identity types are value objects: equality, hash
and ordering come from their components. `TypeId` is the construction exception - `of(String)`
validates the shape, `raw(...)` accepts it as-is - and its ten primitive/Object constants are shared
instances.

## Ordering: `NavigableSet` is dex order

Every model collection that a dex file stores sorted is a `NavigableSet` ordered by `compareTo`:

- methods: direct before virtual, then name, return type, parameter types;
- fields: static before instance, then name, then type;
- annotations by annotation type; annotation elements by element name;
- try blocks by start address, and for equal starts the longer block first;
- switch elements by key.

A `class_data_item` and an annotation set are stored in exactly these orders, so iteration needs no
re-sorting and lookup by key is a binary search. The consequence: `compareTo` and `equals` are not
the same relation, two elements can compare equal while differing in fields that do not participate
in ordering, and such a pair cannot coexist in one set. Natural ordering is also an `equals`
precondition: a set built with a custom `Comparator` through `raw(...)`/`unchecked(...)` makes
`equals`/`hashCode` throw `IllegalArgumentException`: the model requires natural ordering.

`IntMap`/`IntSet` are the primitive-keyed counterpart for addresses and indexes; they are not
`java.util.Map`/`Set` implementations.

## The factory families

- **`of(...)`** - validates, copies iterables into unmodifiable collections, sorts sets, rejects a
  duplicate key with `IllegalArgumentException` (interfaces are deduplicated).
- **`raw(...)`** - `@Internal`; skips copying and uses your collection as the model's storage, so
  you must never modify it afterwards.
- **`unchecked(...)`** - also skips validation; meant for lookup keys, not for definitions.

`model-invariants.md` carries the per-type table of what each factory copies, sorts, rejects and
validates.

## Routes A-E

| Route | Steps | Files |
|---|---|---|
| A read | `DexIO.read` parses a buffer at an offset; `getClasses()`/`findClass` navigate. `readCache` answers header questions before bodies. | `io.md`, `model-types.md` |
| B patch | Rebuild one `MethodDef` with `MethodBuilder.of(method).withCode(...)`, replace it in the class, write. Only that body changes. | `code-builder.md`, `code-emitters.md` |
| C generate | `ClassBuilder.build` produces a `ClassDef`, `Dex.of` wraps it, `DexIO.write` serialises; values come from the values package. | `builder-class.md`, `values.md` |
| D rewrite | Subclass `DexRewriter`, override reference hooks, `rewriteDex`, write; legacy input goes through `LegacyRewriter`. | `rewriter.md` |
| E smali | `SmaliIO.parse` turns text into a `Dex`; `SmaliIO.print*` with `SmaliOptions` turns a model, a class or a method back into text. | `smali.md`, `io.md` |

`DexIO.balanceAll` packs a class list into as many `Dex` objects as the 16-bit pool indexes allow
(a class that cannot fit even alone throws `IllegalStateException` naming the class and the pool);
`writeDexContainer` writes several as one `DEX041` container and rejects other versions with
`IllegalArgumentException`.

## Glossary

| Term | Meaning |
|---|---|
| descriptor | DEX spelling of a type: `I`, `V`, `Ljava/lang/String;`, `[[J` |
| binary name | JVM spelling: `java.lang.String`, `[Ljava.lang.String;` |
| code unit | the 16-bit unit instructions, addresses and try ranges are counted in |
| pool | a per-file table addressed by a 16-bit index; `NO_INDEX` (`-1`) means absent |
| `class_def` | the record defining one class; modelled by `ClassDef` |
| `code_item` | the record holding one method body; modelled by `MethodImplementation` |
| `encoded_value` | self-describing encoding for annotation elements, field values, call-site arguments |
| annotation visibility | build (`0x00`), runtime (`0x01`) or system (`0x02`) |
| direct method | `private`, `static` or a constructor; everything else is virtual |
| register file | the registers a body addresses: `registers = parameters + locals`, where `parameters` counts `this` for a non-static method |
| try block | half-open range `[start, start + unit_count)` with typed handlers and an optional catch-all; blocks must not overlap |
| class path | the class definitions a `ClassPath` can see; resolution maps an id to a member, dispatch also picks the implementation |

Up: [SKILL.md](../SKILL.md)
