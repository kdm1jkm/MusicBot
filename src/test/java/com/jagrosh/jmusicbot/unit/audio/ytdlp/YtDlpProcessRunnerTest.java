package com.jagrosh.jmusicbot.audio.ytdlp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class YtDlpProcessRunnerTest {
    @TempDir Path temporary;
    private static final Duration NORMAL = Duration.ofSeconds(20);

    private YtDlpProcessRunner runner() throws IOException {
        return new YtDlpProcessRunner(YtDlpTestChild.javaPrefix(), List.of(), null, 32 * 1024 * 1024);
    }

    private YtDlpProcessRunner operandRunner() throws IOException {
        if (!System.getProperty("os.name").startsWith("Windows")) return runner();
        // Java's Windows launcher converts argv through ANSI. Test the process boundary
        // with a Unicode-native consumer instead of losing characters inside that fixture.
        Path script = temporary.resolve("operand.ps1");
        Files.writeString(script, """
                [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
                if ($args.Count -ne 2 -or $args[0] -cne '--') { exit 11 }
                @{ operand = $args[1] } | ConvertTo-Json -Compress
                """, StandardCharsets.UTF_8);
        Path powershell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell",
                "v1.0", "powershell.exe");
        return new YtDlpProcessRunner(List.of(powershell.toString(), "-NoLogo", "-NoProfile",
                "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString()),
                List.of(), null, 4096);
    }

    private static List<String> args(String mode) { return List.of("--", mode); }
    private static List<String> recorded(String mode, Path file) { return List.of("--test-file", file.toString(), "--", mode); }

    @Test void concurrentlyDrainsMoreThanPipeCapacityAndKeepsUtf8() throws Exception {
        try (YtDlpProcessRunner runner = runner()) {
            assertTrue(runner.run(args("noisy"), NORMAL).get("ok").asBoolean());
            assertEquals("윤하 사건의 지평선", runner.run(args("valid"), NORMAL).get("title").asText());
        }
    }

    @Test void categorizesFailuresWithoutExposingDiagnosticSecretsAndReleasesPermits() throws Exception {
        try (YtDlpProcessRunner runner = runner()) {
            assertFailure(runner, "malformed", YtDlpProcessRunner.Category.INVALID_JSON);
            assertFailure(runner, "trailing", YtDlpProcessRunner.Category.INVALID_JSON);
            assertFailure(runner, "oversized", YtDlpProcessRunner.Category.OUTPUT_TOO_LARGE);
            assertFailure(runner, "exit", YtDlpProcessRunner.Category.EXIT);
            YtDlpProcessRunner.Failure auth = assertFailure(runner, "auth", YtDlpProcessRunner.Category.AUTHENTICATION);
            assertEquals("YouTube requires authentication or rejected this IP. Configure playback.youtube.cookiesFile with a valid YouTube cookies file.", auth.getMessage());
            assertTrue(runner.run(args("valid"), NORMAL).get("ok").asBoolean());
        }
    }

    @Test void timeoutTerminatesChildAndDescendant() throws Exception {
        Path pids = temporary.resolve("timeout-pids");
        try (YtDlpProcessRunner runner = runner()) {
            YtDlpProcessRunner.Failure failure = assertThrows(YtDlpProcessRunner.Failure.class,
                    () -> runner.run(recorded("spawn", pids), Duration.ofSeconds(4)));
            assertEquals(YtDlpProcessRunner.Category.TIMEOUT, failure.category());
            assertDead(waitForPids(pids, 2));
            assertTrue(runner.run(args("valid"), NORMAL).get("ok").asBoolean());
        }
    }

    @EnabledOnOs(OS.WINDOWS) // Unix orphan reaping belongs to the host's init process, not Java.
    @Test void tracksDescendantEvenAfterItsParentExits() throws Exception {
        Path pids = temporary.resolve("orphan-pids");
        try (YtDlpProcessRunner runner = runner()) {
            // Some JVMs close the exited parent's pipes immediately; others retain inherited handles.
            try {
                assertTrue(runner.run(recorded("orphan", pids), Duration.ofSeconds(4)).isObject());
            } catch (YtDlpProcessRunner.Failure failure) {
                assertEquals(YtDlpProcessRunner.Category.TIMEOUT, failure.category());
            }
            assertDead(waitForPids(pids, 2));
        }
    }

    @Test void interruptionPreservesFlagTerminatesTreeAndReleasesPermit() throws Exception {
        Path pids = temporary.resolve("interruption-pids");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean preserved = new AtomicBoolean();
        try (YtDlpProcessRunner runner = runner()) {
            Thread thread = Thread.ofVirtual().start(() -> {
                try { runner.run(recorded("spawn", pids), NORMAL); }
                catch (Throwable ex) { failure.set(ex); preserved.set(Thread.currentThread().isInterrupted()); }
            });
            List<Long> observed = waitForPids(pids, 2);
            thread.interrupt();
            thread.join(10000);
            assertFalse(thread.isAlive());
            assertInstanceOf(InterruptedException.class, failure.get());
            assertTrue(preserved.get());
            assertDead(observed);
            assertTrue(runner.run(args("valid"), NORMAL).get("ok").asBoolean());
        }
    }

    @Test void enforcesFourProcessesAndIncludesPermitAcquisitionInDeadline() throws Exception {
        try (YtDlpProcessRunner runner = runner(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> running = new ArrayList<>();
            List<Long> pids = new ArrayList<>();
            try {
                for (int i = 0; i < 4; i++) {
                    Path record = temporary.resolve("limit-" + i);
                    running.add(executor.submit(() -> runner.run(recorded("sleep", record), NORMAL)));
                    pids.addAll(waitForPids(record, 1));
                }
                Path fifth = temporary.resolve("not-launched");
                YtDlpProcessRunner.Failure failure = assertThrows(YtDlpProcessRunner.Failure.class,
                        () -> runner.run(recorded("sleep", fifth), Duration.ofMillis(150)));
                assertEquals(YtDlpProcessRunner.Category.TIMEOUT, failure.category());
                assertFalse(Files.exists(fifth));
            } finally {
                running.forEach(future -> future.cancel(true));
            }
            assertDead(pids);
            assertTrue(runner.run(args("valid"), NORMAL).get("ok").asBoolean());
        }
    }

    @Test void shutdownCancelsTreesAndPreventsFurtherLaunch() throws Exception {
        Path pids = temporary.resolve("shutdown-pids");
        try (YtDlpProcessRunner runner = runner(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<YtDlpProcessRunner.Category> running = executor.submit(() -> {
                try { runner.run(recorded("spawn", pids), NORMAL); return null; }
                catch (YtDlpProcessRunner.Failure ex) { return ex.category(); }
            });
            List<Long> observed = waitForPids(pids, 2);
            runner.close();
            assertEquals(YtDlpProcessRunner.Category.CLOSED, running.get(10, TimeUnit.SECONDS));
            assertDead(observed);
            assertEquals(YtDlpProcessRunner.Category.CLOSED,
                    assertThrows(YtDlpProcessRunner.Failure.class, () -> runner.run(args("valid"), NORMAL)).category());
        }
    }

    @Test void cookieRewriteOnlyTouchesPrivateCopyAndDeletesIt() throws Exception {
        Path original = temporary.resolve("user cookies 한글.txt");
        byte[] bytes = "# Netscape HTTP Cookie File\n.youtube.com\tTRUE\t/\tTRUE\t0\tname\tsecret\n".getBytes(StandardCharsets.UTF_8);
        Files.write(original, bytes);
        try (YtDlpProcessRunner runner = new YtDlpProcessRunner(YtDlpTestChild.javaPrefix(), List.of(), original, 4096)) {
            JsonNode result = runner.run(args("cookies"), NORMAL);
            Path copy = Path.of(result.get("copy").asText());
            assertNotEquals(original, copy);
            assertArrayEquals(bytes, Files.readAllBytes(original));
            assertFalse(Files.exists(copy));
            assertFalse(Files.exists(copy.getParent()));
        }
    }

    @Test void cookieCopiesAreDeletedAfterExitTimeoutInterruptionAndShutdown() throws Exception {
        Path original = temporary.resolve("private-original.txt");
        byte[] originalBytes = "# Netscape HTTP Cookie File\noriginal-secret".getBytes(StandardCharsets.UTF_8);
        Files.write(original, originalBytes);
        for (String scenario : List.of("exit", "timeout", "interrupt", "shutdown")) {
            Path record = temporary.resolve("cookie-copy-" + scenario);
            try (YtDlpProcessRunner runner = new YtDlpProcessRunner(YtDlpTestChild.javaPrefix(), List.of(), original, 4096)) {
                String mode = scenario.equals("exit") ? "exit" : "sleep";
                List<String> arguments = List.of("--copy-record", record.toString(), "--", mode);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                AtomicBoolean interruptPreserved = new AtomicBoolean();
                Thread operation = Thread.ofVirtual().start(() -> {
                    try { runner.run(arguments, scenario.equals("timeout") ? Duration.ofSeconds(4) : NORMAL); }
                    catch (Throwable ex) {
                        failure.set(ex);
                        interruptPreserved.set(Thread.currentThread().isInterrupted());
                    }
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while ((!Files.exists(record) || Files.size(record) == 0) && System.nanoTime() < deadline) Thread.sleep(10);
                assertTrue(Files.exists(record), "Child must observe its private cookie copy");
                Path copy = Path.of(Files.readString(record));
                if (scenario.equals("interrupt")) operation.interrupt();
                if (scenario.equals("shutdown")) runner.close();
                operation.join(10000);
                assertFalse(operation.isAlive());
                if (scenario.equals("interrupt")) {
                    assertInstanceOf(InterruptedException.class, failure.get());
                    assertTrue(interruptPreserved.get());
                } else {
                    YtDlpProcessRunner.Failure safe = assertInstanceOf(YtDlpProcessRunner.Failure.class, failure.get());
                    YtDlpProcessRunner.Category expected = switch (scenario) {
                        case "exit" -> YtDlpProcessRunner.Category.EXIT;
                        case "timeout" -> YtDlpProcessRunner.Category.TIMEOUT;
                        default -> YtDlpProcessRunner.Category.CLOSED;
                    };
                    assertEquals(expected, safe.category());
                }
                assertFalse(Files.exists(copy));
                assertFalse(Files.exists(copy.getParent()));
                assertArrayEquals(originalBytes, Files.readAllBytes(original));
            }
        }
    }

    @Test void invalidCookiePathIsNotSilentlyAnonymous() {
        YtDlpProcessRunner.Failure failure = assertThrows(YtDlpProcessRunner.Failure.class,
                () -> new YtDlpProcessRunner(YtDlpTestChild.javaPrefix(), List.of(), temporary.resolve("missing"), 4096));
        assertEquals(YtDlpProcessRunner.Category.IO, failure.category());
    }

    @Test void optionLookingOperandRemainsOneDataArgumentAndBadShapeIsRejected() throws Exception {
        try (YtDlpProcessRunner runner = operandRunner()) {
            String operand = "--exec=malicious 윤하 & calc.exe";
            assertEquals(operand, runner.run(args(operand), NORMAL).get("operand").asText());
            assertThrows(IllegalArgumentException.class, () -> runner.run(List.of("--", "one", "two"), NORMAL));
        }
    }

    @Test void launchFailureIsCategorizedAndDoesNotLeakArguments() throws Exception {
        try (YtDlpProcessRunner runner = new YtDlpProcessRunner(List.of(temporary.resolve("missing.exe").toString()), List.of(), null, 4096)) {
            YtDlpProcessRunner.Failure failure = assertThrows(YtDlpProcessRunner.Failure.class,
                    () -> runner.run(args("credential-secret"), NORMAL));
            assertEquals(YtDlpProcessRunner.Category.LAUNCH, failure.category());
            assertFalse(failure.getMessage().contains("credential-secret"));
            assertNull(failure.getCause());
        }
    }

    private static YtDlpProcessRunner.Failure assertFailure(YtDlpProcessRunner runner, String mode,
                                                           YtDlpProcessRunner.Category category) {
        YtDlpProcessRunner.Failure failure = assertThrows(YtDlpProcessRunner.Failure.class,
                () -> runner.run(args(mode), NORMAL));
        assertEquals(category, failure.category());
        assertFalse(failure.getMessage().contains("credential-secret"));
        assertFalse(failure.getMessage().contains("signed.example"));
        assertNull(failure.getCause());
        return failure;
    }

    private static List<Long> waitForPids(Path path, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                if (lines.size() >= count && lines.stream().allMatch(line -> line.matches("[0-9]+")))
                    return lines.stream().map(Long::parseLong).toList();
            }
            Thread.sleep(10);
        }
        fail("Child did not record its process IDs");
        return List.of();
    }

    private static void assertDead(List<Long> pids) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && pids.stream().anyMatch(YtDlpProcessRunnerTest::alive)) Thread.sleep(10);
        for (long pid : pids) assertFalse(alive(pid), "Process still alive: " + pid);
    }
    private static boolean alive(long pid) { return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); }
}
