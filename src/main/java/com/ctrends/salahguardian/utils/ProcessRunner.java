package com.ctrends.salahguardian.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Thin, defensive wrapper around {@link ProcessBuilder}.
 *
 * <p>Every external integration in this application (GeoClue via {@code gdbus},
 * desktop notifications via {@code notify-send}, tool discovery via
 * {@code which}) goes through here so that a missing binary, a hung daemon or a
 * flood of output can never block the scheduler thread.</p>
 *
 * <p>Guarantees:</p>
 * <ul>
 *   <li>the command is always killed once its deadline elapses;</li>
 *   <li>stdout and stderr are drained concurrently, so a chatty child process
 *       cannot deadlock on a full pipe buffer;</li>
 *   <li>no checked exception escapes - failures are reported through
 *       {@link ProcessResult}.</li>
 * </ul>
 *
 * @author CTrends Software
 */
public final class ProcessRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessRunner.class);

    /** Deadline applied when a caller does not specify one. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /** Upper bound on captured output, guarding against runaway processes. */
    private static final int MAX_CAPTURE_BYTES = 256 * 1024;

    private ProcessRunner() {
        // utility class
    }

    /**
     * Runs a command with the {@linkplain #DEFAULT_TIMEOUT default deadline}.
     *
     * @param command the executable and its arguments
     * @return the captured outcome
     */
    public static ProcessResult run(String... command) {
        return run(DEFAULT_TIMEOUT, List.of(command));
    }

    /**
     * Runs a command with an explicit deadline.
     *
     * @param timeout maximum time the command may take
     * @param command the executable and its arguments
     * @return the captured outcome
     */
    public static ProcessResult run(Duration timeout, List<String> command) {
        if (command == null || command.isEmpty()) {
            return new ProcessResult(-1, "", "empty command", false);
        }
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
            process.getOutputStream().close();

            StreamCollector out = StreamCollector.start(process.getInputStream());
            StreamCollector err = StreamCollector.start(process.getErrorStream());

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                LOG.debug("Command timed out after {} ms: {}", timeout.toMillis(), command);
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                return new ProcessResult(-1, out.join(), err.join(), true);
            }
            return new ProcessResult(process.exitValue(), out.join(), err.join(), false);
        } catch (IOException e) {
            LOG.debug("Command not executable: {} ({})", command, e.getMessage());
            return new ProcessResult(-1, "", String.valueOf(e.getMessage()), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new ProcessResult(-1, "", "interrupted", false);
        }
    }

    /**
     * Tests whether an executable exists on the {@code PATH}.
     *
     * @param executable the binary name, e.g. {@code notify-send}
     * @return {@code true} when the binary can be located
     */
    public static boolean isCommandAvailable(String executable) {
        if (executable == null || executable.isBlank()) {
            return false;
        }
        ProcessResult result = run(Duration.ofSeconds(3), List.of("which", executable));
        return result.isSuccess() && !result.trimmedOutput().isEmpty();
    }

    /**
     * Runs a command and returns its standard output only when it succeeded.
     *
     * @param timeout maximum time the command may take
     * @param command the executable and its arguments
     * @return the trimmed output of a successful run
     */
    public static Optional<String> capture(Duration timeout, List<String> command) {
        ProcessResult result = run(timeout, command);
        if (!result.isSuccess()) {
            return Optional.empty();
        }
        String output = result.trimmedOutput();
        return output.isEmpty() ? Optional.empty() : Optional.of(output);
    }

    /**
     * Drains one stream of a child process on its own daemon thread.
     */
    private static final class StreamCollector {

        private final Thread thread;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private StreamCollector(InputStream stream) {
            this.thread = new Thread(() -> drain(stream), "process-stream-collector");
            this.thread.setDaemon(true);
        }

        static StreamCollector start(InputStream stream) {
            StreamCollector collector = new StreamCollector(stream);
            collector.thread.start();
            return collector;
        }

        private void drain(InputStream stream) {
            byte[] chunk = new byte[4096];
            try (InputStream in = stream) {
                int read;
                while ((read = in.read(chunk)) != -1) {
                    if (buffer.size() < MAX_CAPTURE_BYTES) {
                        buffer.write(chunk, 0, read);
                    }
                }
            } catch (IOException ignored) {
                // stream closed by the timeout handler - expected
            }
        }

        String join() {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
