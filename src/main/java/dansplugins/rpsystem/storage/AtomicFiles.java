package dansplugins.rpsystem.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Small same-directory atomic-write helper used by every durable character file. */
public final class AtomicFiles {

    /** Signals that the installed file no longer matches the caller's migration snapshot. */
    public static final class FileContentChangedException extends IOException {
        private FileContentChangedException() {
            super("target changed while replacement was prepared");
        }
    }

    private AtomicFiles() {
    }

    public static void writeUtf8(Path target, String content) throws IOException {
        writeUtf8(target, content, false, null);
    }

    /**
     * Writes through a same-directory atomic move and fails without replacing the target when the
     * filesystem cannot provide that guarantee.
     */
    public static void writeUtf8AtomicRequired(Path target, String content) throws IOException {
        writeUtf8(target, content, true, null);
    }

    /**
     * Performs a best-effort conflict check immediately before the atomic replacement.
     *
     * <p>Portable Java filesystems do not expose a compare-and-replace primitive, so an external
     * editor could still write in the narrow interval between the comparison and move. This check
     * protects edits completed while the replacement is being prepared; it is not a locking
     * protocol.</p>
     */
    public static void writeUtf8AtomicRequired(Path target, String content,
                                                byte[] expectedCurrent) throws IOException {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        writeUtf8(target, content, true, expectedCurrent.clone());
    }

    private static void writeUtf8(Path target, String content, boolean atomicRequired,
                                  byte[] expectedCurrent) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Path parent = target.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("target has no parent directory: " + target);
        }
        Files.createDirectories(parent);

        Path temporary = parent.resolve("." + target.getFileName() + "."
                + UUID.randomUUID() + ".tmp");
        boolean moved = false;
        try {
            byte[] encoded = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            if (expectedCurrent != null
                    && !Arrays.equals(expectedCurrent, Files.readAllBytes(target))) {
                throw new FileContentChangedException();
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                if (atomicRequired) {
                    throw unsupported;
                }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
