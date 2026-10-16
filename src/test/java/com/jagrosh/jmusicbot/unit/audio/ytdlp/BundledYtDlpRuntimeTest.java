package com.jagrosh.jmusicbot.audio.ytdlp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BundledYtDlpRuntimeTest {
    @TempDir Path temporary;
    private static final String ROOT = "/ytdlp/windows-x86_64/";
    private static final byte[] YT = new byte[] { 0, 1, 2, (byte) 255, (byte) 128, 10, 13 };
    private static final byte[] DENO = new byte[] { (byte) 255, 0, 4, 10, 13, (byte) 128 };

    @Test void extractsByteIdenticalExecutablesWithoutZipTraversalAndLaunchesRealVersionChecks() throws Exception {
        Map<String, byte[]> resources = resources(zip(Map.of("deno.exe", DENO, "../../escape.exe", YT)));
        Path parent = temporary.resolve("spaces and 한글");
        Files.createDirectories(parent);
        BundledYtDlpRuntime runtime = runtime(resources, parent);
        assertThrows(IllegalStateException.class, runtime::executable);
        runtime.initialize();
        Path executable = runtime.executable();
        Path deno = runtime.denoExecutable();
        assertArrayEquals(YT, Files.readAllBytes(executable));
        assertArrayEquals(DENO, Files.readAllBytes(deno));
        assertEquals(executable.getParent(), deno.getParent());
        assertTrue(executable.getParent().getFileName().toString().startsWith("jmusicbot-ytdlp-"));
        assertFalse(Files.exists(temporary.resolve("escape.exe")));
        assertFalse(Files.exists(executable.getParent().resolve("deno.zip")));
        runtime.close();
        assertFalse(Files.exists(executable.getParent()));
        assertThrows(IllegalStateException.class, runtime::executable);
        assertThrows(YtDlpProcessRunner.Failure.class, runtime::initialize);
    }

    @Test void concurrentInitializationPublishesOneCompletedExtraction() throws Exception {
        Map<String, byte[]> resources = resources(zip(Map.of("deno.exe", DENO)));
        AtomicInteger executableReads = new AtomicInteger();
        BundledYtDlpRuntime.Resources provider = name -> {
            if (name.endsWith("yt-dlp.exe")) executableReads.incrementAndGet();
            byte[] bytes = resources.get(name);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        };
        try (BundledYtDlpRuntime runtime = new BundledYtDlpRuntime(provider, temporary, "Windows 11", "AMD64",
                path -> YtDlpTestChild.javaPrefix(), path -> YtDlpTestChild.javaPrefix(), Duration.ofSeconds(10));
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(runtime::initialize);
            var second = executor.submit(runtime::initialize);
            assertSame(runtime, first.get());
            assertSame(runtime, second.get());
            assertEquals(1, executableReads.get());
            try (var directories = Files.list(temporary)) { assertEquals(1, directories.count()); }
        }
    }

    @Test void corruptionOfEitherAssetNeverPublishesPathsAndCleansPartialExtraction() throws Exception {
        for (String asset : List.of("yt-dlp.exe", "deno.zip")) {
            Map<String, byte[]> resources = resources(zip(Map.of("deno.exe", DENO)));
            byte[] corrupt = resources.get(ROOT + asset).clone();
            corrupt[0] ^= 1;
            resources.put(ROOT + asset, corrupt);
            assertInitializationFailure(resources, "checksum mismatch");
        }
    }

    @Test void missingResourcesAndManifestChecksumsAreExplicitFailures() throws Exception {
        for (String missing : List.of("tools.properties", "yt-dlp.exe", "deno.zip")) {
            Map<String, byte[]> resources = resources(zip(Map.of("deno.exe", DENO)));
            resources.remove(ROOT + missing);
            assertInitializationFailure(resources, "Missing bundled YouTube resource");
        }
        Map<String, byte[]> resources = resources(zip(Map.of("deno.exe", DENO)));
        resources.put(ROOT + "tools.properties", "ytdlp.version=1\ndeno.version=1\nytdlp.sha256=not-a-hash\n".getBytes(StandardCharsets.UTF_8));
        assertInitializationFailure(resources, "invalid SHA-256");
    }

    @Test void missingEmptyOrDuplicateDenoEntriesNeverPublishRuntime() throws Exception {
        assertInitializationFailure(resources(zip(Map.of("other.exe", DENO))), "missing a usable deno.exe");
        assertInitializationFailure(resources(zip(Map.of("deno.exe", new byte[0]))), "missing a usable deno.exe");
        byte[] duplicate = zip(Map.of("deno.exe", DENO, "deno.exf", DENO));
        // ZipOutputStream forbids duplicate names; patch only the equal-length filename fields.
        byte[] from = "deno.exf".getBytes(StandardCharsets.US_ASCII);
        byte[] to = "deno.exe".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= duplicate.length - from.length; i++) {
            boolean matches = true;
            for (int j = 0; j < from.length; j++) if (duplicate[i + j] != from[j]) matches = false;
            if (matches) System.arraycopy(to, 0, duplicate, i, to.length);
        }
        assertInitializationFailure(resources(duplicate), "duplicate deno.exe");
    }

    @Test void unsupportedPlatformsNeverReadResources() {
        for (String[] platform : List.of(new String[] { "Linux", "amd64" }, new String[] { "Windows 11", "aarch64" })) {
            try (BundledYtDlpRuntime runtime = new BundledYtDlpRuntime(name -> {
                fail("Resources must remain lazy on unsupported platforms");
                return null;
            }, temporary, platform[0], platform[1], path -> List.of(), path -> List.of(), Duration.ofSeconds(10))) {
                YtDlpProcessRunner.Failure failure = assertThrows(YtDlpProcessRunner.Failure.class, runtime::initialize);
                assertEquals("Bundled YouTube playback requires Windows x64.", failure.getMessage());
            }
        }
    }

    @Test void shutdownDeletesOnlyOwnedFilesNotForeignFilesOrOtherRuntime() throws Exception {
        Map<String, byte[]> resources = resources(zip(Map.of("deno.exe", DENO)));
        try (BundledYtDlpRuntime first = runtime(resources, temporary);
             BundledYtDlpRuntime second = runtime(resources, temporary)) {
            first.initialize();
            second.initialize();
            Path firstExe = first.executable();
            Path secondExe = second.executable();
            Path foreign = firstExe.getParent().resolve("not-owned.txt");
            Files.writeString(foreign, "user file");
            first.close();
            assertFalse(Files.exists(firstExe));
            assertTrue(Files.exists(foreign));
            assertTrue(Files.exists(secondExe));
            assertNotEquals(firstExe.getParent(), secondExe.getParent());
        }
    }

    @Test void launchAndVersionFailuresHaveSafeCategoriesAndLocalContext() throws Exception {
        Map<String, byte[]> resources = resources(zip(Map.of("deno.exe", DENO)));
        for (String mode : List.of("sleep", "exit", "empty", "launch")) {
            Function<Path, List<String>> command = path -> {
                if (mode.equals("launch")) return List.of(path.toString() + ".missing");
                ArrayList<String> prefix = new ArrayList<>(YtDlpTestChild.javaPrefix());
                prefix.add(1, "-Dytdlp.fixture.versionMode=" + mode);
                return prefix;
            };
            try (BundledYtDlpRuntime runtime = new BundledYtDlpRuntime(provider(resources), temporary, "Windows", "x86_64",
                    command, path -> YtDlpTestChild.javaPrefix(), Duration.ofSeconds(2))) {
                YtDlpProcessRunner.Failure failure = assertThrows(YtDlpProcessRunner.Failure.class, runtime::initialize);
                YtDlpProcessRunner.Category expected = switch (mode) {
                    case "sleep" -> YtDlpProcessRunner.Category.TIMEOUT;
                    case "exit" -> YtDlpProcessRunner.Category.EXIT;
                    case "launch" -> YtDlpProcessRunner.Category.LAUNCH;
                    default -> YtDlpProcessRunner.Category.INITIALIZATION;
                };
                assertEquals(expected, failure.category());
                assertTrue(failure.getMessage().contains("local executable"));
                assertNull(failure.getCause());
                assertThrows(IllegalStateException.class, runtime::executable);
                try (var files = Files.list(temporary)) { assertEquals(0, files.count()); }
            }
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @EnabledIfSystemProperty(named = "os.arch", matches = "(?i)(amd64|x86_64)")
    void embeddedWindowsToolsLaunchWithNoInstalledDownloader() throws Exception {
        try (BundledYtDlpRuntime runtime = BundledYtDlpRuntime.open()) {
            assertTrue(Files.isRegularFile(runtime.executable()));
            assertTrue(Files.isRegularFile(runtime.denoExecutable()));
        }
    }

    private void assertInitializationFailure(Map<String, byte[]> resources, String expected) throws Exception {
        try (BundledYtDlpRuntime runtime = runtime(resources, temporary)) {
            YtDlpProcessRunner.Failure failure = assertThrows(YtDlpProcessRunner.Failure.class, runtime::initialize);
            assertEquals(YtDlpProcessRunner.Category.INITIALIZATION, failure.category());
            assertTrue(failure.getMessage().contains(expected), failure.getMessage());
            assertThrows(IllegalStateException.class, runtime::executable);
            assertThrows(IllegalStateException.class, runtime::denoExecutable);
            try (var files = Files.list(temporary)) { assertEquals(0, files.count()); }
        }
    }

    private static BundledYtDlpRuntime runtime(Map<String, byte[]> resources, Path parent) {
        return new BundledYtDlpRuntime(provider(resources), parent, "Windows 11", "amd64",
                path -> YtDlpTestChild.javaPrefix(), path -> YtDlpTestChild.javaPrefix(), Duration.ofSeconds(10));
    }
    private static BundledYtDlpRuntime.Resources provider(Map<String, byte[]> resources) {
        return name -> {
            byte[] bytes = resources.get(name);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        };
    }
    private static Map<String, byte[]> resources(byte[] zip) throws Exception {
        Map<String, byte[]> resources = new HashMap<>();
        resources.put(ROOT + "yt-dlp.exe", YT.clone());
        resources.put(ROOT + "deno.zip", zip);
        String manifest = "ytdlp.version=1\ndeno.version=1\nytdlp.sha256=" + sha(YT) + "\ndeno.sha256=" + sha(zip) + "\n";
        resources.put(ROOT + "tools.properties", manifest.getBytes(StandardCharsets.UTF_8));
        return resources;
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
