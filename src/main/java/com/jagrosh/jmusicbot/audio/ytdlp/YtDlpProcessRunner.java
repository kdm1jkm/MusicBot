package com.jagrosh.jmusicbot.audio.ytdlp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import org.apache.http.HttpHost;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Source-owned, bounded yt-dlp subprocesses. No shell or inherited yt-dlp configuration. */
public final class YtDlpProcessRunner implements AutoCloseable {
    public enum Category { INITIALIZATION, LAUNCH, CLOSED, TIMEOUT, OUTPUT_TOO_LARGE, INVALID_JSON, EXIT, AUTHENTICATION, IO }

    /** Safe to expose at the source boundary: neither diagnostic text nor process arguments escape. */
    public static final class Failure extends IOException {
        private final Category category;
        public Failure(Category category, String message) {
            super(message);
            this.category = category;
        }
        public Category category() { return category; }
    }

    private static final int JSON_LIMIT = 32 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final List<String> commandPrefix;
    private final List<String> common;
    private final Path cookies;
    private final int outputLimit;
    private final Semaphore permits = new Semaphore(4, true);
    private final Object lifecycle = new Object();
    private final Set<Operation> active = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public YtDlpProcessRunner(BundledYtDlpRuntime runtime, String cookiesFile, HttpHost proxy) throws IOException {
        this(List.of(runtime.executable().toAbsolutePath().toString()),
                commonArguments(runtime.denoExecutable(), proxy), cookiePath(cookiesFile), JSON_LIMIT);
    }

    // Test seam: actual child processes, different executable prefix and bounded-output threshold.
    YtDlpProcessRunner(List<String> commandPrefix, List<String> common, Path cookies, int outputLimit) throws IOException {
        this.commandPrefix = List.copyOf(commandPrefix);
        this.common = List.copyOf(common);
        this.cookies = cookies == null ? null : cookies.toAbsolutePath();
        this.outputLimit = outputLimit;
        validateCookies(this.cookies);
    }

    private static List<String> commonArguments(Path deno, HttpHost proxy) {
        return List.of("--ignore-config", "--no-plugin-dirs", "--no-remote-components",
                "--no-js-runtimes", "--js-runtimes", "deno:" + deno.toAbsolutePath(),
                "--no-update", "--no-cache-dir", "--no-progress", "--color", "never",
                "--encoding", "UTF-8", "--socket-timeout", "15", "--retries", "0",
                "--extractor-retries", "0", "--skip-download", "--dump-single-json",
                "--proxy", proxy == null ? "" : proxy.toURI());
    }

    private static Path cookiePath(String value) throws IOException {
        if (value == null || value.isBlank()) return null;
        try { return OtherUtil.getPath(value); }
        catch (InvalidPathException ex) { throw new Failure(Category.IO, "Configured YouTube cookies file path is invalid."); }
    }

    private static void validateCookies(Path path) throws IOException {
        if (path != null && (!Files.isRegularFile(path) || !Files.isReadable(path)))
            throw new Failure(Category.IO, "Configured YouTube cookies file must be a readable regular file.");
    }

    public JsonNode run(List<String> arguments, Duration timeout) throws IOException, InterruptedException {
        int separator = arguments.indexOf("--");
        if (separator != arguments.size() - 2 || separator < 0 || arguments.lastIndexOf("--") != separator)
            throw new IllegalArgumentException("Expected options followed by -- and one YouTube operand.");
        long deadline = deadline(timeout);
        boolean acquired = false;
        Path cookieCopy = null;
        Path cookieDirectory = null;
        try {
            if (closed) throw closedFailure();
            acquired = permits.tryAcquire(remaining(deadline), TimeUnit.NANOSECONDS);
            if (!acquired) throw timeoutFailure();
            if (closed) throw closedFailure();
            ArrayList<String> command = new ArrayList<>(commandPrefix);
            command.addAll(common);
            if (cookies != null) {
                validateCookies(cookies);
                cookieDirectory = Files.createTempDirectory("jmusicbot-ytdlp-cookies-");
                cookieCopy = cookieDirectory.resolve("cookies.txt");
                Files.copy(cookies, cookieCopy, StandardCopyOption.REPLACE_EXISTING);
                command.add("--cookies");
                command.add(cookieCopy.toString());
            }
            command.addAll(arguments);
            Operation operation;
            synchronized (lifecycle) {
                if (closed) throw closedFailure();
                if (remaining(deadline) == 0) throw timeoutFailure();
                operation = new Operation(command, outputLimit);
                active.add(operation);
            }
            byte[] bytes;
            try { bytes = operation.await(deadline); }
            finally {
                operation.stop();
                active.remove(operation);
            }
            try {
                JsonNode result = JSON.readTree(bytes);
                if (result == null || !result.isObject()) throw new Failure(Category.INVALID_JSON, "YouTube extractor returned invalid JSON.");
                if (remaining(deadline) == 0) throw timeoutFailure();
                return result;
            } catch (Failure ex) { throw ex; }
            catch (IOException ex) { throw new Failure(Category.INVALID_JSON, "YouTube extractor returned invalid JSON."); }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (Failure ex) { throw ex; }
        catch (IOException ex) { throw new Failure(Category.IO, "YouTube extraction encountered a local I/O failure."); }
        finally {
            if (cookieCopy != null) deletePrivate(cookieCopy);
            if (cookieDirectory != null) deletePrivate(cookieDirectory);
            if (acquired) permits.release();
        }
    }

    private static void deletePrivate(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ex) { path.toFile().deleteOnExit(); }
    }

    @Override public void close() {
        List<Operation> running;
        synchronized (lifecycle) {
            closed = true;
            running = List.copyOf(active);
        }
        // Stop independently: shutdown latency does not multiply by the process limit.
        List<Thread> stoppers = running.stream().map(op -> Thread.ofVirtual().start(op::stop)).toList();
        boolean interrupted = Thread.interrupted();
        for (Thread thread : stoppers) {
            boolean joined = false;
            while (!joined) {
                try { thread.join(); joined = true; }
                catch (InterruptedException ex) { interrupted = true; }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    static void validateVersion(List<String> command, Duration timeout) throws IOException {
        Operation operation = new Operation(command, 64 * 1024);
        try {
            byte[] output = operation.await(deadline(timeout));
            if (new String(output, StandardCharsets.UTF_8).isBlank())
                throw new Failure(Category.INITIALIZATION, "Bundled tool version check returned no version.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Failure(Category.INITIALIZATION, "Bundled tool initialization was interrupted.");
        } finally { operation.stop(); }
    }

    private static Failure closedFailure() { return new Failure(Category.CLOSED, "YouTube extractor has been shut down."); }
    private static Failure timeoutFailure() { return new Failure(Category.TIMEOUT, "YouTube extraction exceeded its deadline."); }
    private static long deadline(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive timeout required.");
        long nanos;
        try { nanos = timeout.toNanos(); }
        catch (ArithmeticException ex) { nanos = Long.MAX_VALUE / 2; }
        return System.nanoTime() + Math.min(nanos, Long.MAX_VALUE / 2);
    }
    private static long remaining(long deadline) { return Math.max(0, deadline - System.nanoTime()); }

    private static final class Operation {
        final Process process;
        final Set<ProcessHandle> descendants = ConcurrentHashMap.newKeySet();
        final AtomicReference<Category> readFailure = new AtomicReference<>();
        final CompletableFuture<byte[]> stdout = new CompletableFuture<>();
        final CompletableFuture<Void> stderrDone = new CompletableFuture<>();
        final DiagnosticTail stderr = new DiagnosticTail();
        final Thread watcher;
        volatile boolean stopped;

        Operation(List<String> command, int limit) throws IOException {
            try {
                process = new ProcessBuilder(command).start();
            } catch (IOException | SecurityException ex) {
                throw new Failure(Category.LAUNCH, "Could not launch bundled YouTube tool. Check executable file permissions and local antivirus quarantine.");
            }
            watcher = Thread.ofVirtual().start(() -> {
                while (!stopped) {
                    captureDescendants();
                    try { Thread.sleep(10); }
                    catch (InterruptedException ex) { return; }
                }
            });
            Thread.ofVirtual().start(() -> {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(limit, 8192));
                try (InputStream stream = process.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = stream.read(chunk)) != -1) {
                        if (buffer.size() <= limit - count && readFailure.get() != Category.OUTPUT_TOO_LARGE)
                            buffer.write(chunk, 0, count);
                        else readFailure.set(Category.OUTPUT_TOO_LARGE);
                    }
                    stdout.complete(buffer.toByteArray());
                } catch (IOException ex) { readFailure.compareAndSet(null, Category.IO); stdout.complete(new byte[0]); }
            });
            Thread.ofVirtual().start(() -> {
                try (InputStream stream = process.getErrorStream()) {
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = stream.read(chunk)) != -1) stderr.append(chunk, count);
                } catch (IOException ex) { readFailure.compareAndSet(null, Category.IO); }
                finally { stderrDone.complete(null); }
            });
            try { process.getOutputStream().close(); }
            catch (IOException ex) {
                stop();
                throw new Failure(Category.LAUNCH, "Could not initialize bundled YouTube tool input.");
            }
        }

        byte[] await(long deadline) throws IOException, InterruptedException {
            while (true) {
                if (Thread.interrupted()) throw new InterruptedException("YouTube extraction interrupted.");
                if (stopped) throw closedFailure();
                Category failure = readFailure.get();
                if (failure != null) throw new Failure(failure, failure == Category.OUTPUT_TOO_LARGE
                        ? "YouTube extractor JSON exceeded the output limit." : "Could not read YouTube extractor output.");
                if (remaining(deadline) == 0) throw timeoutFailure();
                if (!process.isAlive() && stdout.isDone() && stderrDone.isDone()) break;
                TimeUnit.NANOSECONDS.sleep(Math.min(remaining(deadline), TimeUnit.MILLISECONDS.toNanos(10)));
            }
            Category completedFailure = readFailure.get();
            if (completedFailure != null) throw new Failure(completedFailure, completedFailure == Category.OUTPUT_TOO_LARGE
                    ? "YouTube extractor JSON exceeded the output limit." : "Could not read YouTube extractor output.");
            if (process.exitValue() != 0) {
                Category category = stderr.authenticationFailure() ? Category.AUTHENTICATION : Category.EXIT;
                throw new Failure(category, category == Category.AUTHENTICATION
                        ? "YouTube requires authentication or rejected this IP. Configure playback.youtube.cookiesFile with a valid YouTube cookies file."
                        : "YouTube extractor failed (exit " + process.exitValue() + ").");
            }
            return stdout.join();
        }

        private void captureDescendants() {
            process.descendants().forEach(descendants::add);
            // Retain descendants after the root exits, and discover children they spawn during shutdown.
            descendants.forEach(handle -> handle.descendants().forEach(descendants::add));
        }

        synchronized void stop() {
            if (stopped) return;
            boolean interrupted = Thread.interrupted();
            captureDescendants();
            stopped = true;
            watcher.interrupt();
            descendants.forEach(handle -> { if (handle.isAlive()) handle.destroy(); });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            // Keep the root alive briefly to reap its children (important on Unix test hosts).
            while (descendants.stream().anyMatch(ProcessHandle::isAlive) && remaining(deadline) > 0) {
                captureDescendants();
                descendants.forEach(handle -> { if (handle.isAlive()) handle.destroy(); });
                try { Thread.sleep(10); }
                catch (InterruptedException ex) { interrupted = true; }
            }
            if (process.isAlive()) process.destroy();
            while ((process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) && remaining(deadline) > 0) {
                captureDescendants();
                descendants.forEach(handle -> { if (handle.isAlive()) handle.destroy(); });
                try { Thread.sleep(10); }
                catch (InterruptedException ex) { interrupted = true; }
            }
            descendants.forEach(handle -> { if (handle.isAlive()) handle.destroyForcibly(); });
            if (process.isAlive()) process.destroyForcibly();
            long forcedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) && remaining(forcedDeadline) > 0) {
                captureDescendants();
                descendants.forEach(handle -> { if (handle.isAlive()) handle.destroyForcibly(); });
                try { Thread.sleep(10); }
                catch (InterruptedException ex) { interrupted = true; }
            }
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            try { process.getErrorStream().close(); } catch (IOException ignored) { }
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /** Retains only the final 16 KiB; raw diagnostics are deliberately never exposed. */
    private static final class DiagnosticTail {
        private final byte[] ring = new byte[16 * 1024];
        private int position;
        private int size;
        synchronized void append(byte[] data, int length) {
            for (int i = 0; i < length; i++) { ring[position] = data[i]; position = (position + 1) % ring.length; }
            size = Math.min(ring.length, size + length);
        }
        synchronized boolean authenticationFailure() {
            byte[] bytes = new byte[size];
            int start = (position - size + ring.length) % ring.length;
            for (int i = 0; i < size; i++) bytes[i] = ring[(start + i) % ring.length];
            String text = new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return text.contains("sign in") || text.contains("sign-in") || text.contains("not a bot")
                    || text.contains("login required") || text.contains("authentication") || text.contains("registered users")
                    || text.contains("members-only");
        }
    }
}
