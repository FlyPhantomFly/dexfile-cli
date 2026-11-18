# Factories, duplicates and equality

Every model type is built through static factories; constructors are internal. It answers two
questions: is my collection copied, and is this validated?

## Three factory families

| Family | Copies | Validates | Use when |
|---|---|---|---|
| `of(...)` | yes, into unmodifiable collections | yes | the default |
| `@Internal raw(...)` | no, except `ClassDef` interfaces | scalars, flag combinations and element kinds (`void` parameters, reference-only class types); sets trusted; `FieldDef.raw` also enforces the initial-value shorty check | the collections are already sorted, duplicate-free and never mutated |
| `@Internal unchecked(...)` (`MethodDef` only) | no | only `null` arguments and `void` parameters | building a lookup probe |

**A `null` collection argument means an empty collection**, uniformly: every non-`raw` factory,
builder method and `CodeBuilder` payload emitter, `Iterable`/`List` and varargs array alike
(`Dex.of((ClassDef[]) null)`, `setInterfaces(null)`, `Parameter.listOf((TypeId[]) null)`). The
exceptions are the `@Internal raw(...)` factories and `CodeBuilder.fill_array_data_raw`: they still
throw `NullPointerException` and never inspect the contents. A `null` *element* is not affected by
this rule: the member factories still throw `NullPointerException` for it. An empty collection is
not `EncodedNull`: an annotation element gets `EncodedArray.EMPTY` (values-annotations.md), while
`EncodedValue.ofValue(null)` still returns `EncodedNull`.

## Duplicates: `of` rejects, builders replace, `raw` trusts sets

- `of(...)` adds elements one by one, so a second element with the same `compareTo` key - a duplicate
  method or field signature, annotation type or try-block range - throws `IllegalArgumentException`.
  Nothing is dropped silently, not even inside a `NavigableSet` whose custom comparator hides a pair.
- `ClassBuilder`/`FieldBuilder`/`MethodBuilder` never throw for a duplicate: `set*` clears and then
  adds (the first of a colliding pair wins), `with*` removes the existing key and adds the new one
  (the last wins), `without*` removes.
- `raw(...)`/`unchecked(...)` store the collection as it is: whatever the `TreeSet` holds is what
  the model holds. `ClassDef.raw` is the exception for its `interfaces` argument: that list is
  always rebuilt, de-duplicated with first-seen order kept, and made unmodifiable; only its
  `fields`, `methods` and `annotations` sets are adopted. It requires non-null `type`, `interfaces`,
  `fields`, `methods` and `annotations` (`NullPointerException`); `superclass` and `source_file` may
  be null.
- `Dex` is an exception by design: it is an ordered list, so duplicate classes are allowed and
  nothing checks uniqueness (`findClass` returns the first match; `DexIO.read(..., ids)` repeats a
  class for a repeated index).

## The equality contract: `compareTo` is the key, `equals` is the object

| Type | `compareTo` key (dex slot) | `equals`/`hashCode` (full object) |
|---|---|---|
| `FieldDef` | static-ness, name, type | flags, hiddenapi flags, name, type, initial value, annotations |
| `MethodDef` | direct-ness, name, return type, parameter types | flags, hiddenapi flags, name, return type, parameters, implementation, annotations |
| `TryBlock` | start, then longer-first length | start, length, catch-all, handlers |
| `ClassDef` | not `Comparable` | type, flags, superclass, interfaces, source file, fields, methods, annotations |
| `Parameter` | not `Comparable` | name, type, annotations |

1. Two `MethodDef`s with the same signature and direct-ness but different bodies compare equal and
   are not `equals`; the same holds for a field with another initial value and for two annotations of
   one type with different visibility or elements.
2. A `NavigableSet` is keyed by `compareTo`, so it cannot hold both: `TreeSet.add` of the second
   returns `false` and keeps the first. `of` rejects the pair instead, `with*` replaces.
3. `ClassDef.equals` compares the method bodies, field initial values and annotation contents, so it
   is a usable change detector, and equal objects always have equal hashes.
4. Set fields are compared element by element through `equals`, never with `Set.equals`/`contains`
   (which fall back to `compareTo`), and hashed in iteration order; natural order keeps the hash
   consistent with the content.

## Custom comparators are rejected

`of` and the builders only create natural-order sets. A set with a custom `Comparator` can enter the
model solely through `raw(...)`/`unchecked(...)`, and then `equals`/`hashCode` throw
`IllegalArgumentException`: the model requires natural ordering, not two orders that cannot be
compared. Never pass one: the lookup helpers and the writer assume the library's order.

## Debug `toString`

A debug view only, not an API contract; the source marks each one for replacement by the smali
printer. Flags inside these strings come from the `AccessFlag` `formatAccessFlagsFor*` formatter.

| Type | Example |
|---|---|
| `Dex` | `Dex{classes=[Lfoo;, Lbar;]}` - types only, no recursion |
| `ClassDef` | `ClassDef{type=Lfoo; flags=[public final] superclass=Lbar; interfaces=[...] source=Foo.java fields=N methods=N annotations=N}` - optional parts omitted, no inner-class flags |
| `FieldDef` | `FieldDef{flags=[public static] name=f type=I hiddenapi=0x00000000 annotations=N}` |
| `MethodDef` | `MethodDef{flags=[public] name=m proto=()V hiddenapi=0x00000000 annotations=N}` |
| `Parameter` | `Parameter{type=I name=<unnamed> annotations=N}` - `<unnamed>` for a `null` name |
| `Annotation` | `Annotation{visibility=BUILD type=Lfoo; elements=N}` |
| `AnnotationElement` | `AnnotationElement{name=x type=EncodedInt}` |
| `TryBlock` | `TryBlock{start=0 end=4 handlers=[...] catch-all=5}` - catch-all omitted when `null` |
| `ExceptionHandler` | `ExceptionHandler{type=Lfoo; address=3}` |
| `MethodImplementation` | `MethodImplementation{registers=N instructions=N try-blocks=N debug-items=N}` |
| `CallSiteId` | `CallSiteId{name=... handle=... method=... proto=... extras=N}` |

## What throws what

| Check | Type | Exception |
|---|---|---|
| duplicate method signature | `ClassDef` | `IllegalArgumentException` |
| duplicate field signature | `ClassDef` | `IllegalArgumentException` |
| duplicate annotation type | `ClassDef`, `FieldDef`, `MethodDef`, `Parameter` | `IllegalArgumentException` |
| duplicate try-block range | `MethodImplementation` | `IllegalArgumentException` |
| bad class flags | `ClassDef` | `IllegalArgumentException` (mask only) |
| primitive or array class type, superclass or interface | `ClassDef` | `IllegalArgumentException` (the message names the offending type; `null` checked first) |
| interface with another superclass | `ClassDef` | `IllegalArgumentException` |
| `java.lang.Object` with a superclass | `ClassDef` | `IllegalArgumentException` |
| missing superclass | `ClassDef` | `IllegalArgumentException` |
| bad field flags | `FieldDef` | `IllegalArgumentException` (mask plus `final`+`volatile`) |
| initial value on an instance field | `FieldDef` | `IllegalArgumentException` |
| initial value shorty vs field shorty | `FieldDef` | `IllegalArgumentException` (primitive: own kind, reference: any reference kind, `void`: none; both `of` and `raw`) |
| bad method flags | `MethodDef` | `IllegalArgumentException` (mask plus `abstract` with `private`/`static`/`final`/`native`/`synchronized`/`strictfp`) |
| body on an `abstract`/`native` method | `MethodDef` | `IllegalArgumentException` |
| no body on a concrete method | `MethodDef` | `IllegalArgumentException` |
| too few registers for the parameters | `MethodDef` | `IllegalArgumentException` |
| bad `<init>`/`<clinit>` name or flags | `MethodDef` | `IllegalArgumentException`, one per rule (`<init>` static, `<init>` not `void`, `<clinit>` not static, `<clinit>` not `()V`, wrong name, constructor without a body, `<init>` with `final`/`synchronized`/`bridge`) |
| register count, try-block range, address and unit-count violations | `MethodImplementation`, `TryBlock`, `ExceptionHandler` | `IllegalArgumentException` (ranges in model-bodies.md) |
| malformed type descriptor | `TypeId` | `IllegalArgumentException` (type descriptor or shorty) |
| `void` array (`array(1)` on `V`, `ofName("void[]")`) | `TypeId` | `IllegalArgumentException` (a `void` array is not a valid type) |
| malformed proto descriptor | `ProtoId` | `IllegalArgumentException` (not a proto descriptor, or a bad argument or return type) |
| empty proto descriptor (`of("")`) | `ProtoId` | `IllegalArgumentException` (empty descriptor) |
| `void` parameter | `ProtoId`, `MethodDef`, `Parameter` | `IllegalArgumentException` (a `void` parameter is not allowed; `ProtoId` names the index) |
| malformed field or method descriptor | `FieldId`, `MethodId` | `IllegalArgumentException` (not a field or method descriptor) |
| primitive declaring class | `FieldId`, `MethodId` | `IllegalArgumentException` |
| member kind vs handle kind | `MethodHandleId` | `IllegalArgumentException` naming the required class (`NullPointerException` for a `null` kind or member) |
| malformed method-handle descriptor | `MethodHandleId` | `IllegalArgumentException` (not a handle descriptor, or an unknown handle type) |

## Aliasing: `raw` does not copy

`of` returns unmodifiable `List`s and `NavigableSet` wrappers over immutable elements, so an object
built with `of` never changes. `raw` stores your sets and never re-checks them: adding a `FieldDef`
to a set you handed to `ClassDef.raw` changes the class, including its live `getStaticFields()`
view, and a `TreeSet<TryBlock>` handed to `MethodImplementation.raw` may receive an overlapping
block afterwards. The `ClassDef.raw` interface list is the exception - it is copied into a new
unmodifiable, de-duplicated list, so later changes to the caller's list do not reach the class.

| Rule | Why |
|---|---|
| after `of`, keep the result and drop the input | the input is copied |
| after `raw`, treat the object *and* its sets as frozen, and never hand one set to two owners | there is no copy of the sets; a later mutation is visible in both (`ClassDef` interfaces are copied) |

`unchecked` also skips the `ACC_CONSTRUCTOR` normalisation: `MethodDef.unchecked("<init>", …)`
gives `isConstructor() == false`, `raw` adds the flag.

## Example

```java
MethodDef oldBody = MethodDef.of("m", TypeId.V, List.of(), 0x9, 0,
        MethodImplementation.of(1, List.of(InstructionN0x.of(Opcode.NOP)), List.of(),
                List.of()), List.of());
MethodDef newBody = MethodDef.of("m", TypeId.V, List.of(), 0x9, 0,
        MethodImplementation.of(1, List.of(), List.of(), List.of()), List.of());

System.out.println(oldBody.compareTo(newBody));   // 0, same dex slot
System.out.println(oldBody.equals(newBody));      // false, bodies differ
TreeSet<MethodDef> set = new TreeSet<>();
set.add(oldBody);
System.out.println(set.add(newBody));             // false, keeps oldBody
```

Up: [SKILL.md](../SKILL.md)
