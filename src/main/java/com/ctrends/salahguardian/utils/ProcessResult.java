package com.ctrends.salahguardian.utils;

/**
 * Outcome of an external command executed by {@link ProcessRunner}.
 *
 * @param exitCode process exit status, or {@code -1} when the process never
 *                 started or had to be destroyed after a timeout
 * @param stdout   captured standard output, never {@code null}
 * @param stderr   captured standard error, never {@code null}
 * @param timedOut {@code true} when the command exceeded its deadline
 * @author CTrends Software
 */
public record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {

    /**
     * @return {@code true} when the command finished normally with status 0
     */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    /**
     * @return trimmed standard output, convenient for single line results
     */
    public String trimmedOutput() {
        return stdout.trim();
    }
}
