Up: [SKILL.md](../SKILL.md)

# DexIO

`DexIO` is the only class that touches dex bytes: it reads `dex`/`cdex`/`DEX041`/odex payloads,
writes the model back and packs classes into several dex files. `readCache` is lazy per element;
`read` adds `getClasses()`.

## com.v7878.dex.DexIO

`public final class DexIO`

**Methods**

| Signature | Note |
|---|---|
| `public static DexReaderCache readCache(ReadOptions, byte[], int, int, int)` | 3 offsets |
| `public static DexReaderCache readCache(ReadOptions, byte[], int, int)` | `content_offset = 0` |
| `public static DexReaderCache readCache(byte[])` | default options |
| `public static Dex read(ReadOptions, byte[], int, int, int[])` | `ids` = class indices |
| `public static Dex read(ReadOptions, byte[], int, int)` | all classes |
| `public static Dex read(byte[])` | default options |
| `public static DexReaderCache[] readDexContainerCache(ReadOptions, byte[], int)` | |
| `public static Dex[] readDexContainer(ReadOptions, byte[], int)` | |
| `public static int balance(WriteOptions, java.util.List<ClassDef>, java.util.List<Dex>, boolean)` | classes consumed |
| `public static void balanceMove(WriteOptions, java.util.List<ClassDef>, java.util.List<Dex>, boolean)` | removes that prefix |
| `public static java.util.List<Dex> balanceAll(WriteOptions, java.util.List<ClassDef>)` | unmodifiable |
| `public static byte[] write(WriteOptions, Dex)` | |
| `public static byte[] write(Dex)` | default options |
| `public static byte[] writeDexContainer(WriteOptions, Dex...)` | |

### Reading

`data_offset` starts the dex region; in-file offsets are relative to it. `header_offset` is the
header position relative to it: `0` for a plain dex (else `InvalidDexFile`) or the component offset
in a `DEX041` container.
An embedded dex or odex payload (`dey\n…`) uses its start as `data_offset` and `header_offset = 0`;
`content_offset` (normally `0`) rebases data-section offsets.

Checked in this order:

1. `options` null → `NullPointerException`; `validate()` → `IllegalArgumentException`.
2. `header_offset < 0` → `IllegalArgumentException`.
3. Too short → `NotADexFile` (112, or 136 for `CDEX001`).
4. Unknown magic (`DexVersion.forMagic`) → `NotADexFile`, with the `IllegalArgumentException` as
   `cause`.
5. `checkApi(targetApi)` → `InvalidDexFile` (the wrapped message keeps the api hint).
6. `endian_tag` (not `DEX009`/`DEX013`): reverse or other → `InvalidDexFile`.
7. `file_size` below the header size → `InvalidDexFile`, beyond the buffer → `InvalidDexFile`;
   containers also check `container_size` and `header_offset`.
8. Header-level failures above keep their type (`NotADexFile`/`InvalidDexFile`); any other
   content failure, a lazy element read included, is wrapped into `InvalidDexFile` with the
   cause, naming the section and index; caller mistakes (nulls, options, offsets, ids) stay
   plain and are checked first. Two content checks are type-specific: an `encoded_value`
   `value_arg` outside the range of its value type, and `ACC_VERIFIED`/`ACC_OPTIMIZED` on a
   `class_def_item` unless the target is Dalvik with odex instructions.

`code_item.ins_size`/`outs_size` are read past, not checked: both are redundant with the proto and
the instructions, so an obfuscated file may hold any value; `registers_size` is the register count.

`ids` selects classes after materialisation, in array order; `null` = all, repeats kept (a repeated
index gives a repeated `ClassDef`, no uniqueness check), out of range → `IndexOutOfBoundsException` (all ids are validated before any element is decoded).
`DEX009`/`DEX013` have no annotations directory: their `dalvik.annotation.Throws` is synthesized from
the throws list only when `hasAnnotations()`; with annotations off the method has none.

### Writing

`write` validates the options, stores SHA-1 over `[32, file_size)` at 12 and Adler-32 over
`[12, file_size)` at 8, and fills the map list and section offsets. `DEX009`/`DEX013` →
`InvalidDexFile`; `ACC_VERIFIED`/`ACC_OPTIMIZED` on a class → `IllegalStateException` unless the
target is Dalvik with odex instructions.

`ins_size` comes from the proto and `outs_size` from the instructions, whatever the input held. A
code item whose `registers_size`, `ins_size` or `outs_size` does not fit an unsigned short raises
`IllegalStateException` naming the field and the value only - a code item is de-duplicated, so it
is not tied to one method. `MethodDef.unchecked` is the only way to reach a value the model rejects.

`type`, `proto`, `field`, `method`, `callsite` and `method handle` sections hold at most
`DexConstants.MAX_POOL_SIZE` entries (`65534`; `65535` is reserved); a section above it raises
`IllegalStateException` - `65534` writes, `65535` fails. `field`/`method` are exempt under
`hasExpandedInstructions()` (`dex\n036`). `withClassSorting(true)` writes superclasses and
interfaces before their referrer, else model order; duplicates and hierarchy cycles raise
`IllegalStateException`.

Payloads are written for every payload carrier: `PACKED_SWITCH_PAYLOAD` and `SPARSE_SWITCH_PAYLOAD`
with 32-bit targets, and the legacy `M_*` forms with the same header but signed 16-bit targets (the
sparse form keeps 32-bit keys). A legacy target outside the signed 16-bit range raises
`IllegalStateException`. `20bc`/`40sc` round-trip through `InstructionN0ci`: the kind byte packs the
`VerificationError` code into bits 0-5 and the reference kind into bits 6-7 (`TYPE` 0, `FIELD` 1,
`METHOD` 2). Reads are strict - reference-type bits 3, an error code outside 1..9 or a nonzero
upper kind byte of `40sc` throw, wrapped into `InvalidDexFile` by `DexIO.read`.

### Containers

A `DEX041` container is one 120-byte header plus adjacent component headers; components share
string data (owned by the last, primary one, which records `container_size`) and code items but
keep their own id sections. A non-last component's `file_size` is 120, the last one's the rest of
the container.

`writeDexContainer` checks non-null varargs (`NullPointerException`), rejects zero components with
`IllegalArgumentException`, requires a container version (else `IllegalArgumentException`), checks
non-null elements, then `validate()` (API ≥ 35).

`readDexContainer(Cache)` advances by `file_size` from `data_offset`, so the buffer must end exactly
at the last component; `file_size` below 120 → `InvalidDexFile`, a non-advancing offset →
`InvalidDexFile`, an empty input (0 bytes) → `NotADexFile`. The API must be in the `DEX041`
window and there is no `ids` overload; a plain dex gives a one-element array. Read one component
with `read(options, bytes, 0, componentOffset)`, measured from the container start; a cdex in a vdex
uses its header offset as `data_offset` (API 28-35).

### Balancing

`balance*` splits a class list under the pool limits and never validates or rewrites classes. It
counts every reachable pool reference per class - type, superclass, interfaces, field and method ids
(with proto types), handles, call sites and their arguments, annotation values, handler types, and
debug-info types only with `hasDebugInfo()`; one class alone can exceed a limit, and then the call
throws `IllegalStateException` naming the class and the pool, without appending that class to
`dexes`.

`balance` appends to `dexes` and retries the class that crossed the limit; `last == true` packs the
remainder and returns `classes.size()`, `last == false` returns the first unpacked index, so a loop
must end with a `last == true` call. `balanceMove` then removes that prefix; `balanceAll` returns
`Collections.unmodifiableList`.

Guaranteed: input order inside a part, untouched input, exact prefix removal, deterministic output,
append-only `dexes`. Not guaranteed: minimal part count (one greedy pass), writability of every
part, duplicate removal (only with class sorting).

### Errors

| Situation | Exception |
|---|---|
| `options`/`data` null | `NullPointerException` |
| `validate()`, `header_offset < 0`, `writeDexContainer` with no components or a non-container version | `IllegalArgumentException` |
| short buffer, unknown magic | `NotADexFile` (`forMagic` exception as `cause`) |
| short `file_size`, bad endian tag, truncation, bad container header, legacy write, `checkApi` mismatch, corrupt content or a failed lazy element read | `InvalidDexFile` |
| class index outside the list | `IndexOutOfBoundsException` |
| more than `MAX_POOL_SIZE` entries, a code item field outside `0..65535`, duplicate class, hierarchy cycle | `IllegalStateException` |

## com.v7878.dex.DexIO.DexMapEntry

`public sealed interface DexIO.DexMapEntry permits MapItem`

Invariant: file order (ascending offset, except the map list); the implementation is internal.

**Methods**

| Signature | Note |
|---|---|
| `public abstract int type()` | `DexConstants.TYPE_*` code |
| `public abstract int size()` | |
| `public abstract int offset()` | from the dex start |

## com.v7878.dex.DexIO.ClassHeader

`public sealed interface DexIO.ClassHeader extends Annotatable permits ClassDef, ClassDefHeader`

**Methods**

| Signature | Note |
|---|---|
| `public abstract TypeId getType()` | |
| `public abstract int getAccessFlags()` | raw `access_flags` |
| `public abstract TypeId getSuperclass()` | `null` only for `java.lang.Object` |
| `public abstract java.util.List<TypeId> getInterfaces()` | file order |
| `public abstract java.util.NavigableSet<Annotation> getAnnotations()` | empty if annotations are off |

## com.v7878.dex.DexIO.DexReaderCache

`public sealed interface DexIO.DexReaderCache permits DexReader`

Invariant: content is stable between calls; decoding is per element, so a broken file can fail for
one index and succeed for another. A failed read throws `InvalidDexFile` naming the section and index (cause preserved).

**Methods**

| Signature | Note |
|---|---|
| `public abstract DexVersion version()` | from the magic |
| `public abstract java.util.List<String> getStrings()` | MUTF-8 |
| `public abstract java.util.List<TypeId> getTypes()` | |
| `public abstract java.util.List<FieldId> getFields()` | |
| `public abstract java.util.List<ProtoId> getProtos()` | |
| `public abstract java.util.List<MethodId> getMethods()` | |
| `public abstract java.util.List<? extends ClassHeader> getClassHeaders()` | no class data |
| `public abstract java.util.List<ClassDef> getClasses()` | full definitions |
| `public abstract java.util.List<MethodHandleId> getMethodHandles()` | |
| `public abstract java.util.List<CallSiteId> getCallSites()` | |
| `public abstract java.util.List<? extends DexMapEntry> getMapItems()` | |
| `public default int getStringCount()` | |
| `public default int getTypeCount()` | |
| `public default int getFieldCount()` | |
| `public default int getProtoCount()` | |
| `public default int getMethodCount()` | |
| `public default int getClassHeaderCount()` | |
| `public default int getClassCount()` | |
| `public default int getMethodHandleCount()` | |
| `public default int getCallSiteCount()` | |
| `public default String getString(int)` | range-checked |
| `public default TypeId getType(int)` | |
| `public default FieldId getField(int)` | |
| `public default ProtoId getProto(int)` | |
| `public default MethodId getMethod(int)` | |
| `public default ClassHeader getClassHeader(int)` | |
| `public default ClassDef getClass(int)` | |
| `public default MethodHandleId getMethodHandle(int)` | |
| `public default CallSiteId getCallSite(int)` | |

`get…(int)` throws `IndexOutOfBoundsException` and
decodes one element. `DEX009`/`DEX013` lack protos, the class-header view, the map list, handles and
call sites: empty lists, `0` counts, never `null`.

## com.v7878.dex.DexIO.InvalidDexFile

`public static class DexIO.InvalidDexFile extends RuntimeException`

**Constructors**

| Signature | Note |
|---|---|
| `public InvalidDexFile()` | |
| `public InvalidDexFile(String)` | |
| `public InvalidDexFile(String, Throwable)` | |
| `public InvalidDexFile(Throwable)` | |

## com.v7878.dex.DexIO.NotADexFile

`public static class DexIO.NotADexFile extends RuntimeException`

**Constructors**

| Signature | Note |
|---|---|
| `public NotADexFile()` | |
| `public NotADexFile(String)` | |
| `public NotADexFile(String, Throwable)` | |
| `public NotADexFile(Throwable)` | |

## Example

```java
public final class Repack {
    public static byte[] toDex038(byte[] in) {
        return DexIO.write(WriteOptions.defaultOptions()
                .withTargetApi(26)
                .withDexVersion(DexVersion.DEX038)
                .withClassSorting(true), DexIO.read(in));
    }
}
```
