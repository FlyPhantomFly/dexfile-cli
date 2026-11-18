Up: [SKILL.md](../SKILL.md)

# Access flags, format constants, annotation visibility

`AccessFlag` is the typed view of an `access_flags` mask, `DexConstants` is the raw
`public static final int` layer of the format, `AnnotationVisibility` is the visibility byte of an
`annotation_item`. All three are stateless holders: no instance state, no lookup table, and their
values are compile-time constants usable in `switch` labels.

## com.v7878.dex.AccessFlag

`public enum AccessFlag`

Invariant: a bit alone does not name a flag - `0x20` is `synchronized` on a method and `super` on a
class; read a mask with the `getAccessFlagsFor…` method of the owning entity.

**Fields** — `public static final AccessFlag`: `PUBLIC` `PRIVATE` `PROTECTED` `STATIC` `FINAL` `SYNCHRONIZED` `SUPER` `VOLATILE` `BRIDGE` `TRANSIENT` `VARARGS` `NATIVE` `INTERFACE` `ABSTRACT` `STRICTFP` `SYNTHETIC` `ANNOTATION` `ENUM` `MANDATED` `CONSTRUCTOR` `DECLARED_SYNCHRONIZED` `VERIFIED` `OPTIMIZED`

**Fields** — `public static final int`: `VALID_CLASS_FLAGS_MASK` `VALID_INNER_CLASS_FLAGS_MASK` `VALID_METHOD_FLAGS_MASK` `VALID_PARAMETER_FLAGS_MASK` `VALID_FIELD_FLAGS_MASK`

Bit, `toString` and validity; C = class, I = inner class, M = method, P = parameter, F = field, and
`/` separates the two names that share one bit (23 constants cover 18 bits):

| Bit | Constant(s) | toString | Valid for |
|---|---|---|---|
| 0x1 | PUBLIC | public | C I M F |
| 0x2 | PRIVATE | private | I M F |
| 0x4 | PROTECTED | protected | I M F |
| 0x8 | STATIC | static | I M F |
| 0x10 | FINAL | final | C I M P F |
| 0x20 | SYNCHRONIZED / SUPER | synchronized / super | M / C I |
| 0x40 | VOLATILE / BRIDGE | volatile / bridge | F / M |
| 0x80 | TRANSIENT / VARARGS | transient / varargs | F / M |
| 0x100 | NATIVE | native | M |
| 0x200 | INTERFACE | interface | C I |
| 0x400 | ABSTRACT | abstract | C I M |
| 0x800 | STRICTFP | strictfp | M |
| 0x1000 | SYNTHETIC | synthetic | C I M P F |
| 0x2000 | ANNOTATION | annotation | C I |
| 0x4000 | ENUM | enum | C I F |
| 0x8000 | MANDATED | mandated | P |
| 0x10000 | CONSTRUCTOR / VERIFIED | constructor / verified | M / C |
| 0x20000 | DECLARED_SYNCHRONIZED / OPTIMIZED | declared-synchronized / optimized | M / C |

`MANDATED` is legal only for a parameter and appears only inside the
`dalvik.annotation.MethodParameters` annotation - `Parameter` itself carries no flags. `VERIFIED`
and `OPTIMIZED` are odex-era class flags: the model accepts them, but the reader and the writer
reject a `class_def_item` with either bit unless `isTargetForDalvik() && hasOdexInstructions()`
(read → `InvalidDexFile`, write → `IllegalStateException`).

Masks are `combine` of the valid constants: class `0x37631`, inner class `0x763F`, method `0x31DFF`,
parameter `0x9010`, field `0x50DF`. The role tables are private (14 method, 3 parameter, 9 field
flags in declaration order); `getAccessFlagsFor*` filters a private table and returns a fresh array
on every call, so mutating the result changes neither the table nor a later call.

**Methods**

| Signature | Note |
|---|---|
| `public static AccessFlag[] values()` | declaration order |
| `public static AccessFlag valueOf(String name)` | Java constant name |
| `public static int combine(AccessFlag... flags)` | OR of `value()`; `0` when empty |
| `public static boolean isValidForClass(int flags)` | mask test |
| `public static boolean isValidForInnerClass(int flags)` | mask test |
| `public static boolean isValidForMethod(int flags)` | mask test |
| `public static boolean isValidForParameter(int flags)` | mask test |
| `public static boolean isValidForField(int flags)` | mask test |
| `public static AccessFlag[] getAccessFlagsForClass(int flags)` | class-valid flags, new array |
| `public static String formatAccessFlagsForClass(int flags)` | names, then `[1 << N]` for outside bits |
| `public static AccessFlag[] getAccessFlagsForInnerClass(int flags)` | |
| `public static String formatAccessFlagsForInnerClass(int flags)` | |
| `public static AccessFlag[] getAccessFlagsForMethod(int flags)` | |
| `public static String formatAccessFlagsForMethod(int flags)` | |
| `public static AccessFlag[] getAccessFlagsForParameter(int flags)` | |
| `public static String formatAccessFlagsForParameter(int flags)` | |
| `public static AccessFlag[] getAccessFlagsForField(int flags)` | |
| `public static String formatAccessFlagsForField(int flags)` | |
| `public boolean isSet(int flags)` | `(value() & flags) != 0` - alias-blind |
| `public int value()` | the numeric bit |
| `public String toString()` | the DEX name |
| `public static AccessFlag of(String name)` | DEX name; unknown name → `IllegalArgumentException` |

`isValidFor…` is a mask test only: `flags == (flags & MASK)`. It accepts `0`, accepts
`PUBLIC | PRIVATE`, ignores aliases and requires no visibility bit. The combination rules live in
the model factories (`Preconditions.check*AccessFlags`, called by `of` and `raw` alike): field
`final`+`volatile`, method `abstract` with `private`/`static`/`final`/`native`/`synchronized`/
`strictfp`, constructor bodies and the `<init>` flag whitelist (model-invariants.md). Class flags
stay mask-only: `abstract`+`final` on a class and a `final` interface are model-legal and fail
when the class is linked, not when the dex is read (ART treats every interface as abstract, so
`ACC_ABSTRACT` is irrelevant there). `formatAccessFlagsFor…` prints the role's flag names in table order, then every set
bit outside the role mask ascending as `[1 << N]` (`0x400000` → `[1 << 22]`, `ACC_STATIC` on a
class → `[1 << 3]`); zero flags give the empty string, never `null`, and nothing is dropped.

`of` and `valueOf` take different alphabets: `of("public")` and `of("declared-synchronized")` work,
`of("PUBLIC")` throws; `valueOf("PUBLIC")` works, `valueOf("public")` throws. Both throw
`NullPointerException` for `null`. Parsing dex text needs `of`, Java-style configuration needs
`valueOf`.

## com.v7878.dex.DexConstants

`public final class DexConstants`

Invariant: `NO_INDEX` is `-1` and `NO_OFFSET` is `0`, and the two are not interchangeable;
`TYPE_*` and `VALUE_*` codes are sparse, so never compute one as `base + n`.

**Fields** — `public static final int`, API sentinels: `API_M5` `API_M3`

`API_M5` is `0`, the only API `DEX013` accepts. `API_M3` is `-1`, the only API `DEX009` accepts and
the lowest target API the option layer allows.

**Fields** — `public static final int`, service values: `ENDIAN_CONSTANT` `REVERSE_ENDIAN_CONSTANT` `NO_INDEX` `NO_OFFSET` `MAX_POOL_SIZE`

`ENDIAN_CONSTANT` is `0x12345678` and `REVERSE_ENDIAN_CONSTANT` `0x78563412`; the reader rejects a
big-endian file with `DexIO.InvalidDexFile`, so the reverse value exists for diagnostics only.
`NO_INDEX` (`-1`) marks an absent index and reads back as `null`; `NO_OFFSET` (`0`) marks an absent
offset and reads back as an empty section or a `null` body. `MAX_POOL_SIZE` is `65534` - `65535`
index slots are reserved - and every pool section must stay at or below it.

**Fields** — `public static final int`, access flags: `ACC_PUBLIC` `ACC_PRIVATE` `ACC_PROTECTED` `ACC_STATIC` `ACC_FINAL` `ACC_SYNCHRONIZED` `ACC_SUPER` `ACC_VOLATILE` `ACC_BRIDGE` `ACC_TRANSIENT` `ACC_VARARGS` `ACC_NATIVE` `ACC_INTERFACE` `ACC_ABSTRACT` `ACC_STRICTFP` `ACC_SYNTHETIC` `ACC_ANNOTATION` `ACC_ENUM` `ACC_MANDATED` `ACC_CONSTRUCTOR` `ACC_VERIFIED` `ACC_DECLARED_SYNCHRONIZED` `ACC_OPTIMIZED` `ACC_VISIBILITY_MASK` `ACC_DIRECT_MASK`

The `ACC_*` bits are exactly the `AccessFlag` values above (including the five alias pairs).
`ACC_VISIBILITY_MASK` is `0x7` and `ACC_DIRECT_MASK` is `0x1000A` - `ACC_PRIVATE | ACC_STATIC |
ACC_CONSTRUCTOR` - which is the definition of a direct method: `MethodDef.isDirect()` is
`(flags & ACC_DIRECT_MASK) != 0`, so a constructor is direct even when neither private nor static.

**Fields** — `public static final int`, map item types: `TYPE_HEADER_ITEM` `TYPE_STRING_ID_ITEM` `TYPE_TYPE_ID_ITEM` `TYPE_PROTO_ID_ITEM` `TYPE_FIELD_ID_ITEM` `TYPE_METHOD_ID_ITEM` `TYPE_CLASS_DEF_ITEM` `TYPE_CALL_SITE_ID_ITEM` `TYPE_METHOD_HANDLE_ITEM` `TYPE_MAP_LIST` `TYPE_TYPE_LIST` `TYPE_ANNOTATION_SET_REF_LIST` `TYPE_ANNOTATION_SET_ITEM` `TYPE_CLASS_DATA_ITEM` `TYPE_CODE_ITEM` `TYPE_STRING_DATA_ITEM` `TYPE_DEBUG_INFO_ITEM` `TYPE_ANNOTATION_ITEM` `TYPE_ENCODED_ARRAY_ITEM` `TYPE_ANNOTATIONS_DIRECTORY_ITEM` `TYPE_HIDDENAPI_CLASS_DATA_ITEM`

These are the `DexIO.DexMapEntry.type()` codes. Header and id sections are `0x0000`-`0x0008`
consecutive in the listed order, metadata `0x1000`-`0x1003`, data `0x2000`-`0x2006`;
`TYPE_HIDDENAPI_CLASS_DATA_ITEM` is `0xF000`. `0x0009`-`0x0FFF`, `0x1004`-`0x1FFF` and
`0x2007`-`0xEFFF` are unassigned.

**Fields** — `public static final int`, encoded value tags: `VALUE_BYTE` `VALUE_SHORT` `VALUE_CHAR` `VALUE_INT` `VALUE_LONG` `VALUE_FLOAT` `VALUE_DOUBLE` `VALUE_METHOD_TYPE` `VALUE_METHOD_HANDLE` `VALUE_STRING` `VALUE_TYPE` `VALUE_FIELD` `VALUE_METHOD` `VALUE_ENUM` `VALUE_ARRAY` `VALUE_ANNOTATION` `VALUE_NULL` `VALUE_BOOLEAN`

These are ascending with gaps in the listed order: `0x00`, `0x02`, `0x03`, `0x04`, `0x06`,
`0x10`, `0x11`, then `0x15`-`0x1F` consecutive from `VALUE_METHOD_TYPE` to `VALUE_BOOLEAN`.
`0x01`, `0x05`, `0x07`-`0x0F` and `0x12`-`0x14` are unassigned by the format. `ValueType` returns
the same numbers but declares `BOOLEAN` (`0x1F`) first, so enum order is not tag order.

**Fields** — `public static final int`, method handle types: `METHOD_HANDLE_TYPE_STATIC_PUT` `METHOD_HANDLE_TYPE_STATIC_GET` `METHOD_HANDLE_TYPE_INSTANCE_PUT` `METHOD_HANDLE_TYPE_INSTANCE_GET` `METHOD_HANDLE_TYPE_INVOKE_STATIC` `METHOD_HANDLE_TYPE_INVOKE_INSTANCE` `METHOD_HANDLE_TYPE_INVOKE_CONSTRUCTOR` `METHOD_HANDLE_TYPE_INVOKE_DIRECT` `METHOD_HANDLE_TYPE_INVOKE_INTERFACE`

`0x00`-`0x08` in the listed order: `0x00`-`0x03` reference a field, `0x04`-`0x08` reference a
method.

**Fields** — `public static final int`, annotation visibility: `VISIBILITY_BUILD` `VISIBILITY_RUNTIME` `VISIBILITY_SYSTEM`

`0`, `1`, `2`; the first byte of an `annotation_item`.

**Fields** — `public static final int`, hiddenapi restriction flags: `HIDDENAPI_FLAG_SDK` `HIDDENAPI_FLAG_UNSUPPORTED` `HIDDENAPI_FLAG_BLOCKED` `HIDDENAPI_FLAG_MAX_TARGET_O` `HIDDENAPI_FLAG_MAX_TARGET_P` `HIDDENAPI_FLAG_MAX_TARGET_Q` `HIDDENAPI_FLAG_MAX_TARGET_R` `HIDDENAPI_FLAG_MAX_TARGET_S`

`0`-`7` in the listed order. Value `0` is meaningful: it means "SDK, not restricted", which is what
`MemberDef.getHiddenApiFlags()` reports for an unrestricted member. The section is read only when
hiddenapi flags are enabled in the read options. The bits are version-dependent and stay verbatim:
`Preconditions.checkHiddenApiFlags` is a no-op placeholder, not a validation.

**Fields** — `public static final int`, debug info opcodes: `DBG_END_SEQUENCE` `DBG_ADVANCE_PC` `DBG_ADVANCE_LINE` `DBG_START_LOCAL` `DBG_START_LOCAL_EXTENDED` `DBG_END_LOCAL` `DBG_RESTART_LOCAL` `DBG_SET_PROLOGUE_END` `DBG_SET_EPILOGUE_BEGIN` `DBG_SET_FILE` `DBG_FIRST_SPECIAL` `DBG_LAST_SPECIAL` `DBG_LINE_BASE` `DBG_LINE_RANGE`

The ten opcodes are `0x00`-`0x09` in the listed order, then `DBG_FIRST_SPECIAL` `0x0A`,
`DBG_LAST_SPECIAL` `0xFF`, `DBG_LINE_BASE` `-4` and `DBG_LINE_RANGE` `15`. The last two are bounds
of the packed line delta used by the special range, not opcodes.

**Fields** — `public static final int`, M5 debug info opcodes: `M5_DBG_END_SEQUENCE` `M5_DBG_ADVANCE_PC` `M5_DBG_ADVANCE_LINE` `M5_DBG_START_LOCAL` `M5_DBG_END_LOCAL` `M5_DBG_RESTART_LOCAL` `M5_DBG_SET_PROLOGUE_END` `M5_DBG_SET_EPILOGUE_BEGIN` `M5_DBG_START_LOCAL_EXTENDED` `M5_DBG_SET_FILE` `M5_DBG_FIRST_SPECIAL` `M5_DBG_LAST_SPECIAL` `M5_DBG_LINE_BASE` `M5_DBG_LINE_RANGE`

Also `0x00`-`0x09` in the listed order, but the three local opcodes are permuted against the modern
set: M5 has `END_LOCAL` `0x04` and `START_LOCAL_EXTENDED` `0x08`, while the modern set has
`START_LOCAL_EXTENDED` `0x04`, `END_LOCAL` `0x05` and `RESTART_LOCAL` `0x06`. `0x04` therefore
means different things in a `dex\n009`/`dex\n013` stream and a modern one; never decode an M5
stream with the `DBG_*` constants.

## com.v7878.dex.AnnotationVisibility

`public enum AnnotationVisibility`

Invariant: declaration order equals value order, so `values()[i].value() == i` and the enum's
natural order is numeric order.

**Fields** — `public static final AnnotationVisibility`: `BUILD` `RUNTIME` `SYSTEM`

`0x00` build, `0x01` runtime, `0x02` system; `toString()` returns exactly those lowercase DEX
names.

**Methods**

| Signature | Note |
|---|---|
| `public static AnnotationVisibility[] values()` | `BUILD` `RUNTIME` `SYSTEM` |
| `public static AnnotationVisibility valueOf(String name)` | Java constant name |
| `public int value()` | the visibility byte |
| `public static AnnotationVisibility of(String name)` | DEX name, lowercase |
| `public static AnnotationVisibility of(int value)` | the byte |
| `public String toString()` | the DEX name |

Both `of` overloads throw `IllegalArgumentException` for an unknown visibility;
`of(String)` throws `NullPointerException` for `null`.
