package io.github.flyphantomfly.dexfilecli;

import com.v7878.dex.DexIO;
import com.v7878.dex.DexVersion;
import com.v7878.dex.ReadOptions;
import com.v7878.dex.ReferenceType;
import com.v7878.dex.WriteOptions;
import com.v7878.dex.immutable.ClassDef;
import com.v7878.dex.immutable.Dex;
import com.v7878.dex.immutable.FieldDef;
import com.v7878.dex.immutable.FieldId;
import com.v7878.dex.immutable.MethodDef;
import com.v7878.dex.immutable.MethodId;
import com.v7878.dex.immutable.MethodImplementation;
import com.v7878.dex.immutable.TypeId;
import com.v7878.dex.immutable.bytecode.Instruction;
import com.v7878.dex.rewriter.DexRewriter;
import com.v7878.dex.rewriter.LegacyRewriter;
import com.v7878.dex.smali.SmaliIO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The six subcommands. Every command returns one JSON object (encoded by Main
 * on stdout) and throws {@link UsageException} for bad arguments; dex-format
 * errors propagate as DexIO.NotADexFile / DexIO.InvalidDexFile for Main's
 * exit-code mapping.
 */
final class Commands {

    private Commands() {
    }

    static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    // ── reading: flat dex, dex 041 container, or magic-scanned wrapper ────

    record ReadResult(DexIO.DexReaderCache cache, Dex dex, DexVersion version,
                      String detected, int headerOffset, int api) {
    }

    private static ReadOptions readOptions(DexVersion version, Integer apiOverride) {
        int api = apiOverride != null ? apiOverride : version.getMinApi();
        // odex instructions are always enabled: the runtime accepts every opcode it
        // supports regardless of the dex version carrying it (vova7878's guidance),
        // and the art flag must follow the api — defaultOptions() carries art=true
        // and withTargetApi() keeps it. The library accepts art from api 19
        // (FIRST_ART_TARGET) and dalvik up to api 20 (LAST_DALVIK_TARGET) —
        // levels 19 and 20 shipped both runtimes; we read them as dalvik-era.
        return ReadOptions.defaultOptions().withTargetApi(api)
                .withTargetForArt(api > 20)
                .withOdexInstructions(true)
                .withExpandedInstructions(api >= 14 && api <= 15);
    }

    /**
     * Read-API ladder: odex/hiddenapi interpretation depends on targetApi, and
     * minApi alone can be too low (e.g. a standard 035 dex carrying ART markers
     * refuses targetApi=1). Try modern interpretations first; the first that
     * reads wins.
     */
    private static int[] apiLadder(DexVersion version, Integer apiOverride) {
        if (apiOverride != null) {
            return new int[]{apiOverride};
        }
        int min = version.getMinApi();
        if (min < 1) {
            // pre-release milestones (dex009 = M3, dex013 = M5) accept exactly their own api
            return new int[]{min};
        }
        if (min >= 34) {
            return new int[]{min};
        }
        int[] ladder = {34, 28, 21, 19, min};
        List<Integer> out = new ArrayList<>();
        for (int api : ladder) {
            if (api >= min && !out.contains(api)) {
                out.add(api);
            }
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    /**
     * One (offset, api) interpretation — odex instructions are always enabled
     * (see readOptions). Deep reads force full class materialization —
     * lazily-computed caches otherwise pass the header checks and blow up later
     * on real-ART cdex (legacy hidden-API bits packed into field flags,
     * quickened odex opcodes). Null = interpretation doesn't work.
     */
    private static ReadResult tryRead(byte[] data, DexVersion version, int dataOffset,
                                      int contentOffset, int api, boolean deep) {
        DexIO.DexReaderCache cache;
        try {
            // embedded dexes are read by slicing the buffer at the magic (data_offset)
            // — DexReader rejects a non-zero header_offset for anything but containers
            cache = DexIO.readCache(readOptions(version, api), data, dataOffset, 0, contentOffset);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (deep) {
            try {
                for (ClassDef cls : cache.getClasses()) {
                    cls.getFields();
                    cls.getMethods();
                }
            } catch (RuntimeException e) {
                return null;
            }
        }
        String how = (dataOffset == 0 ? "flat" : "wrapped@" + Integer.toHexString(dataOffset))
                + (contentOffset != 0 ? "/content@" + Integer.toHexString(contentOffset) : "");
        return new ReadResult(cache, cacheToDex(cache), version, how, dataOffset, api);
    }

    private static ReadResult readAny(byte[] data, Integer apiOverride, boolean deep) {
        DexVersion atZero = versionAt(data, 0);
        if (atZero != null && atZero.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(atZero, apiOverride), data, 0);
            return new ReadResult(null, null, atZero, "container(" + parts.length + ")", 0, atZero.getMinApi());
        }
        if (atZero != null) {
            for (int api : apiLadder(atZero, apiOverride)) {
                ReadResult rr = tryRead(data, atZero, 0, 0, api, deep);
                if (rr != null) {
                    return rr;
                }
            }
            throw new DexIO.NotADexFile(
                    "flat " + atZero.name() + " magic found but no API interpretation reads it (tried " +
                            java.util.Arrays.toString(apiLadder(atZero, apiOverride)) + ")");
        }
        // magic scan: packer-shuffled dex, cdex inside vdex, any wrapped form
        List<Integer> candidates = new ArrayList<>();
        for (int off = 0; off + 8 <= data.length; off++) {
            if (versionAt(data, off) != null) {
                candidates.add(off);
            }
        }
        List<String> found = new ArrayList<>();
        for (int off : candidates) {
            DexVersion v = versionAt(data, off);
            if (found.size() < 8) {
                found.add(v.name() + "@" + Integer.toHexString(off));
            }
            for (int api : apiLadder(v, apiOverride)) {
                for (int contentOffset : new int[]{0, off}) {
                    ReadResult rr = tryRead(data, v, off, contentOffset, api, deep);
                    if (rr != null) {
                        return rr;
                    }
                }
            }
        }
        if (!found.isEmpty()) {
            String shown = String.join(", ", found.subList(0, Math.min(3, found.size())))
                    + (found.size() > 3 ? " +" + (found.size() - 3) + " more" : "");
            throw new DexIO.NotADexFile("dex magic found (" + shown + ") but no API interpretation "
                    + "reads it — a format variant beyond current DexFile support; see README limits");
        }
        throw new DexIO.NotADexFile("no readable dex magic found (flat, container, or wrapped)");
    }

    private static DexVersion versionAt(byte[] data, int off) {
        // fast reject: every dex/cdex magic starts with 'd' or 'c'
        char first = (char) (data[off] & 0xFF);
        if (first != 'd' && first != 'c') {
            return null;
        }
        for (DexVersion v : DexVersion.values()) {
            byte[] magic = v.getMagicArray();
            if (off + magic.length <= data.length) {
                boolean eq = true;
                for (int i = 0; i < magic.length; i++) {
                    if (data[off + i] != magic[i]) {
                        eq = false;
                        break;
                    }
                }
                if (eq) {
                    return v;
                }
            }
        }
        return null;
    }


    private static Dex cacheToDex(DexIO.DexReaderCache cache) {
        // Dex.of() re-validates access flags and rejects real-world quirks (e.g. fields
        // carrying the 0x20 bit inside vdex-era cdex); Dex.raw() trusts the reader
        return Dex.raw(cache.getClasses());
    }

    /** Containers carry multiple parts — model-level surgery needs a single dex. */
    private static void requireSingleDex(ReadResult rr, String command) {
        if (rr.cache == null || rr.dex == null) {
            throw new UsageException(
                    "the input is a dex 041 container (" + rr.detected + ") — " + command
                    + " works on a single dex; run extract or convert first");
        }
    }

    private static WriteOptions writeOptions(DexVersion target) {
        return writeOptions(target, null);
    }

    private static WriteOptions writeOptions(DexVersion target, Integer apiOverride) {
        int api = apiOverride != null ? apiOverride : target.getMinApi();
        // DexOptions derives art = api > LAST_DALVIK_TARGET (20), but withers
        // keep the CURRENT art flag — set it explicitly to stay consistent.
        // odex instructions are written verbatim: the runtime accepts them in any
        // dex version it supports (23 <= api <= 30 covers the quickened set).
        return WriteOptions.defaultOptions()
                .withDexVersion(target)
                .withTargetApi(api)
                .withTargetForArt(api > 20)
                .withOdexInstructions(true)
                .withExpandedInstructions(api >= 14 && api <= 15);
    }

    private static byte[] readFile(String path) {
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (IOException e) {
            throw new UsageException("cannot read '" + path + "': " + e.getMessage());
        }
    }

    private static void writeFile(String path, byte[] data) {
        try {
            Path p = Path.of(path);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.write(p, data);
        } catch (IOException e) {
            throw new UsageException("cannot write '" + path + "': " + e.getMessage());
        }
    }

    /**
     * 009/013 models carry the era's *legacy* instruction set (the m-prefixed
     * opcodes — standard for their day, not odex); the library's LegacyRewriter
     * converts them to the modern set so a standard write works.
     */
    private static Dex toModernIfNeeded(DexVersion version, Dex dex) {
        if (version != DexVersion.DEX009 && version != DexVersion.DEX013) {
            return dex;
        }
        return new LegacyRewriter().rewriteDex(dex);
    }

    /** Odex (runtime-quickened) instruction count — reported when written verbatim. */
    private static int countOdexInstructions(Dex dex) {
        int count = 0;
        for (ClassDef cls : dex.getClasses()) {
            for (MethodDef m : cls.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) {
                    continue;
                }
                for (Instruction ins : impl.getInstructions()) {
                    if (ins.getOpcode().odexOnly()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static final String ODEX_NOTE =
            "odex opcodes are written verbatim — the on-device runtime accepts them, "
                    + "but analysis tools may not resolve quickened references "
                    + "(the DexFile dev branch ships a smali printer that handles "
                    + "odex/legacy/expanded opcodes)";

    /**
     * Write a full dex, mapping the remaining constraint failure to an honest
     * error: with odex instructions enabled and legacy instructions rewritten,
     * this only fires for genuinely incompatible target choices.
     */
    private static void writeDex(String path, WriteOptions opts, Dex dex) {
        byte[] out;
        try {
            out = DexIO.write(opts, dex);
        } catch (IllegalArgumentException e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("Constraints for opcode")) {
                String op = msg.substring(msg.indexOf("opcode ") + 7);
                op = op.substring(0, op.contains(" ") ? op.indexOf(' ') : op.length());
                throw new DexIO.InvalidDexFile("cannot write opcode " + op + " into the chosen "
                        + "target — era-specific instructions pin the targetApi range, so try "
                        + "another --to (e.g. dalvik-era odex like invoke-direct-empty needs "
                        + "--to 035; the write target uses the version's own minimum api)");
            }
            throw e;
        }
        writeFile(path, out);
    }

    private static boolean isZip(byte[] data) {
        return data.length > 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4;
    }

    private static String defaultOut(String input, String suffix) {
        String stem = input;
        for (String ext : new String[]{".apk", ".dex", ".vdex", ".cdex", ".jar", ".smali"}) {
            if (stem.toLowerCase(Locale.ROOT).endsWith(ext)) {
                stem = stem.substring(0, stem.length() - ext.length());
                break;
            }
        }
        return stem + suffix;
    }

    // ── info ──────────────────────────────────────────────────────────────

    static Map<String, Object> info(String file, Integer apiOverride, boolean listClasses, int limit) {
        byte[] data = readFile(file);
        DexVersion atZero = versionAt(data, 0);
        if (atZero != null && atZero.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(atZero, apiOverride), data, 0);
            List<Object> infos = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                infos.add(dexInfo(parts[i], atZero, "flat", "part" + i));
            }
            return Json.obj("file", file, "container", true, "version", atZero.name(),
                    "parts", infos, "part_count", parts.length);
        }
        ReadResult rr = readAny(data, apiOverride, listClasses);
        Map<String, Object> out = dexInfo(rr);
        out.put("file", file);
        if (listClasses) {
            List<String> names = new ArrayList<>();
            for (ClassDef cls : rr.cache.getClasses()) {
                names.add(cls.getType().getName());
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            out.put("classes_list", names.subList(0, Math.min(limit, names.size())));
            out.put("classes_list_total", names.size());
            if (names.size() > limit) {
                out.put("classes_truncated", true);
            }
        }
        return out;
    }

    private static Map<String, Object> dexInfo(ReadResult rr) {
        Map<String, Object> counts = Json.obj(
                "classes", rr.cache.getClasses().size(),
                "methods", rr.cache.getMethods().size(),
                "fields", rr.cache.getFields().size(),
                "strings", rr.cache.getStrings().size(),
                "types", rr.cache.getTypes().size(),
                "map_items", rr.cache.getMapItems().size(),
                "method_handles", rr.cache.getMethodHandles().size(),
                "call_sites", rr.cache.getCallSites().size());
        Map<String, Object> out = Json.obj(
                "version", rr.version.name(),
                "target_api", rr.api,
                "compact", rr.version.isCompact(),
                "container", rr.version.isDexContainer(),
                "detected", rr.detected,
                "counts", counts);
        return out;
    }

    private static Map<String, Object> dexInfo(Dex dex, DexVersion version, String detected, String label) {
        Map<String, Object> out = Json.obj(
                "label", label,
                "version", version.name(),
                "target_api", version.getMinApi(),
                "compact", version.isCompact(),
                "container", version.isDexContainer(),
                "detected", detected,
                "counts", Json.obj("classes", dex.getClasses().size()));
        return out;
    }

    // ── extract ───────────────────────────────────────────────────────────

    static Map<String, Object> extract(String input, String outDir, Integer apiOverride) {
        byte[] data = readFile(input);
        String dir = outDir != null ? outDir : defaultOut(input, "_dex_extract");
        List<Object> dexes = new ArrayList<>();

        if (isZip(data)) {
            try (ZipFile zip = new ZipFile(dumpToTemp(input).toFile())) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".dex")) {
                        continue;
                    }
                    byte[] dexBytes = zip.getInputStream(entry).readAllBytes();
                    dexes.addAll(extractDexBlob(dexBytes, entry.getName(), dir, apiOverride));
                }
            } catch (IOException e) {
                throw new UsageException("cannot read zip '" + input + "': " + e.getMessage());
            }
            if (dexes.isEmpty()) {
                throw new DexIO.NotADexFile("no classes*.dex entries inside '" + input + "'");
            }
        } else {
            dexes.addAll(extractDexBlob(data, Path.of(input).getFileName().toString(), dir, apiOverride));
        }
        return Json.obj("input", input, "out_dir", dir, "dex_files", dexes, "dex_count", dexes.size());
    }

    private static Path dumpToTemp(String input) throws IOException {
        Path tmp = Files.createTempFile("dexfile-cli-", ".zip");
        Files.write(tmp, readFile(input));
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    private static List<Object> extractDexBlob(byte[] data, String label, String dir, Integer apiOverride) {
        List<Object> out = new ArrayList<>();
        DexVersion v = versionAt(data, 0);
        if (v != null && v.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(v, apiOverride), data, 0);
            for (int i = 0; i < parts.length; i++) {
                String name = label + ".part" + i + ".dex";
                String path = Path.of(dir, name).toString();
                writeFile(path, DexIO.write(writeOptions(DexVersion.DEX035), parts[i]));
                out.add(Json.obj("name", name, "path", path, "from", v.name(),
                        "classes", parts[i].getClasses().size(), "container_part", true));
            }
            return out;
        }
        String name = label.endsWith(".dex") ? label : label + ".dex";
        String path = Path.of(dir, name).toString();
        writeFile(path, data);
        Map<String, Object> info;
        try {
            // shallow read: extract only needs the section counts for its report
            ReadResult rr = readAny(data, apiOverride, false);
            info = Json.obj("version", rr.version.name(), "classes", rr.cache.getClasses().size());
        } catch (RuntimeException e) {
            info = Json.obj("version", "unreadable", "error", String.valueOf(e.getMessage()));
        }
        out.add(Json.obj("name", name, "path", path, "info", info));
        return out;
    }

    // ── convert ───────────────────────────────────────────────────────────

    static DexVersion parseVersion(String s) {
        String normalized = s.toUpperCase(Locale.ROOT).replace("-", "");
        DexVersion v = tryVersion(normalized);
        if (v == null && !normalized.startsWith("DEX")) {
            v = tryVersion("DEX" + normalized);
        }
        if (v == null) {
            throw new UsageException("unknown dex version '" + s
                    + "' (supported: 035, 036, 037, 038, 039, 040, 041, cdex001)");
        }
        return v;
    }

    private static DexVersion tryVersion(String name) {
        try {
            return DexVersion.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Map<String, Object> convert(String input, String toVersion, String outPath, Integer apiOverride) {
        byte[] data = readFile(input);
        DexVersion target = parseVersion(toVersion);
        List<Object> converted = new ArrayList<>();
        DexVersion atZero = versionAt(data, 0);
        if (atZero != null && atZero.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(atZero, apiOverride), data, 0);
            for (int i = 0; i < parts.length; i++) {
                String path = (outPath != null && parts.length == 1)
                        ? outPath
                        : (outPath != null ? stripDex(outPath) + ".part" + i + ".dex" : defaultOut(input, ".part" + i + "." + shortVer(target) + ".dex"));
                writeFile(path, DexIO.write(writeOptions(target, apiOverride), parts[i]));
                converted.add(Json.obj("path", path, "from", atZero.name(), "to", target.name(),
                        "classes", parts[i].getClasses().size()));
            }
        } else {
            ReadResult rr = readAny(data, apiOverride, true);
            String path = outPath != null ? outPath : defaultOut(input, "." + shortVer(target) + ".dex");
            writeDex(path, writeOptions(target, apiOverride), toModernIfNeeded(rr.version, rr.dex));
            int odex = countOdexInstructions(rr.dex);
            Map<String, Object> entry = Json.obj("path", path, "from", rr.version.name(), "to", target.name(),
                    "classes", rr.cache.getClasses().size(), "detected", rr.detected);
            if (odex > 0) {
                entry.put("odex_instructions", odex);
                entry.put("note", ODEX_NOTE);
            }
            converted.add(entry);
        }
        return Json.obj("input", input, "target_version", target.name(), "converted", converted);
    }

    private static String shortVer(DexVersion v) {
        return v.name().replace("DEX", "").toLowerCase(Locale.ROOT);
    }

    private static String stripDex(String path) {
        return path.toLowerCase(Locale.ROOT).endsWith(".dex")
                ? path.substring(0, path.length() - 4) : path;
    }

    // ── assemble ──────────────────────────────────────────────────────────

    static Map<String, Object> assemble(String smaliPath, String outPath, Integer apiOverride) {
        String smali;
        try {
            smali = Files.readString(Path.of(smaliPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UsageException("cannot read '" + smaliPath + "': " + e.getMessage());
        }
        return assembleText(smali, outPath, smaliPath, apiOverride);
    }

    static Map<String, Object> assembleText(String smali, String outPath, String label, Integer apiOverride) {
        ClassDef cls;
        try {
            cls = SmaliIO.parseClass(smali);
        } catch (RuntimeException e) {
            throw new UsageException("smali parse failed for '" + label + "': " + e.getMessage());
        }
        DexVersion target = parseVersion("035");
        if (apiOverride != null) {
            target = DexVersion.forApi(apiOverride);
        }
        String path = outPath != null ? outPath : defaultOut(label, ".dex");
        writeFile(path, DexIO.write(writeOptions(target, apiOverride), Dex.of(cls)));
        return Json.obj(
                "out", path,
                "class", cls.getType().getName(),
                "methods", cls.getMethods().size(),
                "fields", cls.getFields().size(),
                "version", target.name());
    }

    // ── outline ───────────────────────────────────────────────────────────

    static Map<String, Object> outline(String input, String className, Integer apiOverride) {
        byte[] data = readFile(input);
        ReadResult rr = readAny(data, apiOverride, true);
        requireSingleDex(rr, "outline");
        for (ClassDef cls : rr.cache.getClasses()) {
            if (cls.getType().getName().equals(className)) {
                List<Object> fields = new ArrayList<>();
                for (FieldDef f : cls.getFields()) {
                    fields.add(Json.obj("name", f.getName(),
                            "type", f.getType().getDescriptor(),
                            "flags", com.v7878.dex.AccessFlag.formatAccessFlagsForField(f.getAccessFlags())));
                }
                List<Object> methods = new ArrayList<>();
                for (MethodDef m : cls.getMethods()) {
                    Integer registers = m.getImplementation() != null
                            ? m.getImplementation().getRegisterCount() : null;
                    methods.add(Json.obj("name", m.getName(),
                            "descriptor", m.getProto().getDescriptor(),
                            "flags", com.v7878.dex.AccessFlag.formatAccessFlagsForMethod(m.getAccessFlags()),
                            "registers", registers));
                }
                List<Object> interfaces = new ArrayList<>();
                for (TypeId it : cls.getInterfaces()) {
                    interfaces.add(it.getName());
                }
                return Json.obj(
                        "class", cls.getType().getName(),
                        "descriptor", cls.getType().getDescriptor(),
                        "super", cls.getSuperclass() != null ? cls.getSuperclass().getName() : null,
                        "interfaces", interfaces,
                        "access_flags", com.v7878.dex.AccessFlag.formatAccessFlagsForClass(cls.getAccessFlags()),
                        "fields", fields,
                        "field_count", fields.size(),
                        "methods", methods,
                        "method_count", methods.size(),
                        "annotations_count", cls.getAnnotations().size(),
                        "dex_version", rr.version.name());
            }
        }
        throw new UsageException("class '" + className + "' not found in '" + input + "'");
    }

    // ── split ─────────────────────────────────────────────────────────────

    static Map<String, Object> split(String input, String outDir, Integer apiOverride) {
        byte[] data = readFile(input);
        ReadResult rr = readAny(data, apiOverride, true);
        requireSingleDex(rr, "split");
        String dir = outDir != null ? outDir : defaultOut(input, "_split");
        WriteOptions opts = writeOptions(parseVersion("035"), apiOverride);
        List<Dex> parts = DexIO.balanceAll(opts,
                toModernIfNeeded(rr.version, rr.dex).getClasses());
        String stem = Path.of(input).getFileName().toString();
        if (stem.endsWith(".dex")) {
            stem = stem.substring(0, stem.length() - 4);
        }
        List<Object> written = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String path = Path.of(dir, stem + ".part" + i + ".dex").toString();
            writeDex(path, opts, parts.get(i));
            written.add(Json.obj("path", path, "classes", parts.get(i).getClasses().size()));
        }
        return Json.obj("input", input, "out_dir", dir, "total_classes", rr.cache.getClasses().size(),
                "parts", written, "part_count", parts.size(),
                "note", "DexBalancer decides the shard count (64K-reference limit); --parts is advisory only");
    }

    // ── validate ──────────────────────────────────────────────────────────

    static Map<String, Object> validate(String input, Integer apiOverride, Integer maxIssues) {
        byte[] data = readFile(input);
        ReadResult rr = readAny(data, apiOverride, true);
        requireSingleDex(rr, "validate");
        Dex dex = toModernIfNeeded(rr.version, rr.dex);

        // self-contained classpath: only this dex's own classes are known.
        // A hierarchy that leaves the dex makes resolution non-determinable
        // (Result.exact() == false) — those references are counted as
        // unknown, never reported as broken.
        Map<TypeId, ClassDef> byType = new LinkedHashMap<>();
        for (ClassDef cls : dex.getClasses()) {
            byType.putIfAbsent(cls.getType(), cls);
        }
        com.v7878.dex.analysis.ClassPath cp =
                new com.v7878.dex.analysis.ClassPath(byType::get);

        List<Object> issues = new ArrayList<>();
        int cap = maxIssues != null ? maxIssues : 200;
        if (cap <= 0) {
            throw new UsageException("--max-issues must be positive");
        }
        int issueTotal = 0;
        int classesChecked = 0;
        int methodsAnalyzed = 0;
        int duplicates = 0;

        Set<TypeId> seenTypes = new java.util.HashSet<>();
        for (ClassDef cls : dex.getClasses()) {
            if (seenTypes.add(cls.getType())) {
                continue;
            }
            duplicates++;
            issueTotal++;
            if (issues.size() < cap) {
                issues.add(Json.obj("severity", "error", "kind", "duplicate_class",
                        "class", cls.getType().getName(),
                        "message", "class defined more than once"));
            }
        }

        for (ClassDef cls : dex.getClasses()) {
            classesChecked++;
            String cname = cls.getType().getName();
            if (cls.getSuperclass() != null && byType.containsKey(cls.getSuperclass())
                    && byType.get(cls.getSuperclass()).isInterface()) {
                issueTotal++;
                if (issues.size() < cap) {
                    issues.add(Json.obj("severity", "error", "kind", "super_is_interface",
                            "class", cname,
                            "message", "superclass " + cls.getSuperclass().getName()
                                    + " is an interface"));
                }
            }
            for (MethodDef m : cls.getMethods()) {
                if (m.getImplementation() == null) {
                    continue;
                }
                try {
                    com.v7878.dex.analysis.AnalyzedMethod.analyze(
                            cls.getType(), m,
                            com.v7878.dex.analysis.AnalysisOptions.defaultOptions());
                    methodsAnalyzed++;
                } catch (com.v7878.dex.analysis.AnalysisException e) {
                    issueTotal++;
                    if (issues.size() < cap) {
                        issues.add(Json.obj("severity", "error", "kind", "analysis_failed",
                                "class", cname, "method", m.getName(),
                                "descriptor", m.getProto().getDescriptor(),
                                "message", String.valueOf(e.getMessage())));
                    }
                } catch (RuntimeException e) {
                    issueTotal++;
                    if (issues.size() < cap) {
                        issues.add(Json.obj("severity", "warning", "kind", "analysis_skipped",
                                "class", cname, "method", m.getName(),
                                "message", String.valueOf(e.getMessage())));
                    }
                }
            }
        }

        // Member-reference resolution for ids whose declaring class is in this
        // dex. Only exact results are diagnostics: an inexact result means the
        // hierarchy (usually the framework) leaves the classpath, so the real
        // runtime could resolve it — counted as unknown, not broken.
        int refsChecked = 0;
        int refsResolved = 0;
        int refsUnknown = 0;
        int refErrors = 0;
        for (com.v7878.dex.immutable.MethodId mid : rr.cache.getMethods()) {
            if (!byType.containsKey(mid.getDeclaringClass())) {
                continue;
            }
            refsChecked++;
            try {
                var r = cp.resolveMethod(mid);
                if (!r.exact()) {
                    refsUnknown++;
                    continue;
                }
                if (r.error() == com.v7878.dex.analysis.ClassPath.ResolutionError.NONE) {
                    refsResolved++;
                    continue;
                }
                refErrors++;
                issueTotal++;
                if (issues.size() < cap) {
                    issues.add(Json.obj("severity", "error", "kind", "unresolved_method",
                            "class", mid.getDeclaringClass().getName(),
                            "method", mid.getName(),
                            "message", r.error() + (r.message() != null
                                    ? ": " + r.message() : "")));
                }
            } catch (RuntimeException e) {
                issueTotal++;
                if (issues.size() < cap) {
                    issues.add(Json.obj("severity", "warning", "kind", "resolution_skipped",
                            "class", mid.getDeclaringClass().getName(), "method", mid.getName(),
                            "message", String.valueOf(e.getMessage())));
                }
            }
        }
        for (com.v7878.dex.immutable.FieldId fid : rr.cache.getFields()) {
            if (!byType.containsKey(fid.getDeclaringClass())) {
                continue;
            }
            refsChecked++;
            try {
                var r = cp.resolveField(fid);
                if (!r.exact()) {
                    refsUnknown++;
                    continue;
                }
                if (r.error() == com.v7878.dex.analysis.ClassPath.ResolutionError.NONE) {
                    refsResolved++;
                    continue;
                }
                refErrors++;
                issueTotal++;
                if (issues.size() < cap) {
                    issues.add(Json.obj("severity", "error", "kind", "unresolved_field",
                            "class", fid.getDeclaringClass().getName(),
                            "field", fid.getName(),
                            "message", r.error() + (r.message() != null
                                    ? ": " + r.message() : "")));
                }
            } catch (RuntimeException e) {
                issueTotal++;
                if (issues.size() < cap) {
                    issues.add(Json.obj("severity", "warning", "kind", "resolution_skipped",
                            "class", fid.getDeclaringClass().getName(), "field", fid.getName(),
                            "message", String.valueOf(e.getMessage())));
                }
            }
        }

        boolean truncated = issueTotal > issues.size();
        boolean ok = issueTotal == 0;
        return Json.obj(
                "file", input,
                "version", rr.version.name(),
                "classes_checked", classesChecked,
                "methods_analyzed", methodsAnalyzed,
                "references_checked", refsChecked,
                "references_resolved", refsResolved,
                "references_unknown", refsUnknown,
                "reference_errors", refErrors,
                "duplicate_classes", duplicates,
                "issues_total", issueTotal,
                "issues_shown", issues.size(),
                "truncated", truncated,
                "issues", issues,
                "ok", ok,
                "note", "self-contained classpath: references whose hierarchy leaves this dex "
                        + "(framework classes) are counted as unknown, not flagged; "
                        + "diagnostics from DexFile's ClassPath/analysis package — the "
                        + "dex-level analog of smali_diagnostics");
    }

    // ── merge ─────────────────────────────────────────────────────────────

    static Map<String, Object> merge(List<String> inputs, String outPath,
                                     Integer apiOverride, boolean allowCdex) {
        if (inputs.size() < 2) {
            throw new UsageException("merge needs at least two dex files");
        }
        DexVersion target;
        if (allowCdex) {
            target = DexVersion.CDEX001;
            if (apiOverride != null && (apiOverride < target.getMinApi()
                    || apiOverride > target.getMaxApi())) {
                throw new UsageException("cdex output requires --api "
                        + target.getMinApi() + ".." + target.getMaxApi()
                        + " (got " + apiOverride + ")");
            }
        } else {
            target = DexVersion.forApi(apiOverride != null ? apiOverride : 21);
        }
        WriteOptions opts = writeOptions(target, apiOverride);

        List<ClassDef> classes = new ArrayList<>();
        Map<TypeId, String> owner = new LinkedHashMap<>();
        List<Object> perInput = new ArrayList<>();
        Set<String> versions = new java.util.TreeSet<>();
        for (String input : inputs) {
            byte[] data = readFile(input);
            ReadResult rr = readAny(data, apiOverride, true);
            requireSingleDex(rr, "merge");
            versions.add(rr.version.name());
            int added = 0;
            for (ClassDef cls : toModernIfNeeded(rr.version, rr.dex).getClasses()) {
                String prev = owner.putIfAbsent(cls.getType(), input);
                if (prev != null) {
                    throw new UsageException("duplicate class '" + cls.getType().getName()
                            + "' in '" + input + "' and '" + prev + "' — remove one first (patch/delete)");
                }
                classes.add(cls);
                added++;
            }
            perInput.add(Json.obj("file", input, "version", rr.version.name(),
                    "classes", added));
        }

        String path = outPath != null ? outPath : defaultOut(inputs.get(0), ".merged.dex");
        try {
            writeDex(path, opts, Dex.of(classes));
        } catch (IllegalStateException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("should not exceed limit")) {
                throw new UsageException("merged dex exceeds a 64K section limit: " + message
                        + " — split the inputs first and merge each shard separately");
            }
            throw e;
        }
        boolean cdexOut = target.isCompact();
        boolean containerOut = target.isDexContainer();
        return Json.obj(
                "inputs", inputs,
                "out", path,
                "target_version", target.name(),
                "classes", classes.size(),
                "per_input", perInput,
                "source_versions", versions,
                "cdex_written", cdexOut,
                "container_written", containerOut,
                "note", cdexOut
                        ? "cdex output is a standalone/analysis artifact — not PathClassLoader-loadable on-device"
                        : containerOut
                        ? "merged into a single-component DEX041 container; each component still observes the 64K section limits"
                        : "merged into a single dex; if a section exceeds 64K the write fails with the limit named — split first");
    }

    // ── patch ─────────────────────────────────────────────────────────────

    static Map<String, Object> patch(String input, String opsPath, String outPath, Integer apiOverride) {
        byte[] data = readFile(input);
        String opsJson;
        try {
            opsJson = Files.readString(Path.of(opsPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UsageException("cannot read ops file '" + opsPath + "': " + e.getMessage());
        }
        Object parsedOps = Json.parse(opsJson);
        List<?> list;
        if (parsedOps instanceof List<?>) {
            list = (List<?>) parsedOps;
        } else if (parsedOps instanceof Map<?, ?> wrapper && wrapper.get("ops") instanceof List<?> inner) {
            list = inner; // accept {"ops": [...]} wrappers too
        } else {
            throw new UsageException("ops file must contain a JSON array of operations "
                    + "(bare array or {\"ops\": [...]})");
        }
        List<Map<String, Object>> ops = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map)) {
                throw new UsageException("each operation must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> op = (Map<String, Object>) o;
            requireOp(op);
            ops.add(op);
        }
        if (ops.isEmpty()) {
            throw new UsageException("ops list is empty");
        }

        ReadResult rr = readAny(data, apiOverride, true);
        requireSingleDex(rr, "patch");
        OpRewriter rewriter = new OpRewriter(ops);
        Dex patched = rewriter.rewriteDex(toModernIfNeeded(rr.version, rr.dex));

        DexVersion original = rr.version;
        // cdex 001 is Android 9 (API 28) era ART output — rewrite it to DEX037
        // (introduced in API 24, covering the cdex era with room to spare)
        // rather than dropping to 035.
        DexVersion target = original.isCompact() ? DexVersion.DEX037 : original;
        WriteOptions opts = writeOptions(target, apiOverride);
        String path = outPath != null ? outPath : input; // .bak is the Python side's job
        writeDex(path, opts, patched);

        int odex = countOdexInstructions(patched);
        List<Object> opResults = new ArrayList<>();
        for (Map<String, Object> op : ops) {
            String type = String.valueOf(op.get("op"));
            opResults.add(Json.obj("op", type, "matched", rewriter.matched.getOrDefault(type, 0)));
        }
        Map<String, Object> out = Json.obj(
                "in", input,
                "out", path,
                "version_from", original.name(),
                "version_to", target.name(),
                "odex_instructions", odex,
                "ops", opResults,
                "note", "matched=0 means the op found nothing — check class/method names");
        if (odex > 0) {
            out.put("odex_note", ODEX_NOTE);
        }
        return out;
    }

    private static void requireOp(Map<String, Object> op) {
        Object type = op.get("op");
        if (type == null) {
            throw new UsageException("op missing 'op' field (supported: replace-const-string, rename, "
                    + "retarget-invoke, flip-flags, replace-method-body)");
        }
        List.of("replace-const-string", "rename", "retarget-invoke", "flip-flags", "replace-method-body")
                .stream().filter(t -> t.equals(type)).findAny()
                .orElseThrow(() -> new UsageException("unknown op '" + type + "' (supported: "
                        + "replace-const-string, rename, retarget-invoke, flip-flags, replace-method-body)"));
    }

    private static int flagValue(String name) {
        try {
            if (name.toLowerCase(Locale.ROOT).startsWith("0x")) {
                return (int) Long.parseLong(name.substring(2), 16);
            }
            return switch (name.toUpperCase(Locale.ROOT)) {
                case "PUBLIC" -> 0x1;
                case "PRIVATE" -> 0x2;
                case "PROTECTED" -> 0x4;
                case "STATIC" -> 0x8;
                case "FINAL" -> 0x10;
                case "SYNCHRONIZED" -> 0x20;
                case "VOLATILE", "BRIDGE" -> 0x40;
                case "TRANSIENT", "VARARGS" -> 0x80;
                case "NATIVE" -> 0x100;
                case "INTERFACE" -> 0x200;
                case "ABSTRACT" -> 0x400;
                case "STRICT" -> 0x800;
                case "SYNTHETIC" -> 0x1000;
                case "ANNOTATION" -> 0x2000;
                case "ENUM" -> 0x4000;
                case "CONSTRUCTOR" -> 0x10000;
                case "DECLARED_SYNCHRONIZED" -> 0x20000;
                default -> throw new UsageException("unknown access flag '" + name
                        + "' (use a standard dex flag name or a 0x… hex value)");
            };
        } catch (NumberFormatException e) {
            throw new UsageException("bad hex flag value '" + name + "'");
        }
    }

    private static final class OpRewriter extends DexRewriter {
        private final List<Map<String, Object>> ops;
        final Map<String, Integer> matched = new HashMap<>();

        OpRewriter(List<Map<String, Object>> ops) {
            this.ops = ops;
        }

        private void bump(String type) {
            matched.merge(type, 1, Integer::sum);
        }

        private boolean matches(Map<String, Object> op, String key, String expected) {
            return op.containsKey(key) && expected != null && expected.equals(op.get(key));
        }

        private List<Map<String, Object>> opsOf(String type) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> op : ops) {
                if (type.equals(op.get("op"))) {
                    out.add(op);
                }
            }
            return out;
        }

        @Override
        public Object rewriteReference(ReferenceType type, Object value) {
            Object result = super.rewriteReference(type, value);
            if (type == ReferenceType.STRING) {
                for (Map<String, Object> op : opsOf("replace-const-string")) {
                    String old = String.valueOf(op.get("old"));
                    if (old.equals(result)) {
                        result = String.valueOf(op.get("new"));
                        bump("replace-const-string");
                    }
                }
            }
            return result;
        }

        @Override
        public TypeId rewriteTypeId(TypeId value) {
            TypeId result = value;
            for (Map<String, Object> op : opsOf("rename")) {
                if ("class".equals(op.get("target")) && matches(op, "class", value.getName())) {
                    result = TypeId.ofName(String.valueOf(op.get("new_name")));
                    bump("rename");
                }
            }
            return result;
        }

        @Override
        public MethodId rewriteMethodId(MethodId value) {
            MethodId result = value;
            for (Map<String, Object> op : opsOf("rename")) {
                if ("method".equals(op.get("target"))
                        && matches(op, "class", value.getDeclaringClass().getName())
                        && matches(op, "name", value.getName())) {
                    result = MethodId.of(value.getDeclaringClass(), String.valueOf(op.get("new_name")),
                            value.getProto());
                    bump("rename");
                }
            }
            for (Map<String, Object> op : opsOf("retarget-invoke")) {
                if (matches(op, "class", value.getDeclaringClass().getName())
                        && matches(op, "name", value.getName())) {
                    result = MethodId.of(TypeId.ofName(String.valueOf(op.get("to_class"))),
                            value.getName(), value.getProto());
                    bump("retarget-invoke");
                }
            }
            return result;
        }

        @Override
        public FieldId rewriteFieldId(FieldId value) {
            FieldId result = value;
            for (Map<String, Object> op : opsOf("rename")) {
                if ("field".equals(op.get("target"))
                        && matches(op, "class", value.getDeclaringClass().getName())
                        && matches(op, "name", value.getName())) {
                    result = FieldId.of(value.getDeclaringClass(), String.valueOf(op.get("new_name")),
                            value.getType());
                    bump("rename");
                }
            }
            return result;
        }

        @Override
        public MethodDef rewriteMethodDef(MethodDef value) {
            MethodDef result = super.rewriteMethodDef(value);
            for (Map<String, Object> op : opsOf("flip-flags")) {
                if (op.containsKey("method") && matches(op, "class", ownerName())
                        && matches(op, "method", value.getName())) {
                    int flags = applyFlags(value.getAccessFlags(), op);
                    result = MethodDef.unchecked(result.getName(), result.getReturnType(),
                            result.getParameters(), flags, result.getHiddenApiFlags(),
                            result.getImplementation(), result.getAnnotations());
                    bump("flip-flags");
                }
            }
            for (Map<String, Object> op : opsOf("replace-method-body")) {
                if (matches(op, "class", ownerName()) && matches(op, "method", value.getName())) {
                    try {
                        String body = String.valueOf(op.get("smali")).strip();
                        if (body.endsWith(".end method")) {
                            body = body.substring(0, body.length() - ".end method".length()).stripTrailing();
                        }
                        if (!body.startsWith(".method ")) {
                            // the vendored Smali (upstream main) parses full methods
                            // only — wrap a bare body fragment in a synthetic frame
                            body = ".method public synthetic " + value.getName()
                                    + value.getProto().getDescriptor() + "\n"
                                    + body + "\n.end method";
                        }
                        MethodDef parsed = SmaliIO.parseMethod(body);
                        // preserve the original hidden-API flags: the smali text cannot express them
                        result = MethodDef.unchecked(parsed.getName(), parsed.getReturnType(),
                                parsed.getParameters(), parsed.getAccessFlags(),
                                value.getHiddenApiFlags(), parsed.getImplementation(),
                                parsed.getAnnotations());
                        bump("replace-method-body");
                    } catch (RuntimeException e) {
                        throw new UsageException("replace-method-body smali parse failed for '"
                                + value.getName() + "': " + e.getMessage());
                    }
                }
            }
            return result;
        }

        @Override
        public FieldDef rewriteFieldDef(FieldDef value) {
            FieldDef result = super.rewriteFieldDef(value);
            for (Map<String, Object> op : opsOf("flip-flags")) {
                if (op.containsKey("field") && matches(op, "class", ownerName())
                        && matches(op, "field", value.getName())) {
                    int flags = applyFlags(value.getAccessFlags(), op);
                    result = FieldDef.of(result.getName(), result.getType(), flags,
                            result.getHiddenApiFlags(), result.getInitialValue(),
                            result.getAnnotations());
                    bump("flip-flags");
                }
            }
            return result;
        }

        private final ThreadLocal<String> currentClass = ThreadLocal.withInitial(() -> "");

        @Override
        public ClassDef rewriteClassDef(ClassDef value) {
            currentClass.set(value.getType().getName());
            ClassDef result = super.rewriteClassDef(value);
            for (Map<String, Object> op : opsOf("flip-flags")) {
                if (!op.containsKey("method") && !op.containsKey("field")
                        && matches(op, "class", value.getType().getName())) {
                    int flags = applyFlags(value.getAccessFlags(), op);
                    result = ClassDef.of(result.getType(), flags, result.getSuperclass(),
                            result.getInterfaces(), result.getSourceFile(), result.getFields(),
                            result.getMethods(), result.getAnnotations());
                    bump("flip-flags");
                }
            }
            return result;
        }

        private String ownerName() {
            return currentClass.get();
        }

        private int applyFlags(int current, Map<String, Object> op) {
            int flags = current;
            Object set = op.get("set");
            if (set instanceof List<?> list) {
                for (Object f : list) {
                    flags |= flagValue(String.valueOf(f));
                }
            }
            Object clear = op.get("clear");
            if (clear instanceof List<?> list) {
                for (Object f : list) {
                    flags &= ~flagValue(String.valueOf(f));
                }
            }
            return flags;
        }
    }
}
