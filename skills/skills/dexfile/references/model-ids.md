# Identifiers and descriptors

`TypeId`, `ProtoId`, `FieldId`, `MethodId`, `MethodHandleId` and `CallSiteId` reference the pools;
`MemberId` is their shared parent. None checks that the target exists; all compare by value.

## Descriptor grammar

| Descriptor | Java type | `getShorty()` | `getRegisterCount()` |
|---|---|---|---|
| `V` | `void` | `V` | 0 |
| `Z` `B` `S` `C` `I` `F` | `boolean` `byte` `short` `char` `int` `float` | the same letter | 1 |
| `J` `D` | `long` `double` | the same letter | 2 |
| `Lpkg/Name;` | reference | `L` | 1 |
| `[X` | array of `X` | `L` | 1 |

`TypeId.of(String)` accepts one of the nine letters (`V` only alone), `[` + a non-void component, or
`L`…`;` with `/`-separated non-empty parts and no `.`; `[`/`]` are legal only as leading array
markers, so a class name containing them (`Lint[];`) is rejected:

| Input | `of(String)` |
|---|---|
| `I`, `V`, `[[J`, `Ljava/lang/String;` | accepted |
| `VI`, `[V`, `[`, `""`, `Ljava/lang/String`, `L;`, `Lfoo/;`, `Lfoo//Bar;`, `Lfoo.Bar;`, `Lint[];`, `Lfoo[bar;`, `Lfoo];` | `IllegalArgumentException` |
| `null` | `NullPointerException` |

Proto descriptor: `(parameters)return`, no `this`.

## com.v7878.dex.immutable.TypeId

`public final class TypeId implements Comparable<TypeId>`

Invariant: `array_depth` is stored apart from the descriptor but derived from it, so `of`,
`raw(String)`, `isArray()` and `getArrayDepth()` always agree.

**Fields** — `public static final TypeId`: `V` `Z` `B` `S` `C` `I` `F` `J` `D` `OBJECT`

**Constructors** — `private TypeId(int array_depth, String descriptor)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static TypeId raw(String descriptor)` | |
| `public static TypeId of(String descriptor)` | |
| `public static TypeId of(char shorty)` | `L` → `OBJECT` |
| `public static TypeId of(Class<?> clazz)` | |
| `public static TypeId ofBinaryName(String name)` | `[`-prefixed input goes through `of`; no `[]` suffix — `int[]` throws, use `ofName` |
| `public static TypeId ofName(String name)` | `int[]` → `[I`, `int[][]` → `[[I`; `void[]` → `IllegalArgumentException` |
| `public static List<TypeId> listOf(Iterable<String> descriptors)` | `null` = empty |
| `public static List<TypeId> listOf(String... descriptors)` | `null` array = empty |
| `public String getDescriptor()` | |
| `public char getShorty()` | |
| `public int getRegisterCount()` | |
| `public boolean isArray()` | |
| `public boolean isReference()` | |
| `public boolean isPrimitive()` | `void` counts as primitive |
| `public boolean isVoid()` | |
| `public boolean isWidePrimitive()` | |
| `public boolean isThinPrimitive()` | |
| `public int getArrayDepth()` | |
| `public TypeId array(int depth)` | `0` → `this`; `void` → `IllegalArgumentException` |
| `public TypeId array()` | |
| `public TypeId componentType()` | `null` if not an array |
| `public TypeId baseType()` | |
| `public TypeId erased()` | references → `OBJECT` |
| `public TypeId basic()` | `Z B S C I` → `I` |
| `public String getBinaryName()` | |
| `public String getName()` | |
| `public static boolean isInSamePackage(TypeId a, TypeId b)` | arrays ignore depth |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |
| `public int compareTo(TypeId)` | |
| `public String toString()` | |

## com.v7878.dex.immutable.ProtoId

`public final class ProtoId implements Comparable<ProtoId>`

Invariant: parameters are ordered; a `void` parameter is rejected by every factory, `raw` and `of`
alike (`IllegalArgumentException` naming the index), so `(IJ)V` and `(JI)V`
differ. No parameter-count limit: only a body is bounded, by `registers_size`; direct calls stop at
`35c` 5 words, `3rc` 255, `5rc` 65535 (dex036) - longer methods need reflection.

**Constructors** — `private ProtoId(TypeId return_type, List<TypeId> parameters)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static ProtoId raw(TypeId return_type, List<TypeId> parameters)` | |
| `public static ProtoId of(TypeId return_type, Iterable<TypeId> parameters)` | `null` = empty |
| `public static ProtoId of(TypeId return_type, TypeId... parameters)` | `null` array = empty |
| `public static ProtoId of(Executable value)` | constructors → `void` |
| `public static ProtoId of(MethodType value)` | |
| `public static ProtoId of(String descriptor)` | |
| `public String getDescriptor()` | |
| `public TypeId getReturnType()` | |
| `public List<TypeId> getParameterTypes()` | |
| `public int getParametersCount()` | |
| `public String computeShorty()` | |
| `public int countInputRegisters()` | no `this` |
| `public ProtoId erased()` | |
| `public ProtoId basic()` | |
| `public ProtoId insertThis(TypeId thiz)` | prepends, live view |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |
| `public int compareTo(ProtoId)` | |
| `public String toString()` | |

## com.v7878.dex.immutable.MemberId

`public abstract sealed class MemberId permits MethodId, FieldId`

Invariant: a primitive declaring class is rejected (`void` too); arrays are accepted.

**Constructors** — `public MemberId()`

**Methods**

| Signature | Note |
|---|---|
| `public abstract TypeId getDeclaringClass()` | |
| `public abstract String getName()` | |

## com.v7878.dex.immutable.FieldId

`public final class FieldId extends MemberId implements Comparable<FieldId>`

Invariant: the type is part of the key, so `Ldemo/A;->x:I` and `Ldemo/A;->x:J` are different ids.

**Constructors** — `private FieldId(TypeId declaring_class, String name, TypeId type)`

**Methods**

| Signature | Note |
|---|---|
| `public static FieldId of(TypeId declaring_class, String name, TypeId type)` | |
| `public static FieldId of(Field field)` | |
| `public static FieldId of(Enum<?> e)` | class and type are the enum |
| `public static FieldId of(String descriptor)` | first `->`, last `:` after it |
| `public String getDescriptor()` | |
| `public TypeId getDeclaringClass()` | |
| `public String getName()` | |
| `public TypeId getType()` | |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |
| `public int compareTo(FieldId)` | |
| `public String toString()` | |

## com.v7878.dex.immutable.MethodId

`public final class MethodId extends MemberId implements Comparable<MethodId>`

Invariant: `isConstructor()` looks at the name only: a `MethodId` carries no flags.

**Constructors** — `private MethodId(TypeId declaring_class, String name, ProtoId proto)`

**Methods**

| Signature | Note |
|---|---|
| `public static MethodId of(TypeId, String, ProtoId)` | |
| `public static MethodId of(TypeId, String, TypeId, Iterable<TypeId>)` | `null` = empty |
| `public static MethodId of(TypeId, String, TypeId, TypeId...)` | `null` array = empty |
| `public static MethodId constructor(TypeId declaring_class, Iterable<TypeId> parameters)` | `<init>`, `void`; `null` = empty |
| `public static MethodId constructor(TypeId declaring_class, TypeId... parameters)` | `null` array = empty |
| `public static MethodId static_constructor(TypeId declaring_class)` | `<clinit>`, `()V` |
| `public static MethodId of(Executable ex)` | static constructor → `<clinit>` |
| `public static MethodId of(String descriptor)` | first `->`, first `(` after it |
| `public String getDescriptor()` | |
| `public TypeId getDeclaringClass()` | |
| `public String getName()` | |
| `public boolean isConstructor()` | |
| `public boolean isInstanceInitializer()` | |
| `public boolean isClassInitializer()` | |
| `public ProtoId getProto()` | |
| `public ProtoId instanceProto()` | live view, `this` first |
| `public TypeId getReturnType()` | |
| `public List<TypeId> getParameterTypes()` | |
| `public String computeShorty()` | |
| `public int countInputRegisters()` | no `this` |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |
| `public int compareTo(MethodId)` | |
| `public String toString()` | |

## com.v7878.dex.immutable.MethodHandleId

`public final class MethodHandleId implements Comparable<MethodHandleId>`

Invariant: the four field kinds (`static-put`, `static-get`, `instance-put`, `instance-get`) need a
`FieldId`, the five `invoke-*` kinds a `MethodId`; a mismatch throws `IllegalArgumentException`
naming the required class, `NullPointerException` only for a `null` kind or member.

**Constructors** — `private MethodHandleId(MethodHandleType handle_type, MemberId member)`

**Methods**

| Signature | Note |
|---|---|
| `public static MethodHandleId of(MethodHandleType handle_type, MemberId member)` | |
| `public static MethodHandleId of(String descriptor)` | |
| `public String getDescriptor()` | |
| `public MethodHandleType getHandleType()` | |
| `public MemberId getMember()` | |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |
| `public int compareTo(MethodHandleId)` | |
| `public String toString()` | |

## com.v7878.dex.immutable.CallSiteId

`public final class CallSiteId implements Comparable<CallSiteId>`

Invariant: the name is the identity tie-breaker - same handle, method name, proto and arguments with
a different name are unequal; `toString()` is a debug view, not an API contract.

**Constructors** — `private CallSiteId(String, MethodHandleId, String, ProtoId, List<EncodedValue>)`

**Methods**

| Signature | Note |
|---|---|
| `@Internal public static CallSiteId raw(String, MethodHandleId, String, ProtoId, List<EncodedValue>)` | no copy |
| `public static CallSiteId of(String, MethodHandleId, String, ProtoId, Iterable<EncodedValue>)` | copies; `null` = empty |
| `public static CallSiteId of(String, MethodHandleId, String, ProtoId, EncodedValue...)` | `null` array = empty |
| `public String getName()` | unique in one dex |
| `public MethodHandleId getMethodHandle()` | |
| `public String getMethodName()` | |
| `public ProtoId getMethodProto()` | |
| `public List<EncodedValue> getExtraArguments()` | |
| `public int hashCode()` | |
| `public boolean equals(Object)` | |
| `public int compareTo(CallSiteId)` | |
| `public String toString()` | `CallSiteId{name=... handle=... method=... proto=... extras=N}` |

## Identity, equality and ordering

`equals`/`hashCode` use the pool components and nothing else: the descriptor; return type plus
parameters; class, name and type; class, name and proto; kind plus member; the five call-site fields.
`compareTo` chains the same components, with two differences: `MethodHandleId` compares the numeric
kind before the member, and `CallSiteId` compares the handle before the method name. `MemberId` has
no state and is not `Comparable`.

Ordering is `String.compareTo` on descriptors, not human order: `"I" < "L..." < "Z" < "[I" < "[J"`.
Never supply a custom `Comparator`: the writer and the lookups assume this order.

## `of` versus `raw`

`of` validates: the descriptor shape in `TypeId.of(String)`, the grammar in `ProtoId.of(String)`,
the `class->name:type` / `class->name(proto)ret` split in `FieldId`/`MethodId`, and a non-primitive
declaring class in every member id. `raw` stores what it is given: `TypeId.raw(String)` skips the
descriptor shape check, `ProtoId.raw`, `CallSiteId.raw` take the list as is. Every `of` collection
argument treats `null` as empty (model-invariants.md); the `raw` factories keep rejecting it.

Up: [SKILL.md](../SKILL.md)
