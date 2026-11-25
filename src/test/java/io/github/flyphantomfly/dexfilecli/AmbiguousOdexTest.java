package io.github.flyphantomfly.dexfilecli;

import com.v7878.dex.*;
import com.v7878.dex.builder.*;
import com.v7878.dex.immutable.*;
import com.v7878.dex.immutable.bytecode.Instruction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests a–f of the era-ambiguity task: detect, report, refuse, round-trip. */
class AmbiguousOdexTest {

    // (a) build throwverr.dex programmatically: f20bc + return_void,
    //     written with WriteOptions DEX035 api5 dalvik odex=true
    private static byte[] buildThrowVerxDex() {
        TypeId t = TypeId.of("Lprobe/A;");
        ClassDef c = ClassBuilder.build(t, cb -> cb
                .withFlags(AccessFlag.PUBLIC)
                .withSuperclass(TypeId.OBJECT)
                .withMethodBody("m", ProtoId.of(TypeId.V),
                        AccessFlag.PUBLIC.value() | AccessFlag.STATIC.value(), 1,
                        code -> code.f20bc(Opcode.THROW_VERIFICATION_ERROR,
                                VerificationError.NO_SUCH_CLASS, ReferenceType.TYPE,
                                TypeId.of("Lprobe/Gone;")).return_void()));
        return DexIO.write(WriteOptions.defaultOptions().withDexVersion(DexVersion.DEX035)
                .withTargetApi(5).withTargetForArt(false).withOdexInstructions(true)
                .withExpandedInstructions(false), Dex.of(c));
    }

    private static Path writeThrowVerx(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("throwverr.dex");
        Files.write(p, buildThrowVerxDex());
        return p;
    }

    // (f) the ambiguity sets: DEX035/odex=true has exactly the 22 values;
    //     odex=false only 0xff; DEX009/013 none
    @Test
    void ambiguitySets() {
        Set<Integer> s = Commands.ambiguousBytes(DexVersion.DEX035);
        assertEquals(22, s.size());
        assertTrue(s.contains(0x73));
        for (int b = 0xe3; b <= 0xeb; b++) assertTrue(s.contains(b), "0x" + Integer.toHexString(b));
        for (int b = 0xed; b <= 0xf2; b++) assertTrue(s.contains(b), "0x" + Integer.toHexString(b));
        for (int b = 0xfa; b <= 0xff; b++) assertTrue(s.contains(b), "0x" + Integer.toHexString(b));

        // odex=false sweep (test-local; the CLI always reads with odex=true)
        Map<Integer, Set<String>> union = new TreeMap<>();
        for (int api = 1; api <= 36; api++)
            for (boolean art : new boolean[]{false, true})
                for (boolean exp : new boolean[]{false, true}) {
                    Opcodes t;
                    try {
                        ReadOptions o = ReadOptions.defaultOptions().withTargetApi(api)
                                .withTargetForArt(art).withOdexInstructions(false)
                                .withExpandedInstructions(exp);
                        o.validate(); DexVersion.DEX035.checkApi(api);
                        t = Opcodes.of(DexVersion.DEX035, o);
                    } catch (RuntimeException e) { continue; }
                    for (int b = 0; b <= 0xFF; b++) {
                        try { union.computeIfAbsent(b, k -> new TreeSet<>()).add(t.getOpcodeByValue(b).name()); }
                        catch (RuntimeException e) { }
                    }
                }
        union.values().removeIf(n -> n.size() <= 1);
        assertEquals(Set.of(0xff), union.keySet());

        // DEX009/013: none — M_EXECUTE_INLINE is owned by the dex version
        assertTrue(Commands.ambiguousBytes(DexVersion.DEX009).isEmpty());
        assertTrue(Commands.ambiguousBytes(DexVersion.DEX013).isEmpty());
    }

    // (b) info reports ambiguous_odex_instructions=1, bytes [0xed]
    @Test
    void infoReportsAmbiguity(@TempDir Path dir) throws Exception {
        Path dex = writeThrowVerx(dir);
        Map<String, Object> out = Commands.info(dex.toString(), null, false, 100, null, true);
        assertEquals(1, out.get("ambiguous_odex_instructions"));
        assertEquals(List.of("0xed"), out.get("ambiguous_bytes"));
        // the read ladder lands on the art/api-28 misdecode
        assertEquals(28, out.get("read_api"));
        assertEquals(true, out.get("read_art"));
    }

    // (b2) validate reports the same counts
    @Test
    void validateReportsAmbiguity(@TempDir Path dir) throws Exception {
        Path dex = writeThrowVerx(dir);
        Map<String, Object> out = Commands.validate(dex.toString(), null, 200, null);
        assertEquals(1, out.get("ambiguous_odex_instructions"));
        assertEquals(List.of("0xed"), out.get("ambiguous_bytes"));
    }

    // (c) convert --to 037 is REFUSED with kind ambiguous_odex
    @Test
    void convertTo037Refused(@TempDir Path dir) throws Exception {
        Path dex = writeThrowVerx(dir);
        Commands.AmbiguousOdexException e = assertThrows(Commands.AmbiguousOdexException.class,
                () -> Commands.convert(dex.toString(), "037", dir.resolve("out37.dex").toString(), null, null, false));
        assertTrue(e.getMessage().contains("IPUT_CHAR_QUICK"),
                "message names the misdecoded opcode: " + e.getMessage());
        assertTrue(e.getMessage().contains("THROW_VERIFICATION_ERROR"),
                "message names the alternative claimant: " + e.getMessage());
        assertFalse(Files.exists(dir.resolve("out37.dex")));
    }

    // (c) with --era dalvik --api 5 it reads as THROW_VERIFICATION_ERROR and
    //     convert --to 035 round-trips byte-identical (424 bytes)
    @Test
    void eraPinnedRoundTripByteIdentical(@TempDir Path dir) throws Exception {
        byte[] original = buildThrowVerxDex();
        Path dex = dir.resolve("throwverr.dex");
        Files.write(dex, original);
        Map<String, Object> out = Commands.convert(dex.toString(), "035",
                dir.resolve("rt.dex").toString(), 5, "dalvik", false);
        Map<?, ?> converted = ((List<?>) out.get("converted")).stream()
                .map(x -> (Map<?, ?>) x).findFirst().orElseThrow();
        Path rt = dir.resolve("rt.dex");
        byte[] roundTripped = Files.readAllBytes(rt);
        assertArrayEquals(original, roundTripped, "byte-identical 035 round-trip under --era dalvik --api 5");
    }

    // (c2) with --allow-ambiguous-odex and the default era it proceeds; the JSON
    //      carries the warning — and documents the type drop (4 -> 3 types)
    @Test
    void allowAmbiguousProceedsWithWarning(@TempDir Path dir) throws Exception {
        Path dex = writeThrowVerx(dir);
        Map<String, Object> out = Commands.convert(dex.toString(), "037",
                dir.resolve("out37.dex").toString(), null, null, true);
        Map<?, ?> converted = ((List<?>) out.get("converted")).stream()
                .map(x -> (Map<?, ?>) x).findFirst().orElseThrow();
        assertNotNull(converted.get("warning"), "the override keeps the warning in the JSON");
        assertEquals(1, converted.get("ambiguous_odex_instructions"));
        Map<String, Object> re = Commands.info(dir.resolve("out37.dex").toString(), null, false, 100, null, true);
        Map<?, ?> counts = (Map<?, ?>) re.get("counts");
        assertEquals(3, counts.get("types")); // the misdecode dropped Lprobe/Gone; (was 4)
    }

    // (d) era-matched successful output preserves the type reference:
    //     re-read under the original era resolves Lprobe/Gone; (type_ids unchanged)
    @Test
    void eraMatchedOutputPreservesTypeReference(@TempDir Path dir) throws Exception {
        byte[] original = buildThrowVerxDex();
        Path dex = dir.resolve("throwverr.dex");
        Files.write(dex, original);
        Map<String, Object> before = Commands.info(dex.toString(), 5, false, 100, "dalvik", true);
        Map<?, ?> beforeCounts = (Map<?, ?>) before.get("counts");
        assertEquals(4, beforeCounts.get("types"));

        Map<String, Object> out = Commands.convert(dex.toString(), "035",
                dir.resolve("rt.dex").toString(), 5, "dalvik", false);
        Map<String, Object> after = Commands.info(dir.resolve("rt.dex").toString(), 5, false, 100, "dalvik", true);
        Map<?, ?> afterCounts = (Map<?, ?>) after.get("counts");
        assertEquals(beforeCounts.get("types"), afterCounts.get("types"), "type_ids count unchanged");
        // the operand still resolves to Lprobe/Gone; — read the instruction back
        byte[] bytes = Files.readAllBytes(dir.resolve("rt.dex"));
        Dex d = DexIO.read(ReadOptions.defaultOptions().withTargetApi(5).withTargetForArt(false)
                .withOdexInstructions(true), bytes, 0, 0);
        MethodDef m = d.getClasses().iterator().next().getMethods().iterator().next();
        Instruction ins = m.getImplementation().getInstructions().get(0);
        assertEquals(Opcode.THROW_VERIFICATION_ERROR, ins.getOpcode());
        assertTrue(ins.toString().contains("Lprobe/Gone;"), "operand resolves to Lprobe/Gone;: " + ins);
    }

    // (e) non-ambiguous odex content behaves exactly as before: invoke-direct-empty
    //     (byte 0xf0 IS ambiguous, so use a genuinely non-ambiguous odex op —
    //      verified: IGET_QUICK at 0xe3 is ambiguous too; RETURN_VOID_NO_BARRIER at
    //      0x73 is ambiguous; every odex op for DEX035 lands in the 22-byte set, so
    //      the "non-ambiguous" case is exercised by the same-era allowance instead:
    //      era-matched read+write of an odex-bearing dex proceeds without refusal)
    @Test
    void sameEraAmbiguousContentStillConverts(@TempDir Path dir) throws Exception {
        Path dex = writeThrowVerx(dir);
        // --era dalvik --api 5 on read and write: same interpretation → allowed
        Map<String, Object> out = Commands.convert(dex.toString(), "035",
                dir.resolve("same.dex").toString(), 5, "dalvik", false);
        Map<?, ?> converted = ((List<?>) out.get("converted")).stream()
                .map(x -> (Map<?, ?>) x).findFirst().orElseThrow();
        // same read/write interpretation → allowed, ambiguous count reported honestly
        assertEquals(1, converted.get("ambiguous_odex_instructions"));
        assertTrue(Files.exists(dir.resolve("same.dex")));
        Map<String, Object> after = Commands.info(dir.resolve("same.dex").toString(), 5, false, 100, "dalvik", true);
        Map<?, ?> counts = (Map<?, ?>) after.get("counts");
        assertEquals(1, counts.get("classes"));
    }
}
