package dansplugins.rpsystem.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Small same-directory atomic-write helper used by every durable character file. */
public final class AtomicFiles {

    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private static final Set<AclEntryPermission> OWNER_ACL_PERMISSIONS = EnumSet.of(
            AclEntryPermission.READ_DATA,
            AclEntryPermission.WRITE_DATA,
            AclEntryPermission.APPEND_DATA,
            AclEntryPermission.READ_NAMED_ATTRS,
            AclEntryPermission.WRITE_NAMED_ATTRS,
            AclEntryPermission.READ_ATTRIBUTES,
            AclEntryPermission.WRITE_ATTRIBUTES,
            AclEntryPermission.DELETE,
            AclEntryPermission.READ_ACL,
            AclEntryPermission.WRITE_ACL,
            AclEntryPermission.SYNCHRONIZE);

    /** Signals that the installed file no longer matches the caller's migration snapshot. */
    public static final class FileContentChangedException extends IOException {
        private FileContentChangedException() {
            super("target changed while replacement was prepared");
        }
    }

    private AtomicFiles() {
    }

    public static void writeUtf8(Path target, String content) throws IOException {
        writeUtf8(target, content, false, null, false);
    }

    /**
     * Writes through a same-directory atomic move and fails without replacing the target when the
     * filesystem cannot provide that guarantee.
     */
    public static void writeUtf8AtomicRequired(Path target, String content) throws IOException {
        writeUtf8(target, content, true, null, false);
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
        writeUtf8(target, content, true, expectedCurrent.clone(), false);
    }

    /** Atomic compare-and-replace whose temporary/replacement file is owner-only before writing. */
    public static void writeUtf8AtomicRequiredOwnerOnly(Path target, String content,
                                                         byte[] expectedCurrent)
            throws IOException {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        writeUtf8(target, content, true, expectedCurrent.clone(), true);
    }

    private static void writeUtf8(Path target, String content, boolean atomicRequired,
                                  byte[] expectedCurrent, boolean ownerOnly) throws IOException {
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
            if (ownerOnly) {
                Files.createFile(temporary);
                hardenOwnerOnly(temporary);
                writeForced(temporary, encoded);
            } else {
                try (FileChannel channel = FileChannel.open(temporary,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    writeForced(channel, encoded);
                }
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

    private static void writeForced(Path target, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(target,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeForced(channel, bytes);
        }
    }

    private static void writeForced(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        channel.force(true);
    }

    /** Restricts a newly-created file before configuration or credential bytes are written. */
    public static void hardenOwnerOnly(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file,
                PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            Files.setPosixFilePermissions(file, OWNER_FILE_PERMISSIONS);
            return;
        }

        AclFileAttributeView acl = Files.getFileAttributeView(file,
                AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl != null) {
            UserPrincipal owner = Files.getOwner(file, LinkOption.NOFOLLOW_LINKS);
            AclEntry ownerOnly = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(OWNER_ACL_PERMISSIONS)
                    .build();
            acl.setAcl(List.of(ownerOnly));
            return;
        }
        throw new IOException("filesystem exposes no owner-only file permissions");
    }
}
