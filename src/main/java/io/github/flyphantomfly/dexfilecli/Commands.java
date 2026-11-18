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
import com.v7878.dex.immutable.TypeId;
import com.v7878.dex.rewriter.DexRewriter;
import com.v7878.dex.smali.SmaliIO;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        return ReadOptions.defaultOptions().withTargetApi(api);
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

    private static DexIO.DexReaderCache tryReadCache(byte[] data, DexVersion version,
                                                     int headerOffset, int contentOffset, Integer apiOverride) {
        var opts = readOptions(version, apiOverride);
        try {
            return DexIO.readCache(opts, data, 0, headerOffset, contentOffset);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ReadResult readAny(byte[] data, Integer apiOverride) {
        DexVersion atZero = versionAt(data, 0);
        if (atZero != null && atZero.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(atZero, apiOverride), data, 0);
            return new ReadResult(null, null, atZero, "container(" + parts.length + ")", 0, atZero.getMinApi());
        }
        if (atZero != null) {
            for (int api : apiLadder(atZero, apiOverride)) {
                var cache = tryReadCache(data, atZero, 0, 0, api);
                if (cache != null) {
                    return new ReadResult(cache, cacheToDex(cache), atZero, "flat", 0, api);
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
        for (int off : candidates) {
            DexVersion v = versionAt(data, off);
            for (int api : apiLadder(v, apiOverride)) {
                for (int contentOffset : new int[]{0, off}) {
                    var cache = tryReadCache(data, v, off, contentOffset, api);
                    if (cache != null) {
                        String how = "wrapped@" + Integer.toHexString(off)
                                + (contentOffset != 0 ? "/content@" + Integer.toHexString(contentOffset) : "");
                        return new ReadResult(cache, cacheToDex(cache), v, how, off, api);
                    }
                }
            }
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
        return Dex.of(cache.getClasses());
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
        int api = target.getMinApi();
        // DexOptions derives art = api > LAST_DALVIK_TARGET (20), but withers
        // keep the CURRENT art flag — set it explicitly to stay consistent.
        return WriteOptions.defaultOptions()
                .withDexVersion(target)
                .withTargetApi(api)
                .withTargetForArt(api > 20);
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

    private static boolean isZip(byte[] data) {
        return data.length > 4 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4;
    }

    private static String defaultOut(String input, String suffix) {
        String stem = input;
        for (String ext : new String[]{".apk", ".dex", ".vdex", ".cdex", ".jar"}) {
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
        ReadResult rr = readAny(data, apiOverride);
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
            ReadResult rr = readAny(data, apiOverride);
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
                writeFile(path, DexIO.write(writeOptions(target), parts[i]));
                converted.add(Json.obj("path", path, "from", atZero.name(), "to", target.name(),
                        "classes", parts[i].getClasses().size()));
            }
        } else {
            ReadResult rr = readAny(data, apiOverride);
            String path = outPath != null ? outPath : defaultOut(input, "." + shortVer(target) + ".dex");
            writeFile(path, DexIO.write(writeOptions(target), rr.dex));
            converted.add(Json.obj("path", path, "from", rr.version.name(), "to", target.name(),
                    "classes", rr.cache.getClasses().size(), "detected", rr.detected));
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
        writeFile(path, DexIO.write(writeOptions(target), Dex.of(cls)));
        return Json.obj(
                "out", path,
                "class", cls.getType().getName(),
                "methods", cls.getMethods().size(),
                "fields", cls.getFields().size(),
                "version", target.name());
    }

    // ── split ─────────────────────────────────────────────────────────────

    static Map<String, Object> split(String input, String outDir, Integer apiOverride) {
        byte[] data = readFile(input);
        ReadResult rr = readAny(data, apiOverride);
        requireSingleDex(rr, "split");
        String dir = outDir != null ? outDir : defaultOut(input, "_split");
        WriteOptions opts = writeOptions(parseVersion("035"));
        List<Dex> parts = DexIO.balanceAll(opts, rr.cache.getClasses());
        String stem = Path.of(input).getFileName().toString();
        if (stem.endsWith(".dex")) {
            stem = stem.substring(0, stem.length() - 4);
        }
        List<Object> written = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String path = Path.of(dir, stem + ".part" + i + ".dex").toString();
            writeFile(path, DexIO.write(opts, parts.get(i)));
            written.add(Json.obj("path", path, "classes", parts.get(i).getClasses().size()));
        }
        return Json.obj("input", input, "out_dir", dir, "total_classes", rr.cache.getClasses().size(),
                "parts", written, "part_count", parts.size(),
                "note", "DexBalancer decides the shard count (64K-reference limit); --parts is advisory only");
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

        ReadResult rr = readAny(data, apiOverride);
        requireSingleDex(rr, "patch");
        OpRewriter rewriter = new OpRewriter(ops);
        Dex patched = rewriter.rewriteDex(rr.dex);

        DexVersion original = rr.version;
        DexVersion target = original.isCompact() ? DexVersion.DEX035 : original;
        WriteOptions opts = writeOptions(target);
        String path = outPath != null ? outPath : input; // .bak is the Python side's job
        writeFile(path, DexIO.write(opts, patched));

        List<Object> opResults = new ArrayList<>();
        for (Map<String, Object> op : ops) {
            String type = String.valueOf(op.get("op"));
            opResults.add(Json.obj("op", type, "matched", rewriter.matched.getOrDefault(type, 0)));
        }
        return Json.obj(
                "in", input,
                "out", path,
                "version_from", original.name(),
                "version_to", target.name(),
                "ops", opResults,
                "note", "matched=0 means the op found nothing — check class/method names");
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
                        MethodDef parsed = SmaliIO.parseMethod(String.valueOf(op.get("smali")));
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
