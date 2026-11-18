---
name: dexfile
description: Use when starting any task with the io.github.v7878:DexFile library (packages com.v7878.dex and com.v7878.dex.smali), which maps the public API, explains the read to write pipeline, and routes you to the part that owns the types you need.
---

# DexFile - API map and pipeline

`io.github.v7878:DexFile` reads and writes Dalvik executable files from Java and exposes their
content as an immutable object model that you can inspect, build, rewrite and analyse. It ships as
two Java modules: `com.v7878.dex` (format, model, instructions, builders, rewriter, analysis,
collections) and `com.v7878.dex.smali` (a smali parser and printer). It covers dex `009`-`041`,
compact dex, dex containers, encoded values, debug information and the full instruction set
including legacy, odex and `/jumbo` forms.

This pack documents the public surface - 130 types in 11 packages - one question per file, with
complete member lists.

## When to use it

- read, inspect, patch, merge, split or write dex, cdex or dex-container bytes;
- walk classes, members, annotations, debug info or instructions as Java objects;
- emit new bytecode or a whole class from scratch;
- rename, retarget or otherwise transform references in an existing file;
- ask dataflow questions (which constant is in this register, is this code reachable) or linking
  questions (which method does this call dispatch to, is this access legal);
- turn smali text into model objects, or print a model, a class or a method back into smali text;

## Pack map

| File | Answers | Read if |
|---|---|---|
| [references/type-index.md](references/type-index.md) | owner file and role of each type | you know a type but not its file |
| [references/architecture.md](references/architecture.md) | packages, layers, routes A-E, glossary | you need the API shape or a term |
| [references/collections.md](references/collections.md) | `IntMap`, `IntSet`, `LongMap`, `LongSet` | you index addresses or registers |
| [references/io.md](references/io.md) | `DexIO`, lazy cache, containers, balancing | you read or write bytes |
| [references/io-options.md](references/io-options.md) | `DexOptions`, `ReadOptions`, `WriteOptions` | parsing or output must change |
| [references/io-layout.md](references/io-layout.md) | `DexOffsets` constants | you need a field offset or item size |
| [references/constants-flags.md](references/constants-flags.md) | `AccessFlag`, `DexConstants`, `AnnotationVisibility` | you need a flag value or mask |
| [references/constants-versions.md](references/constants-versions.md) | `DexVersion`, `ValueType`, `MethodHandleType`, `ReferenceType`, `VerificationError` | version, API level or value tag |
| [references/rewriter.md](references/rewriter.md) | `DexRewriter`, `LegacyRewriter` | references change everywhere, code stays |
| [references/model-types.md](references/model-types.md) | `Dex`, `ClassDef`, members, `Parameter` | you walk or build definitions |
| [references/model-ids.md](references/model-ids.md) | `TypeId`, `ProtoId`, ids, call sites | you construct or compare identifiers |
| [references/model-bodies.md](references/model-bodies.md) | `MethodImplementation`, `TryBlock`, `ExceptionHandler` | you touch a method body |
| [references/model-invariants.md](references/model-invariants.md) | factories, duplicates, throw table, ordering | a factory rejected your input |
| [references/values.md](references/values.md) | `EncodedValue` and 18 value classes | annotation payload or field initial value |
| [references/values-debug.md](references/values-debug.md) | `DebugItem` and 8 debug classes | you add line numbers or locals |
| [references/values-annotations.md](references/values-annotations.md) | `Annotatable`, `Annotation`, `AnnotationElement` | you attach or read annotations |
| [references/smali.md](references/smali.md) | `SmaliIO`, `SmaliOptions`, directives | the input or output is smali text |
| [references/opcodes.md](references/opcodes.md) | `Opcode` constants and predicates | you pick an opcode or ask what one does |
| [references/instructions.md](references/instructions.md) | 21 carriers, 2 payload classes, format table | you build or downcast an instruction |
| [references/instruction-shapes.md](references/instruction-shapes.md) | 18 `iface` interfaces | you read operands generically |
| [references/opcode-numbers.md](references/opcode-numbers.md) | `Format`, `Opcodes` | you need a numeric opcode or instruction width |
| [references/builder-class.md](references/builder-class.md) | `ClassBuilder`, `FieldBuilder`, `MethodBuilder` | you generate a class |
| [references/code-builder.md](references/code-builder.md) | `CodeBuilder` layout, labels, try/catch, debug | you emit a method body |
| [references/code-emitters.md](references/code-emitters.md) | the emitter families | you need the right emit method |
| [references/code-builder-enums.md](references/code-builder-enums.md) | `Op`, `BinOp`, `UnOp`, `Cmp`, `Test`, `InvokeKind` | an emitter takes an enum |
| [references/analysis-dataflow.md](references/analysis-dataflow.md) | `AnalyzedMethod`, `Position`, options | you ask what a method does |
| [references/analysis-registers.md](references/analysis-registers.md) | `Register`, `RegisterLine` | you inspect register values |
| [references/analysis-classpath.md](references/analysis-classpath.md) | `ClassPath` and its 10 nested types | you ask what a call resolves to |
| [references/analysis-types.md](references/analysis-types.md) | `TypeInfo`, `TypeResolver` | assignability is the question |

## Reading order

- **A read.** `DexIO.read` -> `Dex.getClasses()` -> `ClassDef` / `MethodDef`. Start with
  `io.md`; continue in `model-types.md`, and `model-ids.md` for identifiers.
- **B patch one method.** `MethodBuilder.of(method).withCode(...)`, replace it in the class, write.
  `code-builder.md` for the body, `code-emitters.md` for the calls.
- **C generate a class.** `ClassBuilder.build` -> `Dex.of` -> `DexIO.write`. `builder-class.md`,
  then `code-builder.md`; initial values come from `values.md`.
- **D rewrite.** Subclass `DexRewriter`, override the hooks you need, call `rewriteDex`, write.
  `rewriter.md`; the visited tree is in `model-types.md`.
- **E smali.** `SmaliIO.parse` turns text into a `Dex`; `SmaliIO.print*` with `SmaliOptions` turns
  a model, a class or a method back into text. `smali.md` for the directives, the printer options
  and the round-trip contract, `io.md` for the byte write.
- **F analyse.** `AnalyzedMethod.analyze` over a read model, or `ClassPath` for linking answers.
  `analysis-dataflow.md` first, then `analysis-classpath.md`.

## Ground rules

- **Public API only.** The surface is `com.v7878.dex` with `immutable` (including its `value`,
  `debug`, `bytecode` and `bytecode.iface` subpackages), `builder`, `rewriter`, `analysis`, plus
  `com.v7878.collections` and `com.v7878.dex.smali`. Use the factories, not implementation
  classes.
- **The model is immutable.** `Dex`, `ClassDef`, `MethodDef`, `FieldDef`, `TypeId` and the rest
  expose unmodifiable collections and have no setters; a change creates new objects.
- **Three factory families.** `of(...)` validates and copies the collections; `raw(...)` is
  `@Internal`, skips copying and adopts your member sets; `ClassDef.raw` still rebuilds the
  interface list (de-duplicated, first-seen order); `unchecked(...)` also skips validation.
- **Set order is dex order.** Member, annotation, try-block and payload collections are
  `NavigableSet`s ordered by `compareTo`, and those orders are what a dex file requires. Never
  assume insertion order, and never add two elements that compare equal.
- **Versions.** Reading accepts `DEX009` through `DEX041`, including compact dex; writing supports
  modern versions (`DEX035` and later, plus `CDEX001`), so legacy input is converted first.
  `DEX041` is a container, not a code version.
- **Smali printing.** `SmaliIO.print*` with `SmaliOptions` turns a model back into smali text; the
  output re-parses to an equal model for bodies in the canonical layout this library emits (payloads
  at the end behind an alignment `nop`, debug items and try ranges on instruction boundaries),
  anything the label grammar cannot express is printed commented out with a `nop`, raw operands
  print live and re-parse, and `relativeOffsets` output is write-only.
- **Size limits are real.** A dex has 16-bit pool indexes: a section above `DexConstants.MAX_POOL_SIZE`
  (`65534`) fails with `IllegalStateException`; the writer either fixes what it can
  (`WriteOptions.StringFix`) or the build fails, and `DexIO.balance*` spreads a class set.
- **Content, not layout.** The writer re-derives pool order, section offsets, map list, checksum
  and signature, so a round trip preserves content rather than bytes; compare graphs, not arrays.

## Gotchas

- `compareTo` is the dex slot key, `equals` the whole object: two methods with the same signature but
  different bodies compare equal yet are not `equals`. A `NavigableSet` keeps one of them, `of(...)`
  rejects such a pair and `with*` replaces; `equals`/`hashCode` always cover every field.
- A set built with a custom comparator through `raw`/`unchecked` makes `equals`/`hashCode` throw
  `IllegalArgumentException`: the model requires natural ordering.
- Member/annotation collections passed to `raw(...)` become the model's storage, so mutating them
  later changes an immutable object; the `ClassDef` interface list is copied instead (duplicates
  dropped, first-seen order).
- `TypeId.raw(String)` skips validation and takes the leading `[` count as `array_depth`, so a
  malformed descriptor reaches `getDescriptor()`, `getShorty()` and every array query unchecked.
- `IntMap` removal only writes a tombstone; the pair disappears on the next compacting call, while
  `IntSet.remove` shifts its tail at once.
- `indexOfKey`/`indexOf` return `~(insertion point)` for a missing key, so a negative result is
  rarely `-1`.
- `freeze()` returns `this`; `duplicate()` returns `this` when frozen and a writable copy when
  mutable, as does the copy constructor.
- A `const-string` index above 65535 fails unless `WriteOptions.StringFix` is set; the same limit
  drives `balanceAll` and `DEX041` containers.
- Legacy `DEX009`/`DEX013` input can be read but not written; it must pass through
  `LegacyRewriter` first.
- `Dex` is an ordered list, not a set: duplicates are allowed and never checked, `findClass` returns
  the first match, and `DexIO.read(..., ids)` repeats a class for a repeated index.
- `MethodImplementation == null` means `abstract` or `native`, so a loop over instructions must
  skip those methods; opcode numbers come from `Opcodes.of(version, options)`

## Minimal example

```java
import com.v7878.dex.DexIO;
import com.v7878.dex.immutable.ClassDef;
import com.v7878.dex.immutable.Dex;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ListAndWrite {
    public static void main(String[] args) throws Exception {
        Dex dex = DexIO.read(Files.readAllBytes(Path.of(args[0])));
        for (ClassDef type : dex.getClasses())
            for (var method : type.getMethods())
                System.out.println(type.getType() + "->" + method.getName());
        System.out.println(DexIO.write(dex).length + " bytes");
    }
}
```
