Up: [SKILL.md](../SKILL.md)

# Versions, encoded value tags, method handles

Five enums classify fixed numbers: `DexVersion` (magic and API window), `ValueType` (encoded-value
tag), `MethodHandleType` (method-handle code), `ReferenceType` (operand kind) and
`VerificationError` (odex verification-failure kind). Only `DexVersion` has behaviour.

## com.v7878.dex.DexVersion

`public enum DexVersion`

Invariant: the window is inclusive on both ends, and `forApi` walks declaration order (newest
first), not version order; compare magics only through `forMagic` or `getMagicArray`.

**Fields** — `public static final DexVersion`: `DEX041` `DEX040` `DEX039` `DEX038` `DEX037` `DEX036` `DEX035` `DEX013` `DEX009` `CDEX001`

Magic: `dex\n<version>\0` for the nine `DEX*` versions, `cdex001\0` for `CDEX001`.

| Version | Min API | Max API | Note |
|---|---|---|---|
| DEX041 | 35 | Integer.MAX_VALUE | container, API 35 floor |
| DEX040 | 30 | Integer.MAX_VALUE | |
| DEX039 | 28 | Integer.MAX_VALUE | |
| DEX038 | 26 | Integer.MAX_VALUE | |
| DEX037 | 24 | Integer.MAX_VALUE | |
| DEX036 | 14 | 20 | never completed, non-standard |
| DEX035 | 1 | Integer.MAX_VALUE | |
| DEX013 | 0 | 0 | M5 sentinel, read-only |
| DEX009 | -1 | -1 | M3 sentinel, read-only |
| CDEX001 | 28 | 35 | compact, non-standard |

`DEX036` added the `/jumbo` instructions while the rest stayed at 035, API 16 removed the
difference and API 21 declared it non-existent; the library reads and writes it but keeps it out of
`forApi`. `DEX013` pins both ends to `API_M5` (0) and `DEX009` to `API_M3` (-1).

**Methods**

| Signature | Note |
|---|---|
| `public static DexVersion[] values()` | |
| `public static DexVersion valueOf(String name)` | |
| `public long getMagic()` | little-endian |
| `public byte[] getMagicArray()` | file order |
| `public boolean isCompact()` | CDEX001 |
| `public boolean isDexContainer()` | DEX041 |
| `public int getMinApi()` | |
| `public int getMaxApi()` | |
| `public void checkApi(int api)` | throws outside the window |
| `public boolean isStandard()` | |
| `public static DexVersion forApi(int api)` | |
| `public static DexVersion forMagic(long magic)` | |

`forMagic` throws `IllegalArgumentException` for an unknown magic, which `DexIO.readCache`
rethrows as `DexIO.NotADexFile` with that exception as the cause. `checkApi` throws `IllegalArgumentException`
when the target API is below the version's minimum or above its maximum; it is a support check, so
`DEX035` accepts API 35 just as `DEX041` does, and both reader and writer call it before touching
bytes.

`forApi` returns the first standard constant with `getMinApi() <= api`: `-1` → `DEX009`, `0` →
`DEX013`, 1-23 → `DEX035`, 24-25 → `DEX037`, 26-27 → `DEX038`, 28-29 → `DEX039`, 30-34 →
`DEX040`, 35 and above → `DEX041`. Below -1 it throws `IllegalArgumentException` (no version for that
API); above 37 it still returns `DEX041`, because the option
layer owns the cap. `forApi(15)` is `DEX035`, never `DEX036`.

The properties are independent: compact (CDEX001, header 136 instead of 112, separate debug-info
offset table), container (DEX041, several components, see `DexIO.writeDexContainer`), legacy
(DEX009, DEX013, read-only - writing raises `DexIO.InvalidDexFile`). Writing one `Dex` as `DEX041`
gives a one-component container, not a plain file.

## com.v7878.dex.ValueType

`public enum ValueType`

Invariant: the tag is not the ordinal - `BOOLEAN` has tag `0x1F` and is declared first, so
`values()` and `compareTo` are not tag order; order numerically with `ValueType.compare`.

**Fields** — `public static final ValueType`: `BOOLEAN` `BYTE` `SHORT` `CHAR` `INT` `FLOAT` `LONG` `DOUBLE` `METHOD_TYPE` `METHOD_HANDLE` `STRING` `TYPE` `FIELD` `METHOD` `ENUM` `ARRAY` `ANNOTATION` `NULL`

| Tag | Constant | getType() | Encoded class |
|---|---|---|---|
| 0x1F | BOOLEAN | Z | EncodedBoolean |
| 0x00 | BYTE | B | EncodedByte |
| 0x02 | SHORT | S | EncodedShort |
| 0x03 | CHAR | C | EncodedChar |
| 0x04 | INT | I | EncodedInt |
| 0x10 | FLOAT | F | EncodedFloat |
| 0x06 | LONG | J | EncodedLong |
| 0x11 | DOUBLE | D | EncodedDouble |
| 0x15 | METHOD_TYPE | Ljava/lang/invoke/MethodType; | EncodedMethodType |
| 0x16 | METHOD_HANDLE | Ljava/lang/invoke/MethodHandle; | EncodedMethodHandle |
| 0x17 | STRING | Ljava/lang/String; | EncodedString |
| 0x18 | TYPE | Ljava/lang/Class; | EncodedType |
| 0x19 | FIELD | Ljava/lang/reflect/Field; | EncodedField |
| 0x1A | METHOD | Ljava/lang/reflect/Executable; | EncodedMethod |
| 0x1B | ENUM | Ljava/lang/Enum; | EncodedEnum |
| 0x1C | ARRAY | Ljava/lang/reflect/Array; | EncodedArray |
| 0x1D | ANNOTATION | Ljava/lang/annotation/Annotation; | EncodedAnnotation |
| 0x1E | NULL | Ljava/lang/Object; | EncodedNull |

`METHOD` covers methods and constructors because a `method_id` does not distinguish them, hence
`Executable`. `ARRAY` is untyped with heterogeneous elements, hence the `reflect.Array` marker;
`NULL` uses `Object`.

**Methods**

| Signature | Note |
|---|---|
| `public static ValueType[] values()` | declaration order |
| `public static ValueType valueOf(String name)` | |
| `public int value()` | the tag |
| `public TypeId getType()` | TypeId of the Java class |
| `public static ValueType of(int value)` | |
| `public static int compare(ValueType left, ValueType right)` | by tag |

`of(int)` throws `IllegalArgumentException` for `0x01`,
`0x05`, `0x07`-`0x0F` and `0x12`-`0x14`. `values()[0]` is `BOOLEAN` (`0x1F`), so a `TreeSet`
without a comparator is in declaration order; only `compare` is in tag order.

## com.v7878.dex.MethodHandleType

`public enum MethodHandleType`

Invariant: `value()` is the code in `method_handle_item`; `MethodHandleId.of(type, member)` checks
`isMethodAccess()` with `instanceof`, so a mismatched pair throws `IllegalArgumentException` naming
the expected member class (`NullPointerException` only for `null`).

**Fields** — `public static final MethodHandleType`: `STATIC_PUT` `STATIC_GET` `INSTANCE_PUT` `INSTANCE_GET` `INVOKE_STATIC` `INVOKE_INSTANCE` `INVOKE_CONSTRUCTOR` `INVOKE_DIRECT` `INVOKE_INTERFACE`

**Methods**

| Signature | Note |
|---|---|
| `public static MethodHandleType[] values()` | declaration order |
| `public static MethodHandleType valueOf(String name)` | |
| `public int value()` | 0x00-0x08 |
| `public boolean isMethodAccess()` | |
| `public boolean isFieldAccess()` | |
| `public static MethodHandleType of(String name)` | |
| `public static MethodHandleType of(int value)` | |
| `public static int compare(MethodHandleType left, MethodHandleType right)` | by code |
| `public String toString()` | the DEX name |

This is the only enum in the package where declaration order equals code order
(`values()[i].value() == i`). `of(String)` takes DEX names (`static-put`, `invoke-instance`),
`valueOf` takes Java names; both `of` overloads throw `IllegalArgumentException` for an unknown
type, and `of(String)` throws `NullPointerException` for `null`.

## Method handle kinds and reference kinds

The classifiers are independent: the left half is the `method_handle_item` code, the right half is
the operand kind an instruction declares.

| MethodHandleType (code) | Member required | ReferenceType | Operand class |
|---|---|---|---|
| STATIC_PUT (0x00) | FieldId | STRING | java.lang.String |
| STATIC_GET (0x01) | FieldId | TYPE | TypeId |
| INSTANCE_PUT (0x02) | FieldId | PROTO | ProtoId |
| INSTANCE_GET (0x03) | FieldId | FIELD | FieldId |
| INVOKE_STATIC (0x04) | MethodId | METHOD | MethodId |
| INVOKE_INSTANCE (0x05) | MethodId | CLASS_DEF | TypeId |
| INVOKE_CONSTRUCTOR (0x06) | MethodId | CALLSITE | CallSiteId |
| INVOKE_DIRECT (0x07) | MethodId | METHOD_HANDLE | MethodHandleId |
| INVOKE_INTERFACE (0x08) | MethodId | RAW_INDEX | java.lang.Integer |

`MethodHandleId.toString()` joins the kind and the member with `@`
(`invoke-instance@Ldemo/Box;->size()I`), which is what `ReferenceType.describe(METHOD_HANDLE, id)`
returns.

## com.v7878.dex.ReferenceType

`public enum ReferenceType`

Invariant: there is no numeric form - the kind is inferred from the instruction format and the pool
section, so never persist `ordinal()`; `validate` is a checked cast, not a predicate.

**Fields** — `public static final ReferenceType`: `STRING` `TYPE` `PROTO` `FIELD` `METHOD` `CLASS_DEF` `CALLSITE` `METHOD_HANDLE` `RAW_INDEX`

**Methods**

| Signature | Note |
|---|---|
| `public static ReferenceType[] values()` | declaration order |
| `public static ReferenceType valueOf(String name)` | |
| `public static Object validate(ReferenceType type, Object value)` | checked cast |
| `public static String describe(ReferenceType type, Object value)` | validate, then format |

`validate` calls `Objects.requireNonNull(value)` and casts per branch: a `null` value or a `null`
type throws `NullPointerException`, a value of the wrong class throws `ClassCastException`, and the
result is the same instance. `describe` validates first and formats `STRING` in double quotes,
`CLASS_DEF` with a `def@` prefix, `RAW_INDEX` as `@` plus the integer, and every other kind with
`toString()`.

## com.v7878.dex.VerificationError

`public enum VerificationError`

Invariant: the kind of a `throw-verification-error` operand (`InstructionN0ci`), codes `1..9`;
`no-error` (code 0) is not modelled, so both `of` overloads reject it.

**Fields** — `public static final VerificationError`: `GENERIC` `NO_SUCH_CLASS` `NO_SUCH_FIELD` `NO_SUCH_METHOD` `ILLEGAL_CLASS_ACCESS` `ILLEGAL_FIELD_ACCESS` `ILLEGAL_METHOD_ACCESS` `CLASS_CHANGE` `INSTANTIATION`

| Code | Constant | Smali name |
|---|---|---|
| 1 | GENERIC | generic-error |
| 2 | NO_SUCH_CLASS | no-such-class |
| 3 | NO_SUCH_FIELD | no-such-field |
| 4 | NO_SUCH_METHOD | no-such-method |
| 5 | ILLEGAL_CLASS_ACCESS | illegal-class-access |
| 6 | ILLEGAL_FIELD_ACCESS | illegal-field-access |
| 7 | ILLEGAL_METHOD_ACCESS | illegal-method-access |
| 8 | CLASS_CHANGE | class-change-error |
| 9 | INSTANTIATION | instantiation-error |

**Methods**

| Signature | Note |
|---|---|
| `public static VerificationError[] values()` | declaration order |
| `public static VerificationError valueOf(String name)` | |
| `public int code()` | 1..9 |
| `public static VerificationError of(String name)` | smali name; `IllegalArgumentException` when absent |
| `public static VerificationError of(int code)` | 1..9; `IllegalArgumentException` when absent |
| `public String toString()` | the smali name |
