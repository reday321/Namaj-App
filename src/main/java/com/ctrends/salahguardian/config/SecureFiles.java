package com.ctrends.salahguardian.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Creates files and directories that only their owner can read.
 *
 * <h2>Why this exists</h2>
 * Everything this application stores - the user's coordinates, their movement
 * history in the logs - is personal data, and coordinates precise to a few
 * metres identify a home. Left to the ambient umask those files are created
 * {@code 0644}, or {@code 0664} where the umask is {@code 002}, which is the
 * default on Debian derivatives with per-user groups. On a shared machine, or
 * one where {@code /home/user} is not itself restrictive, that is readable by
 * anyone with an account.
 *
 * <h2>Why permissions are set at creation</h2>
 * Creating a file and then calling {@code chmod} leaves a window in which the
 * contents exist at the wider permissions. Every method here passes the mode as
 * a creation attribute so the file is never briefly world-readable.
 *
 * <h2>Non-POSIX filesystems</h2>
 * A FAT or NTFS volume, or an exotic network mount, cannot express POSIX modes
 * and throws {@link UnsupportedOperationException}. That is not a reason to
 * refuse to start: the operation falls back to a plain create and the failure is
 * logged once, so the user keeps a working application and an honest record.
 *
 * @author CTrends Software
 */
public final class SecureFiles {

    private static final Logger LOG = LoggerFactory.getLogger(SecureFiles.class);

    /** {@code rw-------} - readable and writable only by the owner. */
    public static final Set<PosixFilePermission> OWNER_ONLY_FILE =
            PosixFilePermissions.fromString("rw-------");

    /** {@code rwx------} - only the owner may enter the directory. */
    public static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY =
            PosixFilePermissions.fromString("rwx------");

    private SecureFiles() {
        // utility class
    }

    /**
     * Creates a directory that only its owner can traverse, including any
     * missing parents.
     *
     * <p>An existing directory has its mode tightened, because a user upgrading
     * from an earlier version already has one at {@code 0775}.</p>
     *
     * @param directory the directory to create or tighten
     * @throws IOException when the directory cannot be created at all
     */
    public static void createPrivateDirectory(Path directory) throws IOException {
        if (directory == null) {
            return;
        }
        if (Files.isDirectory(directory)) {
            harden(directory, OWNER_ONLY_DIRECTORY);
            return;
        }
        try {
            Files.createDirectories(directory, asAttribute(OWNER_ONLY_DIRECTORY));
        } catch (UnsupportedOperationException nonPosix) {
            LOG.debug("Filesystem at {} does not support POSIX permissions", directory);
            Files.createDirectories(directory);
        }
    }

    /**
     * Opens a writer onto a file that only its owner can read.
     *
     * <p>Any existing file is removed first: reusing it would keep whatever
     * mode it already had, which defeats the point when tightening an
     * installation that predates this class.</p>
     *
     * @param file the file to create
     * @return a UTF-8 writer onto the newly created file
     * @throws IOException when the file cannot be created
     */
    public static Writer newPrivateWriter(Path file) throws IOException {
        Files.deleteIfExists(file);
        try {
            Files.createFile(file, asAttribute(OWNER_ONLY_FILE));
        } catch (UnsupportedOperationException nonPosix) {
            LOG.debug("Filesystem at {} does not support POSIX permissions", file);
            Files.createFile(file);
        }
        return Files.newBufferedWriter(file, StandardCharsets.UTF_8);
    }

    /**
     * Tightens the mode of an existing path, ignoring a filesystem that cannot
     * express it.
     *
     * @param path        the file or directory to tighten
     * @param permissions the mode to apply
     * @return {@code true} when the mode was applied
     */
    public static boolean harden(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            LOG.debug("Could not tighten permissions on {}", path, e);
            return false;
        }
    }

    /**
     * Tightens a directory and every regular file directly inside it.
     *
     * <p>Used for the log directory. Logback creates its own files and offers no
     * hook to set their mode, so they arrive at whatever the umask allows; this
     * sweeps them at start-up. The directory itself being {@code 0700} is the
     * load-bearing part - it denies other users the traversal they would need
     * to reach a file whose own mode is looser.</p>
     *
     * @param directory the directory to sweep
     * @return the number of files whose mode was tightened
     */
    public static int hardenDirectoryContents(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return 0;
        }
        harden(directory, OWNER_ONLY_DIRECTORY);
        int tightened = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (Files.isRegularFile(entry) && harden(entry, OWNER_ONLY_FILE)) {
                    tightened++;
                }
            }
        } catch (IOException e) {
            LOG.debug("Could not sweep {} for permissions", directory, e);
        }
        return tightened;
    }

    private static FileAttribute<Set<PosixFilePermission>> asAttribute(
            Set<PosixFilePermission> permissions) {
        return PosixFilePermissions.asFileAttribute(permissions);
    }
}
