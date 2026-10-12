package dev.kondalabs.gaslight;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Probe the actual runner, never simulate a successful durability operation. */
final class JournalTestSupport {
    private JournalTestSupport() {}
    static boolean directorySyncAvailable(Path parent) {
        try {
            String storage=Files.getFileStore(parent).type().toLowerCase(java.util.Locale.ROOT);
            if(!java.util.Set.of("ext4","ntfs").contains(storage))return false;
            for(Path dir=parent.toAbsolutePath();dir!=null;dir=dir.getParent())
                try(FileChannel channel=FileChannel.open(dir,StandardOpenOption.READ)) {
                    channel.force(true);
                }
            return true;
        }catch(IOException|UnsupportedOperationException unavailable){return false;}
    }
}
