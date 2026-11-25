package io.github.flyphantomfly.dexfilecli;

import com.v7878.dex.DexIO;
import com.v7878.dex.Opcodes;
import com.v7878.dex.Opcode;
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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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
                      String detected, int headerOffset, int api, String era) {
    }

    private static ReadOptions readOptions(DexVersion version, Integer apiOverride, String era) {
        int api = apiOverride != null ? apiOverride : version.getMinApi();
        boolean art = era != null ? era.equals("art") : api > 20;
        // odex instructions are always enabled: the runtime accepts every opcode it
        // supports regardless of the dex version carrying it (vova7878's guidance),
        // and the art flag must follow the api — defaultOptions() carries art=true
        // and withTargetApi() keeps it. The library accepts art from api 19
        // (FIRST_ART_TARGET) and dalvik up to api 20 (LAST_DALVIK_TARGET) —
        // levels 19 and 20 shipped both runtimes; we read them as dalvik-era.
        return ReadOptions.defaultOptions().withTargetApi(api)
                .withTargetForArt(art)
                .withOdexInstructions(true)
                .withExpandedInstructions(api >= 14 && api <= 15);
    }

    /**
     * Read-API ladder: odex/hiddenapi interpretation depends on targetApi, and
     * minApi alone can be too low (e.g. a standard 035 dex carrying ART markers
     * refuses targetApi=1). Try modern interpretations first; the first that
     * reads wins. With --era, only that runtime family is tried.
     */
    private static int[] apiLadder(DexVersion version, Integer apiOverride, String era) {
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
            if (api < min || out.contains(api)) {
                continue;
            }
            if (era != null && (era.equals("dalvik") ? api > 20 : api < 21)) {
                continue; // --era pins the runtime family
            }
            out.add(api);
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
                                      int contentOffset, int api, String era, boolean deep) {
        DexIO.DexReaderCache cache;
        try {
            // embedded dexes are read by slicing the buffer at the magic (data_offset)
            // — DexReader rejects a non-zero header_offset for anything but containers
            cache = DexIO.readCache(readOptions(version, api, era), data, dataOffset, 0, contentOffset);
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
        return new ReadResult(cache, cacheToDex(cache), version, how, dataOffset, api, era);
    }

    private static ReadResult readAny(byte[] data, Integer apiOverride, String era, boolean deep) {
        DexVersion atZero = versionAt(data, 0);
        if (atZero != null && atZero.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(atZero, apiOverride, era), data, 0);
            int usedApi = apiOverride != null ? apiOverride : atZero.getMinApi();
            return new ReadResult(null, null, atZero, "container(" + parts.length + ")", 0, usedApi, era);
        }
        if (atZero != null) {
            for (int api : apiLadder(atZero, apiOverride, era)) {
                ReadResult rr = tryRead(data, atZero, 0, 0, api, era, deep);
                if (rr != null) {
                    return rr;
                }
            }
            throw new DexIO.NotADexFile(
                    "flat " + atZero.name() + " magic found but no API interpretation reads it (tried " +
                            java.util.Arrays.toString(apiLadder(atZero, apiOverride, era)) + ")");
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
            for (int api : apiLadder(v, apiOverride, era)) {
                for (int contentOffset : new int[]{0, off}) {
                    ReadResult rr = tryRead(data, v, off, contentOffset, api, era, deep);
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

    record WriteOpts(WriteOptions opts, int api, boolean art) {
    }

    private static WriteOptions writeOptions(DexVersion target, Integer apiOverride) {
        return writeOptionsEx(target, apiOverride, null).opts();
    }

    /**
     * Write options with the era pinned: --era dalvik caps the effective api at
     * 20 (art=false), --era art floors it at 21 (art=true) — matching the read
     * ladder's family filter. Without --era the api/art follow the target
     * version (or the explicit --api override).
     */
    private static WriteOpts writeOptionsEx(DexVersion target, Integer apiOverride, String era) {
        int api = apiOverride != null ? apiOverride : target.getMinApi();
        boolean art;
        if (era != null) {
            art = era.equals("art");
            if (art && api < 21) {
                api = 21;       // --era art floors the api at 21
            }
            if (!art && api > 20) {
                api = 20;       // --era dalvik caps the api at 20
            }
        } else {
            art = api > 20;
        }
        // odex instructions are written verbatim: the runtime accepts them in any
        // dex version it supports (23 <= api <= 30 covers the quickened set).
        WriteOptions opts = WriteOptions.defaultOptions()
                .withDexVersion(target)
                .withTargetApi(api)
                .withTargetForArt(art)
                .withOdexInstructions(true)
                .withExpandedInstructions(api >= 14 && api <= 15);
        return new WriteOpts(opts, api, art);
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

    // ── ambiguous odex opcode detection (CLI-side; no upstream changes) ───
    //
    // Within ONE option set a byte has exactly one claimant; ambiguity is
    // ACROSS option sets: the same byte decodes as different instructions
    // depending on the reader's api / art-vs-dalvik era, and the file carries
    // no runtime-era info, so our interpretation guess decides the meaning.
    // Example: 0xed is THROW_VERIFICATION_ERROR (dalvik, api >= 5) and
    // IPUT_CHAR_QUICK (api 23-30) — same size, unrelated semantics.

    private static final Map<DexVersion, Map<Integer, List<String>>> AMBIGUOUS_BYTES = new ConcurrentHashMap<>();
    private static final Map<DexVersion, Map<String, List<Integer>>> CLAIMANT_BYTES = new ConcurrentHashMap<>();

    private static void buildAmbiguity(DexVersion version) {
        AMBIGUOUS_BYTES.computeIfAbsent(version, v -> {
            Map<Integer, Set<String>> union = new TreeMap<>();
            for (int api = 1; api <= 36; api++) {
                for (boolean art : new boolean[]{false, true}) {
                    for (boolean expanded : new boolean[]{false, true}) {
                        Opcodes table;
                        try {
                            ReadOptions opts = ReadOptions.defaultOptions().withTargetApi(api)
                                    .withTargetForArt(art).withOdexInstructions(true)
                                    .withExpandedInstructions(expanded);
                            opts.validate();
                            v.checkApi(api);
                            table = Opcodes.of(v, opts);
                        } catch (RuntimeException e) {
                            continue; // combination invalid for this version
                        }
                        for (int value = 0; value <= 0xFF; value++) {
                            try {
                                Opcode op = table.getOpcodeByValue(value);
                                union.computeIfAbsent(value, k -> new TreeSet<>()).add(op.name());
                            } catch (RuntimeException e) {
                                // value has no active claimant under this option set
                            }
                        }
                    }
                }
            }
            union.values().removeIf(names -> names.size() <= 1);
            Map<Integer, List<String>> bytes = new TreeMap<>();
            Map<String, List<Integer>> byName = new TreeMap<>();
            for (var e : union.entrySet()) {
                bytes.put(e.getKey(), List.copyOf(e.getValue()));
                for (String name : e.getValue()) {
                    byName.computeIfAbsent(name, k -> new ArrayList<>()).add(e.getKey());
                }
            }
            CLAIMANT_BYTES.put(v, Map.copyOf(byName));
            return Map.copyOf(bytes);
        });
    }

    /** The ambiguous byte set for a version (built once, cached). */
    static Set<Integer> ambiguousBytes(DexVersion version) {
        buildAmbiguity(version);
        return AMBIGUOUS_BYTES.get(version).keySet();
    }

    /** True when this opcode's byte has other claimants under other option sets. */
    private static boolean isAmbiguous(Opcode op, DexVersion version) {
        buildAmbiguity(version);
        return CLAIMANT_BYTES.get(version).containsKey(op.name());
    }

    /** The opcodes that claim the same byte as this one, under other option sets. */
    private static List<String> alternativeClaimants(Opcode op, DexVersion version) {
        buildAmbiguity(version);
        List<String> out = new ArrayList<>();
        for (int b : CLAIMANT_BYTES.get(version).getOrDefault(op.name(), List.of())) {
            for (String name : AMBIGUOUS_BYTES.get(version).get(b)) {
                if (!name.equals(op.name()) && !out.contains(name)) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    private static boolean readArtOf(int readApi, String era) {
        return era != null ? era.equals("art") : readApi > 20;
    }

    record OdexScan(int odexInstructions, int ambiguousOdexInstructions,
                    List<Integer> ambiguousBytes, List<String> ambiguousOps,
                    List<String> alternatives, int readApi, boolean readArt) {
        Map<String, Object> toJson() {
            Map<String, Object> m = Json.obj("odex_instructions", odexInstructions,
                    "ambiguous_odex_instructions", ambiguousOdexInstructions,
                    "read_api", readApi, "read_art", readArt);
            if (!ambiguousBytes.isEmpty()) {
                m.put("ambiguous_bytes", ambiguousBytes.stream()
                        .map(b -> String.format("0x%02x", b)).toList());
            }
            return m;
        }
    }

    /** Walks every instruction of the model and classifies odex/ambiguity. */
    private static OdexScan scanModel(Dex dex, DexVersion version, int readApi, String era) {
        boolean readArt = era != null ? era.equals("art") : readApi > 20;
        buildAmbiguity(version);
        Opcodes readTable = Opcodes.of(version, readOptions(version, readApi, era));
        int odex = 0, amb = 0;
        Set<Integer> bytes = new TreeSet<>();
        Set<String> ops = new TreeSet<>();
        Set<String> alts = new TreeSet<>();
        for (ClassDef c : dex.getClasses()) {
            for (MethodDef m : c.getMethods()) {
                if (m.getImplementation() == null) {
                    continue;
                }
                for (Instruction ins : m.getImplementation().getInstructions()) {
                    Opcode op = ins.getOpcode();
                    if (op.odexOnly()) {
                        odex++;
                    }
                    int value;
                    try {
                        value = readTable.getOpcodeValue(op);
                    } catch (RuntimeException e) {
                        continue; // not claimable under the read table
                    }
                    if (AMBIGUOUS_BYTES.get(version).containsKey(value)) {
                        amb++;
                        bytes.add(value);
                        ops.add(op.name());
                        alts.addAll(alternativeClaimants(op, version));
                    }
                }
            }
        }
        return new OdexScan(odex, amb, List.copyOf(bytes), List.copyOf(ops),
                List.copyOf(alts), readApi, readArt);
    }

    private static String hex(byte... values) {
        StringBuilder sb = new StringBuilder();
        for (int v : values) {
            sb.append(sb.isEmpty() ? "" : ", ").append(String.format("0x%02x", v));
        }
        return sb.toString();
    }

    private static OdexScan aggregateScans(List<OdexScan> scans, int readApi, boolean readArt) {
        int odex = 0, amb = 0;
        Set<Integer> bytes = new TreeSet<>();
        Set<String> ops = new TreeSet<>();
        Set<String> alts = new TreeSet<>();
        for (OdexScan s : scans) {
            odex += s.odexInstructions();
            amb += s.ambiguousOdexInstructions();
            bytes.addAll(s.ambiguousBytes());
            ops.addAll(s.ambiguousOps());
            alts.addAll(s.alternatives());
        }
        return new OdexScan(odex, amb, List.copyOf(bytes), List.copyOf(ops),
                List.copyOf(alts), readApi, readArt);
    }

    /** Adds the scan keys/note/warning to a result entry; only gains keys. */
    private static void attachScan(Map<String, Object> entry, OdexScan scan, boolean overridden) {
        if (scan.odexInstructions() > 0) {
            entry.put("odex_instructions", scan.odexInstructions());
        }
        if (scan.ambiguousOdexInstructions() > 0) {
            entry.putAll(scan.toJson());
            if (overridden) {
                entry.put("warning", ambiguousNote(scan.readApi(), scan.readArt(),
                        String.join(", ", scan.ambiguousOps()), hexBytes(scan.ambiguousBytes())));
            }
        } else if (scan.odexInstructions() > 0) {
            entry.put("note", ODEX_NOTE);
        }
    }

    static final class AmbiguousOdexException extends RuntimeException {
        AmbiguousOdexException(String message) {
            super(message);
        }
    }

    private static String hexBytes(List<Integer> bytes) {
        StringBuilder sb = new StringBuilder();
        for (int b : bytes) {
            sb.append(sb.isEmpty() ? "" : ", ").append(String.format("0x%02x", b));
        }
        return sb.toString();
    }

    /**
     * Safe-by-default write guard: a model read under one interpretation
     * (api/art) must not be re-encoded under a different one when it carries
     * era-ambiguous odex instructions — the same bytes decode as different
     * instructions per era, so the write would silently change their meaning.
     * Same interpretation => allowed (the count is reported in the JSON);
     * --allow-ambiguous-odex proceeds with an explicit warning.
     */
    private static boolean guardAmbiguousWrite(OdexScan scan, WriteOpts wo,
                                               boolean allowAmbiguous, String command) {
        if (scan == null || scan.ambiguousOdexInstructions() == 0) {
            return false;
        }
        // strict: ANY api or art difference from the read interpretation can flip
        // an ambiguous byte's meaning (the claimants' api windows differ), so the
        // write is refused unless the interpretation matches exactly
        if (wo.api() == scan.readApi() && wo.art() == scan.readArt()) {
            return false; // same interpretation — meaning preserved
        }
        String msg = "ambiguous odex instructions in " + command + ": "
                + scan.ambiguousOdexInstructions() + " instruction(s) decode as ["
                + String.join(", ", scan.ambiguousOps()) + "] at byte(s) "
                + hexBytes(scan.ambiguousBytes())
                + " under the read interpretation (api " + scan.readApi() + ", "
                + (scan.readArt() ? "art" : "dalvik") + "); writing under api " + wo.api()
                + ", " + (wo.art() ? "art" : "dalvik") + " would change their meaning "
                + "(the same bytes also decode as: "
                + String.join(", ", scan.alternatives()) + "). Pin --era to match the "
                + "file's runtime era, or pass --allow-ambiguous-odex to proceed anyway";
        if (allowAmbiguous) {
            return true; // caller attaches the warning to its JSON
        }
        throw new AmbiguousOdexException(msg);
    }

    private static String ambiguousNote(int readApi, boolean readArt, String ops, String bytes) {
        return "era-ambiguous odex opcodes present (" + ops + " at byte" + (bytes.contains(",") ? "s" : "")
                + " " + bytes + "): the same bytes decode as different instructions under "
                + "dalvik- vs art-era interpretations; this file was read at api " + readApi
                + " (" + (readArt ? "art" : "dalvik") + ") — writing under a different api/era "
                + "changes their meaning silently; pin --era or pass --allow-ambiguous-odex";
    }

    private static final String ODEX_NOTE =
            "non-ambiguous odex opcodes are written verbatim — the on-device runtime "
                    + "accepts them; analysis tools may not resolve quickened references "
                    + "(DexFile's own smali printer handles odex/legacy/expanded opcodes)";

    /**
     * Write a full dex, mapping the remaining constraint failure to an honest
     * error: with odex instructions enabled and legacy instructions rewritten,
     * this only fires for genuinely incompatible target choices.
     */
    private static void writeDex(String path, WriteOptions opts, Dex dex, DexVersion version,
                                 List<String> ambiguousOps, List<Integer> ambiguousBytes) {
        byte[] out;
        try {
            out = DexIO.write(opts, dex);
        } catch (IllegalArgumentException e) {
            String msg = String.valueOf(e.getMessage());
            if (msg.contains("Constraints for opcode")) {
                String op = msg.substring(msg.indexOf("opcode ") + 7);
                op = op.substring(0, op.contains(" ") ? op.indexOf(' ') : op.length());
                // if the failing opcode is era-ambiguous, the model may have been
                // decoded under the wrong runtime era — say so explicitly
                String extra = "";
                if (ambiguousOps.contains(op)) {
                    extra = " This opcode's byte (" + hexBytes(new ArrayList<>(ambiguousBytes))
                            + ") is era-ambiguous — the model may have been decoded under the "
                            + "wrong runtime era; pin --era to the file's runtime or use a "
                            + "matching --to.";
                }
                throw new DexIO.InvalidDexFile("cannot write opcode " + op + " into the chosen "
                        + "target — era-specific instructions pin the targetApi range, so try "
                        + "another --to (e.g. dalvik-era odex like invoke-direct-empty needs "
                        + "--to 035; the write target uses the version's own minimum api)." + extra);
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

    static Map<String, Object> info(String file, Integer apiOverride, boolean listClasses, int limit,
                                    String era, boolean scan) {
        byte[] data = readFile(file);
        DexVersion atZero = versionAt(data, 0);
        if (atZero != null && atZero.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(atZero, apiOverride, era), data, 0);
            List<Object> infos = new ArrayList<>();
            int readApi = apiOverride != null ? apiOverride : atZero.getMinApi();
            List<OdexScan> scans = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                Map<String, Object> part = dexInfo(parts[i], atZero, "flat", "part" + i);
                if (scan) {
                    OdexScan s = scanModel(parts[i], atZero, readApi, era);
                    scans.add(s);
                    part.putAll(s.toJson());
                }
                infos.add(part);
            }
            Map<String, Object> out = Json.obj("file", file, "container", true, "version", atZero.name(),
                    "parts", infos, "part_count", parts.length);
            if (scan) {
                boolean art = readArtOf(readApi, era);
                out.putAll(aggregateScans(scans, readApi, art).toJson());
            }
            return out;
        }
        ReadResult rr = readAny(data, apiOverride, era, listClasses || scan);
        Map<String, Object> out = dexInfo(rr);
        out.put("file", file);
        if (scan) {
            attachScan(out, scanModel(rr.dex, rr.version, rr.api, era), false);
        }
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

    static Map<String, Object> extract(String input, String outDir, Integer apiOverride, String era) {
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
                    dexes.addAll(extractDexBlob(dexBytes, entry.getName(), dir, apiOverride, era));
                }
            } catch (IOException e) {
                throw new UsageException("cannot read zip '" + input + "': " + e.getMessage());
            }
            if (dexes.isEmpty()) {
                throw new DexIO.NotADexFile("no classes*.dex entries inside '" + input + "'");
            }
        } else {
            dexes.addAll(extractDexBlob(data, Path.of(input).getFileName().toString(), dir, apiOverride, era));
        }
        return Json.obj("input", input, "out_dir", dir, "dex_files", dexes, "dex_count", dexes.size());
    }

    private static Path dumpToTemp(String input) throws IOException {
        Path tmp = Files.createTempFile("dexfile-cli-", ".zip");
        Files.write(tmp, readFile(input));
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    private static List<Object> extractDexBlob(byte[] data, String label, String dir, Integer apiOverride, String era) {
        List<Object> out = new ArrayList<>();
        DexVersion v = versionAt(data, 0);
        if (v != null && v.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(v, apiOverride, era), data, 0);
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
            ReadResult rr = readAny(data, apiOverride, era, false);
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

    static Map<String, Object> convert(String input, String toVersion, String outPath, Integer apiOverride, String era,
                                       boolean allowAmbiguous) {
        byte[] data = readFile(input);
        DexVersion target = parseVersion(toVersion);
        List<Object> converted = new ArrayList<>();
        DexVersion atZero = versionAt(data, 0);
        WriteOpts wo = writeOptionsEx(target, apiOverride, era);
        if (atZero != null && atZero.isDexContainer()) {
            Dex[] parts = DexIO.readDexContainer(readOptions(atZero, apiOverride, era), data, 0);
            int readApi = apiOverride != null ? apiOverride : atZero.getMinApi();
            List<OdexScan> scans = new ArrayList<>();
            for (Dex part : parts) {
                scans.add(scanModel(part, atZero, readApi, era));
            }
            boolean overridden = guardAmbiguousWrite(
                    aggregateScans(scans, readApi, readArtOf(readApi, era)), wo, allowAmbiguous, "convert");
            for (int i = 0; i < parts.length; i++) {
                String path = (outPath != null && parts.length == 1)
                        ? outPath
                        : (outPath != null ? stripDex(outPath) + ".part" + i + ".dex" : defaultOut(input, ".part" + i + "." + shortVer(target) + ".dex"));
                writeFile(path, DexIO.write(wo.opts(), parts[i]));
                Map<String, Object> entry = Json.obj("path", path, "from", atZero.name(), "to", target.name(),
                        "classes", parts[i].getClasses().size());
                attachScan(entry, scans.get(i), overridden);
                converted.add(entry);
            }
        } else {
            ReadResult rr = readAny(data, apiOverride, era, true);
            String path = outPath != null ? outPath : defaultOut(input, "." + shortVer(target) + ".dex");
            OdexScan scan = scanModel(rr.dex, rr.version, rr.api, era);
            boolean overridden = guardAmbiguousWrite(scan, wo, allowAmbiguous, "convert");
            writeDex(path, wo.opts(), toModernIfNeeded(rr.version, rr.dex), rr.version,
                    scan.ambiguousOps(), scan.ambiguousBytes());
            Map<String, Object> entry = Json.obj("path", path, "from", rr.version.name(), "to", target.name(),
                    "classes", rr.cache.getClasses().size(), "detected", rr.detected);
            attachScan(entry, scan, overridden);
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

    static Map<String, Object> assemble(String smaliPath, String outPath, Integer apiOverride,
                                        String era) {
        String smali;
        try {
            smali = Files.readString(Path.of(smaliPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UsageException("cannot read '" + smaliPath + "': " + e.getMessage());
        }
        return assembleText(smali, outPath, smaliPath, apiOverride, era);
    }

    static Map<String, Object> assembleText(String smali, String outPath, String label, Integer apiOverride,
                                            String era) {
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
        writeFile(path, DexIO.write(writeOptionsEx(target, apiOverride, era).opts(), Dex.of(cls)));
        return Json.obj(
                "out", path,
                "class", cls.getType().getName(),
                "methods", cls.getMethods().size(),
                "fields", cls.getFields().size(),
                "version", target.name());
    }

    // ── outline ───────────────────────────────────────────────────────────

    static Map<String, Object> outline(String input, String className, Integer apiOverride, String era) {
        byte[] data = readFile(input);
        ReadResult rr = readAny(data, apiOverride, era, true);
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

    static Map<String, Object> split(String input, String outDir, Integer apiOverride, String era,
                                     boolean allowAmbiguous) {
        byte[] data = readFile(input);
        ReadResult rr = readAny(data, apiOverride, era, true);
        requireSingleDex(rr, "split");
        String dir = outDir != null ? outDir : defaultOut(input, "_split");
        WriteOpts wo = writeOptionsEx(parseVersion("035"), apiOverride, era);
        OdexScan scan = scanModel(rr.dex, rr.version, rr.api, era);
        boolean overridden = guardAmbiguousWrite(scan, wo, allowAmbiguous, "split");
        List<Dex> parts = DexIO.balanceAll(wo.opts(),
                toModernIfNeeded(rr.version, rr.dex).getClasses());
        String stem = Path.of(input).getFileName().toString();
        if (stem.endsWith(".dex")) {
            stem = stem.substring(0, stem.length() - 4);
        }
        List<Object> written = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String path = Path.of(dir, stem + ".part" + i + ".dex").toString();
            writeDex(path, wo.opts(), parts.get(i), rr.version,
                    scan.ambiguousOps(), scan.ambiguousBytes());
            written.add(Json.obj("path", path, "classes", parts.get(i).getClasses().size()));
        }
        Map<String, Object> out = Json.obj("input", input, "out_dir", dir, "total_classes", rr.cache.getClasses().size(),
                "parts", written, "part_count", parts.size(),
                "note", "DexBalancer decides the shard count (64K-reference limit); --parts is advisory only");
        attachScan(out, scan, overridden);
        return out;
    }

    // ── validate ──────────────────────────────────────────────────────────

    static Map<String, Object> validate(String input, Integer apiOverride, Integer maxIssues, String era) {
        byte[] data = readFile(input);
        ReadResult rr = readAny(data, apiOverride, era, true);
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
        Map<String, Object> out = Json.obj(
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
        attachScan(out, scanModel(rr.dex, rr.version, rr.api, era), false);
        return out;
    }

    // ── merge ─────────────────────────────────────────────────────────────

    static Map<String, Object> merge(List<String> inputs, String outPath,
                                     Integer apiOverride, String era, boolean allowAmbiguous,
                                     boolean allowCdex) {
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
        WriteOpts wo = writeOptionsEx(target, apiOverride, era);

        List<ClassDef> classes = new ArrayList<>();
        Map<TypeId, String> owner = new LinkedHashMap<>();
        List<Object> perInput = new ArrayList<>();
        Set<String> versions = new java.util.TreeSet<>();
        List<OdexScan> scans = new ArrayList<>();
        for (String input : inputs) {
            byte[] data = readFile(input);
            ReadResult rr = readAny(data, apiOverride, era, true);
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
            scans.add(scanModel(rr.dex, rr.version, rr.api, era));
            perInput.add(Json.obj("file", input, "version", rr.version.name(),
                    "classes", added));
            // per-input scan keys ride inside each per_input entry
            Map<String, Object> last = (Map<String, Object>) perInput.get(perInput.size() - 1);
            attachScan(last, scans.get(scans.size() - 1), false);
        }
        OdexScan agg = aggregateScans(scans, scans.isEmpty() ? 0 : scans.get(0).readApi(),
                scans.isEmpty() ? false : scans.get(0).readArt());
        boolean overridden = guardAmbiguousWrite(agg, wo, allowAmbiguous, "merge");

        String path = outPath != null ? outPath : defaultOut(inputs.get(0), ".merged.dex");
        try {
            writeDex(path, wo.opts(), Dex.of(classes), target,
                    agg.ambiguousOps(), agg.ambiguousBytes());
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
        Map<String, Object> out = Json.obj(
                "inputs", inputs,
                "out", path,
                "target_version", target.name(),
                "classes", classes.size(),
                "per_input", perInput,
                "source_versions", versions,
                "cdex_written", cdexOut,
                "container_written", containerOut,
                "odex_instructions", agg.odexInstructions(),
                "note", cdexOut
                        ? "cdex output is a standalone/analysis artifact — not PathClassLoader-loadable on-device"
                        : containerOut
                        ? "merged into a single-component DEX041 container; each component still observes the 64K section limits"
                        : "merged into a single dex; if a section exceeds 64K the write fails with the limit named — split first");
        if (agg.ambiguousOdexInstructions() > 0) {
            out.put("ambiguous_odex_instructions", agg.ambiguousOdexInstructions());
            out.put("ambiguous_bytes", agg.ambiguousBytes().stream()
                    .map(b -> String.format("0x%02x", b)).toList());
            if (overridden) {
                out.put("warning", ambiguousNote(agg.readApi(), agg.readArt(),
                        String.join(", ", agg.ambiguousOps()), hexBytes(agg.ambiguousBytes())));
            }
        }
        return out;
    }

    // ── patch ─────────────────────────────────────────────────────────────

    static Map<String, Object> patch(String input, String opsPath, String outPath, Integer apiOverride, String era,
                                     boolean allowAmbiguous) {
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

        ReadResult rr = readAny(data, apiOverride, era, true);
        requireSingleDex(rr, "patch");
        OpRewriter rewriter = new OpRewriter(ops);
        Dex patched = rewriter.rewriteDex(toModernIfNeeded(rr.version, rr.dex));

        DexVersion original = rr.version;
        // cdex 001 is Android 9 (API 28) era ART output — rewrite it to DEX037
        // (introduced in API 24, covering the cdex era with room to spare)
        // rather than dropping to 035.
        DexVersion target = original.isCompact() ? DexVersion.DEX037 : original;
        WriteOpts wo = writeOptionsEx(target, apiOverride, era);
        String path = outPath != null ? outPath : input; // .bak is the Python side's job
        // the guard compares the READ interpretation against the write options —
        // the scanned model is what was read, before the ops touched it
        OdexScan scan = scanModel(rr.dex, rr.version, rr.api, era);
        boolean overridden = guardAmbiguousWrite(scan, wo, allowAmbiguous, "patch");
        writeDex(path, wo.opts(), patched, rr.version,
                scan.ambiguousOps(), scan.ambiguousBytes());

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
                "ops", opResults,
                "note", "matched=0 means the op found nothing — check class/method names");
        attachScan(out, scan, overridden);
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
