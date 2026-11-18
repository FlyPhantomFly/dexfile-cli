# Encoded values

Nineteen types: the sealed base `EncodedValue` and eighteen final subclasses, one per `value_type`
tag, used for static field initialisers, annotation element values and call-site arguments. The
writer owns the payload width; the model stores the value only.

Tags, which are also the `compareTo` order: 0x00 BYTE, 0x02 SHORT, 0x03 CHAR, 0x04 INT, 0x06 LONG,
0x10 FLOAT, 0x11 DOUBLE, 0x15 METHOD_TYPE, 0x16 METHOD_HANDLE, 0x17 STRING, 0x18 TYPE, 0x19 FIELD,
0x1a METHOD, 0x1b ENUM, 0x1c ARRAY, 0x1d ANNOTATION, 0x1e NULL, 0x1f BOOLEAN.

Caches: `EncodedByte` covers all 256 values, `EncodedShort`, `EncodedInt` and `EncodedLong` cover
-128..127, `EncodedChar` covers 0..255, and `EncodedString`, `EncodedArray`, `EncodedNull`,
`EncodedBoolean` share `EMPTY`, `EMPTY`, `INSTANCE`, `TRUE`/`FALSE`. Every other `of` allocates,
so compare with `equals`.

`isDefault()` is true only for `FALSE`, for the numeric zeros (raw bits zero for float and double)
and always for `EncodedNull`; every reference class returns false, the empty string and array
included. A written `static_values` array drops its trailing run of defaults, so +0.0 and "no
initial value" produce the same file while -0.0 does not. `getType()` is the Java stand-in of the
tag, and `EncodedAnnotation` is its only override.

## com.v7878.dex.immutable.value.EncodedValue

`public abstract sealed class EncodedValue implements Comparable<EncodedValue>`

Invariant: the tag decides the class, so `compareTo` casts without checking; it compares the
`value_type` code first and the payload second, and `compareTo(null)` throws.

**Constructors** — `public EncodedValue()`

**Methods**

| Signature | Note |
|---|---|
| `public abstract ValueType getValueType()` | the tag |
| `public TypeId getType()` | derived from the tag; overridden by EncodedAnnotation |
| `public char getShorty()` | shorty of `getType()`: `V`, `I`, `J`, `D`, `F`, `Z`, `B`, `S`, `C` or `L` |
| `public abstract boolean isDefault()` | true for the zero of the type |
| `public static EncodedValue defaultValue(TypeId type)` | by shorty: Z gives FALSE, B S C I J F D give of(0), L gives EncodedNull; V raises `IllegalArgumentException` |
| `public static EncodedValue ofValue(Object obj)` | in order: null (`EncodedNull.INSTANCE`), EncodedValue, the eight boxed primitives, String, TypeId, ProtoId, MethodId, FieldId, MethodHandleId, CommonAnnotation, Class, MethodType, Executable, Field, Enum, then MethodHandle and reflection annotations raise `UnsupportedOperationException` (not implemented), then the eight primitive arrays, Object[], any Iterable, else `IllegalArgumentException` |

## com.v7878.dex.immutable.value.EncodedByte

`public final class EncodedByte extends EncodedValue`

**Methods**

| Signature |
|---|
| `public static EncodedByte of(byte)` |
| `public byte getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedShort

`public final class EncodedShort extends EncodedValue`

**Methods**

| Signature |
|---|
| `public static EncodedShort of(short)` |
| `public short getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedChar

`public final class EncodedChar extends EncodedValue`

**Methods**

| Signature |
|---|
| `public static EncodedChar of(char)` |
| `public char getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedInt

`public final class EncodedInt extends EncodedValue`

**Methods**

| Signature |
|---|
| `public static EncodedInt of(int)` |
| `public int getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedLong

`public final class EncodedLong extends EncodedValue`

**Methods**

| Signature |
|---|
| `public static EncodedLong of(long)` |
| `public long getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedFloat

`public final class EncodedFloat extends EncodedValue`

Invariant: no cache; `equals`, `hashCode` and `compareTo` use the raw bits, so `NaN` equals itself, `-0.0` differs from `0.0` and sorts below every other value, and negatives sort in reverse numeric order.

**Methods**

| Signature |
|---|
| `public static EncodedFloat of(float)` |
| `public float getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedDouble

`public final class EncodedDouble extends EncodedValue`

Invariant: no cache; raw-bit equality and ordering as in `EncodedFloat`, via `Double.doubleToRawLongBits`, so the order is not that of `Double.compare`.

**Methods**

| Signature |
|---|
| `public static EncodedDouble of(double)` |
| `public double getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedMethodType

`public final class EncodedMethodType extends EncodedValue`

**Methods**

| Signature |
|---|
| `public static EncodedMethodType of(ProtoId)` |
| `public ProtoId getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedMethodHandle

`public final class EncodedMethodHandle extends EncodedValue`

Invariant: the payload is the pool-level `MethodHandleId`, never a `java.lang.invoke.MethodHandle`.

**Methods**

| Signature |
|---|
| `public static EncodedMethodHandle of(MethodHandleId)` |
| `public MethodHandleId getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedString

`public final class EncodedString extends EncodedValue`

Invariant: `of("")` returns `EMPTY` and `of(null)` throws; the empty string is a real constant, never a default.

**Fields** — `public static final EncodedString`: `EMPTY`

**Methods**

| Signature |
|---|
| `public static EncodedString of(String)` |
| `public String getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedType

`public final class EncodedType extends EncodedValue`

Invariant: primitives and arrays are allowed; the value is a class literal, so `getType()` is `Ljava/lang/Class;`, not the wrapped type.

**Methods**

| Signature |
|---|
| `public static EncodedType of(TypeId)` |
| `public TypeId getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedField

`public final class EncodedField extends EncodedValue`

**Methods**

| Signature |
|---|
| `public static EncodedField of(FieldId)` |
| `public FieldId getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedMethod

`public final class EncodedMethod extends EncodedValue`

Invariant: constructors share this tag: a `MethodId` named `<init>` or `<clinit>` is a valid payload.

**Methods**

| Signature |
|---|
| `public static EncodedMethod of(MethodId)` |
| `public MethodId getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedEnum

`public final class EncodedEnum extends EncodedValue`

Invariant: the payload must be the constant's own static field, which is not checked; it wraps the `FieldId` type of `EncodedField` under another tag, so the two are never equal.

**Methods**

| Signature |
|---|
| `public static EncodedEnum of(FieldId)` |
| `public FieldId getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedArray

`public final class EncodedArray extends EncodedValue`

Invariant: untyped and order-sensitive; emptiness is canonical, so `of()`, `of(null)`, `of(empty)` and `raw(empty)` all return `EMPTY`, and `raw` stores your list without copying.

**Fields** — `public static final EncodedArray`: `EMPTY`

**Methods**

| Signature |
|---|
| `@Internal public static EncodedArray raw(List<EncodedValue>)` |
| `public static EncodedArray of(Iterable<EncodedValue>)` |
| `public static EncodedArray of(EncodedValue...)` |
| `public List<EncodedValue> getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedAnnotation

`public final class EncodedAnnotation extends EncodedValue implements CommonAnnotation`

Invariant: elements live in a name-ordered set, so the written order is name order; `of` rejects a duplicate name with `IllegalArgumentException`, while `raw` and a plain `TreeSet` keep the first.

**Methods**

| Signature |
|---|
| `@Internal public static EncodedAnnotation raw(TypeId, NavigableSet<AnnotationElement>)` |
| `public static EncodedAnnotation of(TypeId, Iterable<AnnotationElement>)` |
| `public static EncodedAnnotation of(TypeId, AnnotationElement...)` |
| `public static EncodedAnnotation of(CommonAnnotation)` |
| `public TypeId getType()` |
| `public NavigableSet<AnnotationElement> getElements()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedNull

`public final class EncodedNull extends EncodedValue`

Invariant: no factory exists; second to last in the total order, between `EncodedAnnotation` and `EncodedBoolean`.

**Fields** — `public static final EncodedNull`: `INSTANCE`

**Methods**

| Signature |
|---|
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## com.v7878.dex.immutable.value.EncodedBoolean

`public final class EncodedBoolean extends EncodedValue`

Invariant: exactly two instances, so `of(x) == of(x)` always holds and `of` never allocates.

**Fields** — `public static final EncodedBoolean`: `TRUE` `FALSE`

**Methods**

| Signature |
|---|
| `public static EncodedBoolean of(boolean)` |
| `public boolean getValue()` |
| `public ValueType getValueType()` |
| `public boolean isDefault()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public int compareTo(EncodedValue)` |

## Example

```java
FieldDef counter = FieldDef.of("COUNTER", TypeId.I, ACC_PUBLIC | ACC_STATIC | ACC_FINAL, 0,
        EncodedInt.of(7), List.of());
FieldDef cache = FieldDef.of("CACHE", TypeId.of("Ljava/util/Map;"), ACC_PUBLIC | ACC_STATIC, 0,
        EncodedValue.defaultValue(TypeId.of("Ljava/util/Map;")), List.of());

System.out.println(cache.getInitialValue() == EncodedNull.INSTANCE);   // true
System.out.println(counter.getInitialValue().isDefault());            // false
System.out.println(EncodedValue.ofValue(new int[]{1, 2}).getValueType()); // ARRAY
```

Up: [SKILL.md](../SKILL.md)
