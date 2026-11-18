# Definitions: dex, class and members

`Dex`, `ClassDef`, `MemberDef`, `FieldDef`, `MethodDef` and `Parameter` are the definition side of
the model: flags, bodies, annotations, ordered sets; ids: model-ids.md, bodies:
model-bodies.md, factory rules: model-invariants.md. Every `of` collection argument here takes
`null` as empty (model-invariants.md).

## com.v7878.dex.immutable.Dex

`public final class Dex`

Invariant: the list is used as given - never sorted, never de-duplicated (`Dex.of(a, a)` holds two
classes); `findClass` is linear and returns the first match; no uniqueness check exists anywhere,
and `DexIO.read(..., ids)` repeats entries for repeated indices.

**Constructors** — `private Dex(List<ClassDef> classes)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static Dex raw(List<ClassDef> classes)` | no copy |
| `public static Dex of(Iterable<ClassDef> classes)` | copies |
| `public static Dex of(ClassDef... classes)` | |
| `public List<ClassDef> getClasses()` | |
| `public ClassDef findClass(TypeId type)` | |
| `public String toString()` | `Dex{classes=[Lfoo;, Lbar;]}`, types only |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |

## com.v7878.dex.immutable.ClassDef

`public final class ClassDef implements Annotatable, DexIO.ClassHeader`

Invariant: `type`, `superclass` and every interface must be reference types - no primitive, no array
(`IllegalArgumentException`, checked by `of(...)` and `raw(...)` alike); interfaces need
`TypeId.OBJECT` as superclass, `Object` needs `null`, every other class needs a non-`null` one, and
`null` is checked before those rules; the interface list is always de-duplicated keeping first-seen
order.

**Constructors** — `private ClassDef(TypeId, int, TypeId, List<TypeId>, String, NavigableSet<FieldDef>, NavigableSet<MethodDef>, NavigableSet<Annotation>)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static ClassDef raw(TypeId type, int access_flags, TypeId superclass, List<TypeId> interfaces, String source_file, NavigableSet<FieldDef> fields, NavigableSet<MethodDef> methods, NavigableSet<Annotation> annotations)` | sets adopted without copy; the interface list is always rebuilt (duplicates dropped, first-seen order, unmodifiable); scalars and the reference-only rule validated |
| `public static ClassDef of(TypeId type, int access_flags, TypeId superclass, Iterable<TypeId> interfaces, String source_file, Iterable<FieldDef> fields, Iterable<MethodDef> methods, Iterable<Annotation> annotations)` | copies |
| `public TypeId getType()` | |
| `public int getAccessFlags()` | |
| `public boolean isInterface()` | |
| `public boolean isJavaLangObject()` | |
| `public TypeId getSuperclass()` | |
| `public List<TypeId> getInterfaces()` | unmodifiable, order kept |
| `public String getSourceFile()` | |
| `public NavigableSet<FieldDef> getFields()` | static → instance, name, type |
| `public NavigableSet<FieldDef> getStaticFields()` | view of the prefix |
| `public FieldDef findStaticField(String name, TypeId type)` | |
| `public FieldDef findStaticField(FieldId id)` | |
| `public NavigableSet<FieldDef> getInstanceFields()` | view of the suffix |
| `public FieldDef findInstanceField(String name, TypeId type)` | |
| `public FieldDef findInstanceField(FieldId id)` | |
| `public FieldDef findField(String name, TypeId type)` | instance → static |
| `public FieldDef findField(FieldId id)` | |
| `public NavigableSet<MethodDef> getMethods()` | direct → virtual, name, return, params |
| `public NavigableSet<MethodDef> getDirectMethods()` | view of the prefix |
| `public MethodDef findDirectMethod(String name, ProtoId proto)` | |
| `public MethodDef findDirectMethod(MethodId id)` | |
| `public MethodDef findClassInitializer()` | `<clinit>` with `()V` |
| `public NavigableSet<MethodDef> getVirtualMethods()` | view of the suffix |
| `public MethodDef findVirtualMethod(String name, ProtoId proto)` | |
| `public MethodDef findVirtualMethod(MethodId id)` | |
| `public MethodDef findMethod(String name, ProtoId proto)` | virtual → direct |
| `public MethodDef findMethod(MethodId id)` | |
| `public NavigableSet<Annotation> getAnnotations()` | |
| `public String toString()` | `ClassDef{type=Lfoo; flags=[public final] superclass=Lbar; interfaces=[...] source=Foo.java fields=N methods=N annotations=N}`; optional parts omitted, inner-class flags not printed |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |

## com.v7878.dex.immutable.MemberDef

`public abstract sealed class MemberDef implements Annotatable permits MethodDef, FieldDef`

Invariant: no back reference to the declaring class; `getHiddenApiFlags()` is stored verbatim, and
its check (`Preconditions.checkHiddenApiFlags`) is a placeholder, not validation.

**Constructors** — `public MemberDef()`

**Methods**

| Signature | Note |
|---|---|
| `public abstract String getName()` | |
| `public abstract int getAccessFlags()` | |
| `public abstract int getHiddenApiFlags()` | |

## com.v7878.dex.immutable.FieldDef

`public final class FieldDef extends MemberDef implements Comparable<FieldDef>`

Invariant: a non-`null` initial value requires `ACC_STATIC`; its shorty must match the field shorty (primitive: own kind, reference: any reference kind).
`null` name/type/annotations are checked before that rule, so with two errors the caller sees
`NullPointerException`.

**Constructors** — `private FieldDef(String, TypeId, int, int, EncodedValue, NavigableSet<Annotation>)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static FieldDef raw(String name, TypeId type, int access_flags, int hiddenapi_flags, EncodedValue initial_value, NavigableSet<Annotation> annotations)` | no copy; scalars validated |
| `public static FieldDef of(String name, TypeId type, int access_flags, int hiddenapi_flags, EncodedValue initial_value, Iterable<Annotation> annotations)` | copies |
| `public String getName()` | |
| `public TypeId getType()` | |
| `public int getAccessFlags()` | |
| `public boolean isStatic()` | |
| `public boolean isInstance()` | |
| `public int getHiddenApiFlags()` | |
| `public EncodedValue getInitialValue()` | `null` unless set |
| `public NavigableSet<Annotation> getAnnotations()` | |
| `public int hashCode()` | flags, hiddenapi, name, type, value, annotations |
| `public boolean equals(Object)` | same components |
| `public String toString()` | `FieldDef{flags=[public static] name=f type=I hiddenapi=0x00000000 annotations=N}` |
| `public int compareTo(FieldDef)` | |

## com.v7878.dex.immutable.MethodDef

`public final class MethodDef extends MemberDef implements Comparable<MethodDef>`

Invariant: `<init>`/`<clinit>` force `ACC_CONSTRUCTOR`; `<init>` is non-static and `void`,
`<clinit>` is static with `()V`; `abstract`/`native` require a `null` body, the rest need
`register_count >= countFullInputRegisters()`; a `void` parameter is rejected
(`IllegalArgumentException`).

**Constructors** — `private MethodDef(String, TypeId, List<Parameter>, int, int, MethodImplementation, NavigableSet<Annotation>)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static MethodDef unchecked(String name, TypeId return_type, List<Parameter> parameters, int access_flags, int hiddenapi_flags, MethodImplementation implementation, NavigableSet<Annotation> annotations)` | no flag or body checks; `null` arguments and `void` parameters still rejected |
| `@Internal public static MethodDef raw(String name, TypeId return_type, List<Parameter> parameters, int access_flags, int hiddenapi_flags, MethodImplementation implementation, NavigableSet<Annotation> annotations)` | validates, no copy |
| `public static MethodDef of(String name, TypeId return_type, Iterable<Parameter> parameters, int access_flags, int hiddenapi_flags, MethodImplementation implementation, Iterable<Annotation> annotations)` | copies, then `raw` |
| `public String getName()` | |
| `public TypeId getReturnType()` | |
| `public List<Parameter> getParameters()` | stored list |
| `public List<TypeId> getParameterTypes()` | live view |
| `public ProtoId getProto()` | new per call, no `this` |
| `public ProtoId callProto(TypeId declaring_class)` | prepends `declaring_class` unless static; `null` rejected for both |
| `public String computeShorty()` | return + parameters |
| `public String computeFullShorty()` | adds `L` for `this` |
| `public int countInputRegisters()` | parameters only |
| `public int countFullInputRegisters()` | plus `this` |
| `public int getAccessFlags()` | normalised, includes `ACC_CONSTRUCTOR` |
| `public boolean isConstructor()` | |
| `public boolean isInstanceInitializer()` | |
| `public boolean isClassInitializer()` | |
| `public boolean isDirect()` | any `ACC_DIRECT_MASK` bit |
| `public boolean isVirtual()` | |
| `public int getHiddenApiFlags()` | |
| `public MethodImplementation getImplementation()` | `null` for `abstract`/`native` |
| `public NavigableSet<Annotation> getAnnotations()` | |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |
| `public String toString()` | `MethodDef{flags=[public] name=m proto=()V hiddenapi=0x00000000 annotations=N}` |
| `public int compareTo(MethodDef)` | |

## com.v7878.dex.immutable.Parameter

`public final class Parameter implements Annotatable`

Invariant: `name` may be `null` ("unknown", not empty); a `void` parameter type is rejected
(`IllegalArgumentException`); parameters are positional and
not `Comparable`.

**Constructors** — `private Parameter(TypeId, String, NavigableSet<Annotation>)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static Parameter raw(TypeId type, String name, NavigableSet<Annotation> annotations)` | no copy |
| `public static Parameter of(TypeId type, String name, Iterable<Annotation> annotations)` | copies |
| `public static Parameter of(TypeId type, String name)` | |
| `public static Parameter of(TypeId type)` | |
| `public static List<Parameter> listOf(Iterable<TypeId> types)` | |
| `public static List<Parameter> listOf(TypeId... types)` | |
| `public TypeId getType()` | |
| `public String getName()` | may be `null` |
| `public NavigableSet<Annotation> getAnnotations()` | |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |
| `public String toString()` | `Parameter{type=I name=<unnamed> annotations=N}` (`<unnamed>` for a `null` name) |

## Set order

The sets keep dex order, which is why they are `NavigableSet`s:

- `FieldDef.compareTo`: static before instance → name → type.
- `MethodDef.compareTo`: direct before virtual → name → return type → parameter types.
- `getStaticFields()`/`getInstanceFields()` and `getDirectMethods()`/`getVirtualMethods()` are live
  views of the prefix and the suffix of `getFields()`/`getMethods()`.

A set is keyed by `compareTo`, not `equals`: elements that compare equal are one element. `of(...)`
rejects such a pair, a `TreeSet` keeps the first, `ClassBuilder.with*` replaces the old entry.

## Lookup rules

| Call | Searches |
|---|---|
| `findMethod` | virtual, then direct |
| `findField` | instance, then static |
| `findDirectMethod`, `findVirtualMethod` | that half only |
| `findStaticField`, `findInstanceField` | that half only |
| `findClassInitializer` | `<clinit>` with `()V` |

The asymmetry is deliberate: a valid dex cannot hold the same method (field) in both halves, and
cross-list duplicate validation is expensive, so the model checks duplicates only within one list,
and the half order decides. Every `find*` returns `null` for a missing member and never throws for
one; the `(MethodId)`/`(FieldId)` overloads also check the declaring class, the `(String, ProtoId)`
ones throw `NullPointerException`.

## Equality components

| Type | `equals`/`hashCode` components |
|---|---|
| `Dex` | class list, order significant |
| `ClassDef` | type, flags, superclass, interfaces (order), source file, fields, methods, annotations |
| `FieldDef` | flags, hiddenapi flags, name, type, initial value, annotations |
| `MethodDef` | flags, hiddenapi flags, name, return type, parameters, implementation, annotations |
| `Parameter` | name, type, annotations |

Annotation sets compare element by element through `equals`, so visibility and values participate.

## Example

```java
FieldDef count = FieldDef.of("count", TypeId.I, DexConstants.ACC_PRIVATE, 0, null, List.of());
FieldDef max = FieldDef.of("MAX", TypeId.I, DexConstants.ACC_STATIC, 0, null, List.of());
MethodDef init = MethodDef.of("<init>", TypeId.V, List.of(), DexConstants.ACC_PUBLIC, 0,
        MethodImplementation.of(1, List.of(), List.of(), List.of()), List.of());
ClassDef cls = ClassDef.of(TypeId.of("Ldemo/Counter;"), DexConstants.ACC_PUBLIC,
        TypeId.OBJECT, List.of(), null, List.of(count, max), List.of(init), List.of());

System.out.println(cls.getFields().stream().map(FieldDef::getName).toList()); // [MAX, count]
System.out.println(init.getAccessFlags() & DexConstants.ACC_CONSTRUCTOR);    // 65536
```

Up: [SKILL.md](../SKILL.md)
