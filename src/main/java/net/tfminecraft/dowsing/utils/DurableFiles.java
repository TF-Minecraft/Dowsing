package net.tfminecraft.dowsing.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Durable replacement for node state and the payment intent that precedes it. */
final class DurableFiles {
    private DurableFiles() { }

    static void write(Path destination, String text) throws IOException {
        Path directory = destination.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".node-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer data = StandardCharsets.UTF_8.encode(text);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            }
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            // Include new ancestor entries, even after an earlier failed publication attempt.
            for (Path parent = directory; parent != null; parent = parent.getParent()) {
                try (FileChannel channel = FileChannel.open(parent, StandardOpenOption.READ)) {
                    channel.force(true);
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
