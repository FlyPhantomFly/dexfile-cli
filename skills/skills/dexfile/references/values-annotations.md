# Annotations

Four types: `Annotatable`, `CommonAnnotation`, `Annotation`, `AnnotationElement`. `Annotation`
adds a visibility to the type-plus-elements contract; `EncodedAnnotation` is the same payload without.

## com.v7878.dex.immutable.Annotatable

`public sealed interface Annotatable permits DexIO.ClassHeader, ClassDef, MemberDef, Parameter`

Invariant: the set is keyed by `Annotation.compareTo`, which is the type only, so a carrier holds at
most one annotation per type; the `of` factories reject a second one with
`IllegalArgumentException`.

**Methods**

| Signature | Note |
|---|---|
| `public abstract NavigableSet<Annotation> getAnnotations()` | ordered by type |
| `public default Annotation findAnnotation(TypeId)` | floor lookup; null when absent |

## com.v7878.dex.immutable.CommonAnnotation

`public sealed interface CommonAnnotation permits Annotation, EncodedAnnotation`

**Methods**

| Signature | Note |
|---|---|
| `public abstract TypeId getType()` | |
| `public abstract NavigableSet<AnnotationElement> getElements()` | ordered by name |
| `public default AnnotationElement findElement(String)` | floor lookup by name |

## com.v7878.dex.immutable.Annotation

`public final class Annotation implements CommonAnnotation, Comparable<Annotation>`

Invariant: `compareTo` compares the type only, while `equals` and `hashCode` also see the
visibility and the element values. Visibility is build 0x00, runtime 0x01 or system 0x02, printed in
lowercase. `InnerClass` validates its flags and `MethodParameters` requires equal array lengths,
both with `IllegalArgumentException`.

**Constructors** — `private Annotation(AnnotationVisibility, TypeId, NavigableSet<AnnotationElement>)`

**Methods**

| Signature |
|---|
| `@Internal public static Annotation raw(AnnotationVisibility, TypeId, NavigableSet<AnnotationElement>)` |
| `public static Annotation of(AnnotationVisibility, TypeId, Iterable<AnnotationElement>)` |
| `public static Annotation of(AnnotationVisibility, TypeId, AnnotationElement...)` |
| `public static Annotation of(AnnotationVisibility, CommonAnnotation)` |
| `public static Annotation FastNative()` |
| `public static Annotation CriticalNative()` |
| `public static Annotation AnnotationDefault(CommonAnnotation)` |
| `public static Annotation EnclosingClass(TypeId)` |
| `public static Annotation EnclosingMethod(MethodId)` |
| `public static Annotation InnerClass(String, int)` |
| `public static Annotation MemberClasses(TypeId...)` |
| `public static Annotation MemberClasses(Iterable<TypeId>)` |
| `public static Annotation MethodParameters(String[], int[])` |
| `public static Annotation Signature(String...)` |
| `public static Annotation Signature(Iterable<String>)` |
| `public static Annotation Throws(TypeId...)` |
| `public static Annotation Throws(Iterable<TypeId>)` |
| `public AnnotationVisibility getVisibility()` |
| `public TypeId getType()` |
| `public NavigableSet<AnnotationElement> getElements()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public String toString()` |
| `public int compareTo(Annotation)` |

The varargs `of` also builds the no-element form; `of(visibility, CommonAnnotation)` shares the
element set; `toString()` is the debug form
`Annotation{visibility=BUILD type=Lfoo; elements=N}`. `Annotation.of` takes a `null` element
collection as empty, and `MemberClasses`, `Signature` and `Throws` accept `null` (iterable or varargs
array) too: their element value is then an empty `EncodedArray`, never `EncodedNull` -
`EncodedValue.ofValue(null)` still returns `EncodedNull` (values.md).

## com.v7878.dex.immutable.AnnotationElement

`public final class AnnotationElement implements Comparable<AnnotationElement>`

Invariant: order is by name only, so two elements with one name are one key.

**Constructors** — `private AnnotationElement(String, EncodedValue)`

**Methods**

| Signature |
|---|
| `public static AnnotationElement of(String, EncodedValue)` |
| `public String getName()` |
| `public EncodedValue getValue()` |
| `public int hashCode()` |
| `public boolean equals(Object)` |
| `public String toString()` |
| `public int compareTo(AnnotationElement)` |

`of` rejects a null name or value; the value must already be an `EncodedValue`; `toString()` is
`AnnotationElement{name=x type=EncodedInt}`.

## Payload shape

An encoded annotation costs one header byte (0x1d), then a `uleb128` type index, a `uleb128`
element count, then per element a `uleb128` name index followed by the value. A class-level
`Annotation` writes its visibility byte instead of 0x1d, with identical bytes after it.

- Elements are emitted in name order; an array element is an untyped `EncodedArray` and its name
  never carries `[]`; a nested annotation element is an `EncodedAnnotation` value with no
  visibility.
- `EncodedValue.ofValue` accepts any `CommonAnnotation`; a reflection annotation proxy is not one
  and throws `UnsupportedOperationException` (not implemented).

## Standard payloads

The descriptor is `Ldalvik/annotation/Name;`, or `Ldalvik/annotation/optimization/Name;` for the
last two. System: `Signature` (string array), `Throws` and `MemberClasses` (type array),
`EnclosingClass` (type), `EnclosingMethod` (method), `InnerClass` (name, accessFlags),
`MethodParameters` (names, accessFlags), `AnnotationDefault` (encoded annotation). Build:
`FastNative`, `CriticalNative`, no elements.

## Example

```java
Annotation flag = Annotation.of(AnnotationVisibility.RUNTIME, TypeId.of("Ldemo/Flag;"),
        AnnotationElement.of("name", EncodedString.of("verbose")),
        AnnotationElement.of("count", EncodedInt.of(2)));

System.out.println(flag);                                       // @Ldemo/Flag;
System.out.println(flag.findElement("count").getValue().getValueType()); // INT
System.out.println(EncodedAnnotation.of(flag).getElements());   // same set, no visibility
```

Up: [SKILL.md](../SKILL.md)
