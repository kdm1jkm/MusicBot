package com.jagrosh.jmusicbot.audio.ytdlp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real executable fixture: does not substitute for the process/drain/lifecycle implementation. */
public final class YtDlpTestChild {
    private YtDlpTestChild() { }

    public static void main(String[] args) throws Exception {
        List<String> arguments = Arrays.asList(args);
        if (arguments.contains("--version")) {
            String versionMode = System.getProperty("ytdlp.fixture.versionMode", "valid");
            if (versionMode.equals("sleep")) Thread.sleep(60000);
            if (versionMode.equals("exit")) System.exit(23);
            if (versionMode.equals("empty")) return;
            System.out.println("test-tool 1.0");
            return;
        }
        String mode = args[args.length - 1];
        String file = option(arguments, "--test-file");
        if (file != null) Files.writeString(Path.of(file), Long.toString(ProcessHandle.current().pid()) + "\n");
        String copyRecord = option(arguments, "--copy-record");
        if (copyRecord != null) {
            Path copy = Path.of(option(arguments, "--cookies"));
            Files.writeString(copy, "rewritten by extractor", StandardCharsets.UTF_8);
            Files.writeString(Path.of(copyRecord), copy.toString());
        }
        // The production runner must close child stdin even for children which never read input.
        if (System.in.read() != -1) throw new IllegalStateException("Expected closed stdin");
        switch (mode) {
            case "valid" -> System.out.println("{\"title\":\"윤하 사건의 지평선\",\"ok\":true}");
            case "noisy" -> {
                byte[] noise = "credential-secret https://signed.example/token?secret=credential-secret\n".getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < 40000; i++) System.err.write(noise);
                System.out.println("{\"ok\":true}");
            }
            case "malformed" -> System.out.println("not-json credential-secret");
            case "trailing" -> System.out.println("{} {}");
            case "oversized" -> {
                System.out.print("{\"data\":\"");
                byte[] bytes = new byte[8192];
                Arrays.fill(bytes, (byte) 'a');
                for (int i = 0; i < 4300; i++) System.out.write(bytes);
                System.out.println("\"}");
            }
            case "exit" -> {
                System.err.println("credential-secret https://signed.example/token?secret=credential-secret");
                System.exit(23);
            }
            case "auth" -> {
                System.err.println("Sign in to confirm you're not a bot credential-secret");
                System.exit(1);
            }
            case "cookies" -> {
                Path copy = Path.of(option(arguments, "--cookies"));
                Files.writeString(copy, "rewritten by extractor", StandardCharsets.UTF_8);
                System.out.println("{\"copy\":\"" + copy.toString().replace("\\", "\\\\") + "\"}");
            }
            case "sleep" -> Thread.sleep(60000);
            case "spawn", "orphan" -> {
                ArrayList<String> command = new ArrayList<>(javaPrefix());
                command.add("sleep");
                Process descendant = new ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.PIPE)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                descendant.getOutputStream().close();
                Files.writeString(Path.of(file), ProcessHandle.current().pid() + "\n" + descendant.pid() + "\n");
                if (mode.equals("orphan")) {
                    System.out.println("{}");
                    Thread.sleep(500); // Permit the production watcher to observe this descendant before reparenting.
                } else {
                    Thread.sleep(60000);
                }
            }
            default -> System.out.println("{\"operand\":\"" + mode.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
        }
    }

    static List<String> javaPrefix() {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        return List.of(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                YtDlpTestChild.class.getName());
    }

    private static String option(List<String> arguments, String name) {
        int position = arguments.indexOf(name);
        return position < 0 ? null : arguments.get(position + 1);
    }
}
