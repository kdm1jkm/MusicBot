package com.jagrosh.jmusicbot.audio.ytdlp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A private extraction owned by one YouTube source, never installed or downloaded at runtime. */
public final class BundledYtDlpRuntime implements AutoCloseable {
    private static final String ROOT = "/ytdlp/windows-x86_64/";
    @FunctionalInterface interface Resources { InputStream open(String name) throws IOException; }

    private final Resources resources;
    private final Path temporaryParent;
    private final String os;
    private final String architecture;
    private final Function<Path, List<String>> ytDlpCommand;
    private final Function<Path, List<String>> denoCommand;
    private final Duration versionTimeout;
    private final List<Path> ownedFiles = new ArrayList<>();
    private Path directory;
    private Path executable;
    private Path denoExecutable;
    private boolean closed;

    private BundledYtDlpRuntime() {
        this(BundledYtDlpRuntime.class::getResourceAsStream, null,
                System.getProperty("os.name", ""), System.getProperty("os.arch", ""),
                path -> List.of(path.toAbsolutePath().toString()),
                path -> List.of(path.toAbsolutePath().toString()), Duration.ofSeconds(15));
    }

    // Test seams replace embedded resource streams and executable prefixes, never the process implementation.
    BundledYtDlpRuntime(Resources resources, Path temporaryParent, String os, String architecture,
                       Function<Path, List<String>> ytDlpCommand, Function<Path, List<String>> denoCommand,
                       Duration versionTimeout) {
        this.resources = resources;
        this.temporaryParent = temporaryParent;
        this.os = os;
        this.architecture = architecture;
        this.ytDlpCommand = ytDlpCommand;
        this.denoCommand = denoCommand;
        this.versionTimeout = versionTimeout;
    }

    public static BundledYtDlpRuntime open() throws IOException {
        return new BundledYtDlpRuntime().initialize();
    }

    synchronized BundledYtDlpRuntime initialize() throws IOException {
        if (closed) throw failure("Bundled YouTube runtime has been shut down.");
        if (executable != null) return this;
        if (!os.toLowerCase(Locale.ROOT).startsWith("windows")
                || !(architecture.equalsIgnoreCase("amd64") || architecture.equalsIgnoreCase("x86_64")))
            throw failure("Bundled YouTube playback requires Windows x64.");
        try {
            Properties manifest = new Properties();
            try (InputStream stream = resource("tools.properties")) { manifest.load(stream); }
            String ytHash = checksumProperty(manifest, "ytdlp.sha256");
            String denoHash = checksumProperty(manifest, "deno.sha256");
            if (manifest.getProperty("ytdlp.version", "").isBlank() || manifest.getProperty("deno.version", "").isBlank())
                throw failure("Bundled tool manifest is missing pinned versions.");
            directory = temporaryParent == null ? Files.createTempDirectory("jmusicbot-ytdlp-")
                    : Files.createTempDirectory(temporaryParent, "jmusicbot-ytdlp-");
            Path yt = owned("yt-dlp.exe");
            Path zip = owned("deno.zip");
            Path deno = owned("deno.exe");
            copyVerified("yt-dlp.exe", yt, ytHash);
            copyVerified("deno.zip", zip, denoHash);
            boolean found = false;
            try (ZipInputStream archive = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    if (entry.getName().equals("deno.exe") && !entry.isDirectory()) {
                        if (found) throw failure("Bundled Deno archive contains duplicate deno.exe entries.");
                        found = true;
                        try (OutputStream stream = Files.newOutputStream(deno)) { archive.transferTo(stream); }
                    }
                    archive.closeEntry();
                }
            }
            if (!found || Files.size(deno) == 0) throw failure("Bundled Deno archive is missing a usable deno.exe entry.");
            Files.delete(zip);
            validate(ytDlpCommand.apply(yt), yt);
            validate(denoCommand.apply(deno), deno);
            // Only publish after extraction, integrity checks and both actual launches succeed.
            executable = yt;
            denoExecutable = deno;
            return this;
        } catch (YtDlpProcessRunner.Failure ex) {
            cleanup();
            throw ex;
        } catch (IOException | SecurityException | IllegalArgumentException ex) {
            cleanup();
            throw failure("Bundled YouTube tools could not be extracted or read. Check temporary-directory permissions and local antivirus quarantine.");
        }
    }

    private void validate(List<String> prefix, Path localExecutable) throws IOException {
        ArrayList<String> command = new ArrayList<>(prefix);
        command.add("--version");
        try { YtDlpProcessRunner.validateVersion(command, versionTimeout); }
        catch (YtDlpProcessRunner.Failure ex) {
            throw new YtDlpProcessRunner.Failure(ex.category(), "Bundled tool validation failed (" + ex.category()
                    + ") for local executable " + localExecutable.toAbsolutePath()
                    + ". Check file permissions and local antivirus quarantine.");
        }
    }

    private static String checksumProperty(Properties manifest, String key) throws IOException {
        String value = manifest.getProperty(key, "");
        if (!value.matches("[a-fA-F0-9]{64}")) throw failure("Bundled tool manifest has a missing or invalid SHA-256 value.");
        return value;
    }

    private Path owned(String name) {
        Path path = directory.resolve(name);
        ownedFiles.add(path);
        return path;
    }

    private InputStream resource(String name) throws IOException {
        InputStream stream = resources.open(ROOT + name);
        if (stream == null) throw failure("Missing bundled YouTube resource: " + ROOT + name);
        return stream;
    }

    private void copyVerified(String resourceName, Path destination, String expected) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("Required SHA-256 unavailable", ex); }
        try (InputStream source = resource(resourceName); OutputStream output = Files.newOutputStream(destination)) {
            byte[] bytes = new byte[8192];
            int count;
            while ((count = source.read(bytes)) != -1) {
                digest.update(bytes, 0, count);
                output.write(bytes, 0, count);
            }
        }
        if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected))
            throw failure("Bundled YouTube resource checksum mismatch: " + resourceName);
    }

    public synchronized Path executable() {
        requireReady();
        return executable;
    }

    public synchronized Path denoExecutable() {
        requireReady();
        return denoExecutable;
    }

    private void requireReady() {
        if (closed || executable == null || denoExecutable == null)
            throw new IllegalStateException("Bundled YouTube runtime is not initialized or has been closed.");
    }

    @Override public synchronized void close() {
        closed = true;
        cleanup();
    }

    private void cleanup() {
        executable = null;
        denoExecutable = null;
        for (Path path : ownedFiles) deleteOwned(path);
        ownedFiles.clear();
        if (directory != null) deleteOwned(directory);
        directory = null;
    }

    private static void deleteOwned(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException | SecurityException ex) { path.toFile().deleteOnExit(); }
    }

    private static YtDlpProcessRunner.Failure failure(String message) {
        return new YtDlpProcessRunner.Failure(YtDlpProcessRunner.Category.INITIALIZATION, message);
    }
}
