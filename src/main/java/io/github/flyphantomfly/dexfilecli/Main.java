package io.github.flyphantomfly.dexfilecli;

import com.v7878.dex.DexIO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dexfile-cli — read, convert, patch, assemble and split dex files through
 * the com.v7878.dex (DexFile) model. One JSON object per invocation on
 * stdout; logs on stderr; exit codes: 0 ok, 1 unexpected error, 2 usage,
 * 3 not-a-dex, 4 invalid-dex.
 */
public final class Main {

    private static final String USAGE = """
            usage:
              dexfile-cli info <dex|container|vdex> [--api N] [--classes] [--limit N]
              dexfile-cli extract <apk|dex|vdex> [--out DIR] [--api N]
              dexfile-cli convert <in> --to 035|036|037|038|039|040|041 [--out PATH] [--api N]
              dexfile-cli patch <dex> --ops <ops.json> [--out PATH] [--api N]
              dexfile-cli assemble <smali_file> [--out PATH] [--api N]
              dexfile-cli split <dex> [--out DIR] [--api N]
              dexfile-cli outline <dex> --class <dotted class name> [--api N]
              dexfile-cli validate <dex> [--max-issues N] [--api N]
              dexfile-cli merge <dex...> [--out PATH] [--api N] [--allow-cdex]

            ops file: a JSON array of operations:
              {"op": "replace-const-string", "old": "...", "new": "..."}
              {"op": "rename", "target": "class|method|field", "class": "a.b.C",
                     "name": "foo", "new_name": "bar"}
              {"op": "retarget-invoke", "class": "a.b.C", "name": "foo", "to_class": "x.y.Z"}
              {"op": "flip-flags", "class": "a.b.C", ["method": "m" | "field": "f"],
                     "set": ["STATIC"], "clear": ["FINAL"]}
              {"op": "replace-method-body", "class": "a.b.C", "method": "m",
                     "smali": "<method smali text>"}
            """;

    public static void main(String[] args) {
        int exit;
        try {
            exit = run(args);
        } catch (DexIO.NotADexFile e) {
            System.err.println("not-a-dex: " + e.getMessage());
            System.out.println(Json.encode(Json.obj("error", String.valueOf(e.getMessage()),
                    "kind", "not_a_dex")));
            exit = 3;
        } catch (DexIO.InvalidDexFile e) {
            System.err.println("invalid-dex: " + e.getMessage());
            System.out.println(Json.encode(Json.obj("error", String.valueOf(e.getMessage()),
                    "kind", "invalid_dex")));
            exit = 4;
        } catch (Commands.UsageException e) {
            System.err.println("usage: " + e.getMessage());
            System.out.println(Json.encode(Json.obj("error", String.valueOf(e.getMessage()),
                    "kind", "usage")));
            exit = 2;
        } catch (Exception e) {
            System.err.println("error: " + e);
            System.out.println(Json.encode(Json.obj("error", String.valueOf(e),
                    "kind", "unexpected")));
            exit = 1;
        }
        System.exit(exit);
    }

    private static int run(String[] args) {
        if (args.length == 0) {
            System.err.print(USAGE);
            System.out.println(Json.encode(Json.obj("error", "no subcommand", "usage", USAGE_LINES())));
            return 2;
        }
        String cmd = args[0];
        Map<String, String> flags = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    flags.put(key, args[++i]);
                } else {
                    flags.put(key, "true");
                }
            } else {
                positional.add(args[i]);
            }
        }
        Integer api = intFlag(flags, "api");
        Integer limit = intFlag(flags, "limit");

        Map<String, Object> out;
        switch (cmd) {
            case "info" -> {
                require(positional.size() == 1, "info needs exactly one input file");
                boolean listClasses = flags.containsKey("classes");
                int limitValue = limit != null ? limit : 100;
                out = Commands.info(positional.get(0), api, listClasses, limitValue);
            }
            case "extract" -> {
                require(positional.size() == 1, "extract needs exactly one input file");
                out = Commands.extract(positional.get(0), flags.get("out"), api);
            }
            case "convert" -> {
                require(positional.size() == 1, "convert needs exactly one input file");
                require(flags.containsKey("to"), "convert needs --to 035|036|037|038|039|040|041");
                out = Commands.convert(positional.get(0), flags.get("to"), flags.get("out"), api);
            }
            case "patch" -> {
                require(positional.size() == 1, "patch needs exactly one dex file");
                require(flags.containsKey("ops"), "patch needs --ops <ops.json>");
                out = Commands.patch(positional.get(0), flags.get("ops"), flags.get("out"), api);
            }
            case "assemble" -> {
                require(positional.size() == 1, "assemble needs exactly one smali file");
                out = Commands.assemble(positional.get(0), flags.get("out"), api);
            }
            case "outline" -> {
                require(positional.size() == 1, "outline needs exactly one dex file");
                require(flags.containsKey("class"), "outline needs --class <dotted class name>");
                out = Commands.outline(positional.get(0), flags.get("class"), api);
            }
            case "split" -> {
                require(positional.size() == 1, "split needs exactly one dex file");
                out = Commands.split(positional.get(0), flags.get("out"), api);
            }
            case "validate" -> {
                require(positional.size() == 1, "validate needs exactly one dex file");
                out = Commands.validate(positional.get(0), api,
                        flags.containsKey("max-issues") ? Integer.parseInt(flags.get("max-issues")) : null);
            }
            case "merge" -> {
                require(positional.size() >= 2, "merge needs at least two dex files");
                boolean allowCdex = flags.containsKey("allow-cdex");
                Integer mergeApi = flags.containsKey("api") ? api : null;
                if (allowCdex && mergeApi == null) {
                    mergeApi = 28; // CDEX001's floor — writeOptions validates the era
                }
                out = Commands.merge(positional, flags.get("out"), mergeApi, allowCdex);
            }
            default -> {
                System.err.print(USAGE);
                System.out.println(Json.encode(Json.obj("error", "unknown subcommand '" + cmd + "'",
                        "usage", USAGE_LINES())));
                return 2;
            }
        }
        System.out.println(Json.encode(out));
        return 0;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new Commands.UsageException(message);
        }
    }

    private static Integer intFlag(Map<String, String> flags, String key) {
        String raw = flags.get(key);
        if (raw == null || raw.equals("true")) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new Commands.UsageException("--" + key + " expects an integer, got '" + raw + "'");
        }
    }

    private static String USAGE_LINES() {
        return USAGE.strip();
    }
}
