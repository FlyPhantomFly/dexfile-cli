Up: [SKILL.md](../SKILL.md)

# DexOffsets

`DexOffsets` holds the `public static final int` constants of the binary layout: header sizes, item
sizes, alignments, header field offsets, and the `M3` (`dex\n009`) and `M5` (`dex\n013`) variants.
All offsets are measured from the start of the dex header and all multi-byte values are
little-endian; the reader rejects reverse-endian files.

## com.v7878.dex.DexOffsets

`public final class DexOffsets`

Invariant: values are repeated per version, not shared - select them by `DexVersion`, never by the
numeric offset alone.

**Fields** — `public static final int`: header sizes - `BASE_HEADER_SIZE` (112) `DEXCONTAINER_HEADER_SIZE` (120) `COMPACT_HEADER_SIZE` (136)

**Fields** — `public static final int`: item sizes - `STRING_ID_SIZE` (4) `TYPE_ID_SIZE` (4) `FIELD_ID_SIZE` (8) `PROTO_ID_SIZE` (12) `METHOD_ID_SIZE` (8) `METHOD_HANDLE_ID_SIZE` (8) `CALL_SITE_ID_SIZE` (4) `CLASS_DEF_SIZE` (32) `TRY_ITEM_SIZE` (8) `MAP_ITEM_SIZE` (12)

**Fields** — `public static final int`: alignments - `MAIN_SECTION_ALIGNMENT` (4) `DATA_SECTION_ALIGNMENT` (8) `MAP_ALIGNMENT` (4) `TYPE_LIST_ALIGNMENT` (4) `TRY_ITEM_ALIGNMENT` (4) `CODE_ITEM_ALIGNMENT` (4) `COMPACT_CODE_ITEM_ALIGNMENT` (2) `PAYLOAD_INSTRUCTION_ALIGNMENT` (4) `ANNOTATION_SET_ALIGNMENT` (4) `ANNOTATION_SET_LIST_ALIGNMENT` (4) `ANNOTATION_DIRECTORY_ALIGNMENT` (4) `HIDDENAPI_ALIGNMENT` (4) `COMPACT_OFFSET_TABLE_ALIGNMENT` (4)

**Fields** — `public static final int`: magic, checksum and signature - `MAGIC_OFFSET` (0) `CHECKSUM_OFFSET` (8) `CHECKSUM_DATA_START_OFFSET` (12) `SIGNATURE_OFFSET` (12) `SIGNATURE_SIZE` (20) `SIGNATURE_DATA_START_OFFSET` (32)

**Fields** — `public static final int`: header sizes, endian tag and links - `FILE_SIZE_OFFSET` (32) `HEADER_SIZE_OFFSET` (36) `ENDIAN_TAG_OFFSET` (40) `LINK_SIZE_OFFSET` (44) `LINK_START_OFFSET` (48) `MAP_OFFSET` (52)

**Fields** — `public static final int`: string, type and proto sections - `STRING_COUNT_OFFSET` (56) `STRING_START_OFFSET` (60) `TYPE_COUNT_OFFSET` (64) `TYPE_START_OFFSET` (68) `PROTO_COUNT_OFFSET` (72) `PROTO_START_OFFSET` (76)

**Fields** — `public static final int`: field, method, class and data sections - `FIELD_COUNT_OFFSET` (80) `FIELD_START_OFFSET` (84) `METHOD_COUNT_OFFSET` (88) `METHOD_START_OFFSET` (92) `CLASS_COUNT_OFFSET` (96) `CLASS_START_OFFSET` (100) `DATA_SIZE_OFFSET` (104) `DATA_START_OFFSET` (108)

**Fields** — `public static final int`: `DEX041` header tail - `CONTAINER_SIZE_OFFSET` (112) `HEADER_OFF_OFFSET` (116)

**Fields** — `public static final int`: `CDEX001` header tail - `FEATURE_FLAGS_OFFSET` (112) `DEBUG_INFO_OFFSETS_POS_OFFSET` (116) `DEBUG_INFO_OFFSETS_TABLE_OFFSET` (120) `DEBUG_INFO_BASE_OFFSET` (124, signed)

**Fields** — `public static final int`: `M5` (`dex\n013`) - `M5_HEADER_SIZE` (124) `M5_STRING_COUNT_OFFSET` (48) `M5_STRING_START_OFFSET` (52) `M5_TYPE_COUNT_OFFSET` (60) `M5_TYPE_START_OFFSET` (64) `M5_FIELD_COUNT_OFFSET` (68) `M5_FIELD_START_OFFSET` (72) `M5_METHOD_COUNT_OFFSET` (76) `M5_METHOD_START_OFFSET` (80) `M5_CLASS_COUNT_OFFSET` (84) `M5_CLASS_START_OFFSET` (88) `M5_STRING_ID_SIZE` (8) `M5_FIELD_ID_SIZE` (12) `M5_METHOD_ID_SIZE` (12) `M5_CLASS_DEF_SIZE` (36) `M5_STATIC_FIELD_DEF_ALIGNMENT` (8) `M5_INSTANCE_FIELD_DEF_ALIGNMENT` (4)

**Fields** — `public static final int`: `M3` (`dex\n009`) - `M3_HEADER_SIZE` (92) `M3_STRING_COUNT_OFFSET` (48) `M3_STRING_START_OFFSET` (52) `M3_TYPE_COUNT_OFFSET` (60) `M3_TYPE_START_OFFSET` (64) `M3_FIELD_COUNT_OFFSET` (68) `M3_FIELD_START_OFFSET` (72) `M3_METHOD_COUNT_OFFSET` (76) `M3_METHOD_START_OFFSET` (80) `M3_CLASS_COUNT_OFFSET` (84) `M3_CLASS_START_OFFSET` (88) `M3_STRING_ID_SIZE` (8) `M3_FIELD_ID_SIZE` (12) `M3_METHOD_ID_SIZE` (12) `M3_CLASS_DEF_SIZE` (32) `M3_STATIC_FIELD_DEF_ALIGNMENT` (8) `M3_INSTANCE_FIELD_DEF_ALIGNMENT` (4)

**Methods**

| Signature | Note |
|---|---|
| `public static int headerSize(DexVersion version)` | `null` fails on the `switch` |

`headerSize` maps DEX009 to `M3_HEADER_SIZE` (92), DEX013 to `M5_HEADER_SIZE` (124), CDEX001 to
`COMPACT_HEADER_SIZE` (136), DEX041 to `DEXCONTAINER_HEADER_SIZE` (120) and every other version to
`BASE_HEADER_SIZE` (112).

The checksum covers `[CHECKSUM_DATA_START_OFFSET, file_size)`, that is from 12; the SHA-1 signature
covers `[SIGNATURE_DATA_START_OFFSET, file_size)`, that is from 32. Both windows end at `file_size`,
not at the buffer length, and they start at different offsets: the checksum protects the signature
field, the signature does not protect the checksum.

### Version-specific tails

`DEX041`: each component of a container repeats a full header of 120 bytes. `container_size` is the
size of the whole container, `header_offset` is the component's own offset from the container start,
and the reader verifies that the recorded value equals the offset at which the component was found.

`CDEX001`: the header is 136 bytes and adds `feature_flags`, `debug_info_offsets_pos`,
`debug_info_offsets_table_offset` and a signed `debug_info_base`. Compact dex keeps its own
debug-info offset table and aligns code items to 2 bytes, and its data-section offsets are measured
from the data section rather than from the file start.

The same numeric offsets 112 and 116 mean `container_size`/`header_offset` in `DEX041` and
`feature_flags`/`debug_info_offsets_pos` in `CDEX001`.

`M3` and `M5` share the section-table offsets `0x30`-`0x58` and have no `proto_ids`, method-handle or
call-site sections. Their item sizes are wider than the modern ones (`string_id_item` 8,
`field_id_item` 12, `method_id_item` 12) and `class_def_item` is 32 in `M3` but 36 in `M5`; the
modern `STRING_COUNT_OFFSET` is 56, while both legacy values are 48.

Modern headers have no fields for method handles and call sites (both were added to the format
later): those sections are located through `map_items` only.

### Alignment

The data section is 8-byte aligned, but its members are not: `code_item`, `try_item`, payloads, the
map list, type lists, annotation sets and hiddenapi data use 4, and a compact `code_item` uses 2. In
an ordinary dex these alignments apply to the absolute offset from the file start; in a compact dex
data-section offsets are relative to the data section.

## Example

```java
public static DexVersion check(byte[] bytes) {
    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    DexVersion version = DexVersion.forMagic(buf.getLong(DexOffsets.MAGIC_OFFSET));
    int size = buf.getInt(DexOffsets.HEADER_SIZE_OFFSET);
    if (size != DexOffsets.headerSize(version)
            || buf.getInt(DexOffsets.FILE_SIZE_OFFSET) > bytes.length) {
        throw new IllegalStateException();
    }
    return version;
}
```
