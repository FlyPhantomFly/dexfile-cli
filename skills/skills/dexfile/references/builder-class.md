# Class, field and method builders

`ClassBuilder` stages one `ClassDef`; `FieldBuilder` and `MethodBuilder` stage one member. Mutators return
`this`, state stays mutable until `finish()` calls the model factory, `withFlags` is the only eager check.

## com.v7878.dex.builder.ClassBuilder

`public final class ClassBuilder`

Invariant: a staging area — `finish()` is the first place that checks the superclass rules; the collections are live.

**Constructors** — `private ClassBuilder()`

**Methods**

| Signature | Note |
|---|---|
| `public TypeId getType()` |  |
| `public int getAccessFlags()` |  |
| `public TypeId getSuperclass()` |  |
| `public List<TypeId> getInterfaces()` | live |
| `public String getSourceFile()` |  |
| `public NavigableSet<FieldDef> getFields()` | live |
| `public NavigableSet<MethodDef> getMethods()` |  |
| `public NavigableSet<Annotation> getAnnotations()` | live |
| `public static ClassBuilder newInstance()` |  |
| `public ClassDef finish()` | `ClassDef.of` |
| `public static ClassDef build(Consumer<ClassBuilder>)` |  |
| `public static ClassDef build(TypeId, Consumer<ClassBuilder>)` |  |
| `public ClassBuilder if_(boolean, Consumer<ClassBuilder>, Consumer<ClassBuilder>)` | one branch |
| `public ClassBuilder if_(boolean, Consumer<ClassBuilder>)` |  |
| `public ClassBuilder commit(Consumer<ClassBuilder>)` |  |
| `public ClassBuilder of(ClassDef)` | replaces all state |
| `public ClassBuilder withType(TypeId)` |  |
| `public ClassBuilder withFlags(int)` | validates the mask |
| `public ClassBuilder withFlags(AccessFlag...)` |  |
| `public ClassBuilder withSourceFile(String)` | accepts null |
| `public ClassBuilder withSuperclass(TypeId)` | interfaces take `java.lang.Object`, only `java.lang.Object` itself takes null; `ClassDef` validates |
| `public ClassBuilder setInterfaces(Iterable<TypeId>)` |  |
| `public ClassBuilder setInterfaces(TypeId...)` |  |
| `public ClassBuilder withInterfaces(Iterable<TypeId>)` | append, no dedupe |
| `public ClassBuilder withInterfaces(TypeId...)` |  |
| `public ClassBuilder withoutInterfaces(Iterable<TypeId>)` |  |
| `public ClassBuilder withoutInterfaces(TypeId...)` |  |
| `public ClassBuilder setAnnotations(Iterable<Annotation>)` |  |
| `public ClassBuilder setAnnotations(Annotation...)` |  |
| `public ClassBuilder withAnnotations(Iterable<Annotation>)` |  |
| `public ClassBuilder withAnnotations(Annotation...)` |  |
| `public ClassBuilder withoutAnnotations(Iterable<Annotation>)` |  |
| `public ClassBuilder withoutAnnotations(Annotation...)` |  |
| `public ClassBuilder setFields(Iterable<FieldDef>)` |  |
| `public ClassBuilder setFields(FieldDef...)` |  |
| `public ClassBuilder withFields(Iterable<FieldDef>)` |  |
| `public ClassBuilder withFields(FieldDef...)` |  |
| `public ClassBuilder withField(Consumer<FieldBuilder>)` |  |
| `public ClassBuilder withField(String, TypeId, int)` | no initial value |
| `public ClassBuilder withField(String, int)` | name:type, last colon |
| `public ClassBuilder withField(String, TypeId, int, EncodedValue)` |  |
| `public ClassBuilder withField(String, int, EncodedValue)` |  |
| `public ClassBuilder withoutFields(Iterable<FieldDef>)` |  |
| `public ClassBuilder withoutFields(FieldDef...)` |  |
| `public ClassBuilder setMethods(Iterable<MethodDef>)` |  |
| `public ClassBuilder setMethods(MethodDef...)` |  |
| `public ClassBuilder withMethods(Iterable<MethodDef>)` |  |
| `public ClassBuilder withMethods(MethodDef...)` |  |
| `public ClassBuilder withMethod(Consumer<MethodBuilder>)` |  |
| `public ClassBuilder withMethodBody(String, ProtoId, int, MethodImplementation)` | no body for abstract/native |
| `public ClassBuilder withMethodBody(String, int, MethodImplementation)` | descriptor form, split at the last ( |
| `public ClassBuilder withMethodBody(String, ProtoId, int, int, Consumer<CodeBuilder>)` | extra locals |
| `public ClassBuilder withMethodBody(String, int, int, Consumer<CodeBuilder>)` |  |
| `public ClassBuilder withoutMethods(Iterable<MethodDef>)` |  |
| `public ClassBuilder withoutMethods(MethodDef...)` |  |

## com.v7878.dex.builder.FieldBuilder

`public final class FieldBuilder`

Invariant: `of(FieldId)` copies identity only; `withHiddenApiFlags` stores the word verbatim, its check is a no-op placeholder.

**Constructors** — `private FieldBuilder()`

**Methods**

| Signature | Note |
|---|---|
| `public String getName()` |  |
| `public TypeId getType()` |  |
| `public int getAccessFlags()` |  |
| `public int getHiddenApiFlags()` |  |
| `public EncodedValue getInitialValue()` |  |
| `public NavigableSet<Annotation> getAnnotations()` | live |
| `public static FieldBuilder newInstance()` |  |
| `public FieldDef finish()` | `FieldDef.of` |
| `public static FieldDef build(Consumer<FieldBuilder>)` |  |
| `public FieldBuilder if_(boolean, Consumer<FieldBuilder>, Consumer<FieldBuilder>)` |  |
| `public FieldBuilder if_(boolean, Consumer<FieldBuilder>)` |  |
| `public FieldBuilder commit(Consumer<FieldBuilder>)` |  |
| `public FieldBuilder of(FieldDef)` |  |
| `public FieldBuilder of(FieldId)` | name and type only |
| `public FieldBuilder of(String)` | short `name:type`, last colon; a `->` member reference → `IllegalArgumentException` |
| `public FieldBuilder withName(String)` |  |
| `public FieldBuilder withType(TypeId)` |  |
| `public FieldBuilder withFlags(int)` | validates the mask |
| `public FieldBuilder withFlags(AccessFlag...)` |  |
| `public FieldBuilder withHiddenApiFlags(int)` | stores verbatim; placeholder check |
| `public FieldBuilder withInitialValue(EncodedValue)` | accepts null |
| `public FieldBuilder setAnnotations(Iterable<Annotation>)` |  |
| `public FieldBuilder setAnnotations(Annotation...)` |  |
| `public FieldBuilder withAnnotations(Iterable<Annotation>)` |  |
| `public FieldBuilder withAnnotations(Annotation...)` |  |
| `public FieldBuilder withoutAnnotations(Iterable<Annotation>)` |  |
| `public FieldBuilder withoutAnnotations(Annotation...)` |  |

## com.v7878.dex.builder.MethodBuilder

`public final class MethodBuilder`

Invariant: the parameter list is all-or-nothing — every `withProto`/`withParameters` call replaces it whole, so `null` empties it (model-invariants.md).

**Constructors** — `private MethodBuilder()`

**Methods**

| Signature | Note |
|---|---|
| `public String getName()` |  |
| `public TypeId getReturnType()` |  |
| `public List<Parameter> getParameters()` | null until a parameter call |
| `public int getAccessFlags()` |  |
| `public int getHiddenApiFlags()` |  |
| `public MethodImplementation getImplementation()` |  |
| `public NavigableSet<Annotation> getAnnotations()` | live |
| `public static MethodBuilder newInstance()` |  |
| `public MethodDef finish()` | `MethodDef.of`; an unset name or return type → `IllegalStateException` stating what is missing |
| `public static MethodDef build(Consumer<MethodBuilder>)` |  |
| `public MethodBuilder if_(boolean, Consumer<MethodBuilder>, Consumer<MethodBuilder>)` |  |
| `public MethodBuilder if_(boolean, Consumer<MethodBuilder>)` |  |
| `public MethodBuilder commit(Consumer<MethodBuilder>)` |  |
| `public MethodBuilder of(MethodDef)` |  |
| `public MethodBuilder of(MethodId)` | identity only |
| `public MethodBuilder of(String)` | short `name(proto)ret`, split at the last `(`; a `->` member reference → `IllegalArgumentException` |
| `public MethodBuilder withName(String)` |  |
| `public MethodBuilder withReturnType(TypeId)` |  |
| `public MethodBuilder withConstructorSignature()` | returns void |
| `public MethodBuilder withStaticConstructorSignature()` | (), no ACC_STATIC |
| `public MethodBuilder withParameters(Iterable<Parameter>)` |  |
| `public MethodBuilder withParameters(Parameter...)` |  |
| `public MethodBuilder withParameterTypes(Iterable<TypeId>)` |  |
| `public MethodBuilder withParameterTypes(TypeId...)` |  |
| `public MethodBuilder withProto(ProtoId)` |  |
| `public MethodBuilder withProto(String)` | a prototype |
| `public MethodBuilder withProto(TypeId, Iterable<TypeId>)` |  |
| `public MethodBuilder withProto(TypeId, TypeId...)` |  |
| `public MethodBuilder withFlags(int)` | validates the mask |
| `public MethodBuilder withFlags(AccessFlag...)` |  |
| `public MethodBuilder withHiddenApiFlags(int)` | stores verbatim; placeholder check |
| `public MethodBuilder withCode(MethodImplementation)` | null marks it bodiless |
| `public MethodBuilder withCode(int, Consumer<CodeBuilder>)` | ins from the parameters |
| `public MethodBuilder setAnnotations(Iterable<Annotation>)` |  |
| `public MethodBuilder setAnnotations(Annotation...)` |  |
| `public MethodBuilder withAnnotations(Iterable<Annotation>)` |  |
| `public MethodBuilder withAnnotations(Annotation...)` |  |
| `public MethodBuilder withoutAnnotations(Iterable<Annotation>)` |  |
| `public MethodBuilder withoutAnnotations(Annotation...)` |  |

## The `set*` / `with*` / `without*` contract

`setX` clears and adds every argument; `withX` adds each argument after removing the element with the same
dex key (`compareTo`), so the last value wins; `withoutX` removes every occurrence (`List.removeAll` for
interfaces, `TreeSet.remove` for sets). `setX(null)` clears, `withX(null)`/`withoutX(null)` do nothing
(a `null` collection is empty, varargs arrays included - model-invariants.md), and no builder rejects
a duplicate — `ClassDef.of` does. Keys are (`static`, name, type) and (`direct`, name, return type,
parameter types). `withInterfaces` is the one `with*` that appends without deduplicating; `finish()` keeps
the first occurrence.

## Register layout of a generated body

`withCode(locals, consumer)` derives the layout before the callback runs: `ins` is the sum of
`parameter.getType().getRegisterCount()` (`V` 0, `J`/`D` 2, else 1), `regs = ins + locals`, plus one for
`this` when the method is not `ACC_STATIC`. Hence `registers = parameters + locals (+1 for this)`,
`l(i) = i`, `this_() = locals()`, `p(i) = locals() + 1 + i`. `MethodDef.of` requires
`implementation.getRegisterCount() >= ins`, else `IllegalArgumentException` (too few registers for the
parameters). Both reads are eager (`ACC_STATIC` is read at call time); `withCode(int, …)` without
`withParameterTypes`/`withProto` throws `IllegalStateException` stating that the parameters are unset, not an NPE.
Order parameters → flags → `withCode`.

## What `finish()` validates

`ClassDef.of` re-checks the class flags and the superclass rules (bad flags; an interface whose
superclass is not `java.lang.Object`; a non-`Object` class without a superclass; `java.lang.Object`
with one), all `IllegalArgumentException`. `FieldDef.of` checks the initial value first (an instance
field may not have one), then the mask and the single visibility bit (bad flags; more than one of
public/protected/private), again `IllegalArgumentException`. `MethodDef.of` checks the mask, the body
(an `abstract`/`native` method may not have one, a concrete one must), the register count and the
constructor rules (no `static` on `<init>`, `<init>` is `void`, `static` on `<clinit>`, `<clinit>`
has descriptor `()V`, the name is `<init>` or `<clinit>`), all `IllegalArgumentException`, then adds
`ACC_CONSTRUCTOR`. `MethodBuilder.finish()` rejects an unset name or return type with
`IllegalStateException` before it reaches `MethodDef.of`.

## Pitfalls

- **`getAnnotations()` is the live set** — it changes every later `finish()`.
- **`withInterfaces` never deduplicates**; duplicates vanish at `finish()`.
- **`setX(null)` clears; `withX(null)`/`withoutX(null)` do nothing - also for a varargs array.**
- **Keys are `compareTo` keys** — static and instance fields of one name and type coexist.
- **`of(ClassDef)` is an assignment**, not a merge.
- **`finish()` is repeatable** — the built object is independent.
- **`withCode(locals, …)` needs the prototype and flags already set.**

Up: [SKILL.md](../SKILL.md)
