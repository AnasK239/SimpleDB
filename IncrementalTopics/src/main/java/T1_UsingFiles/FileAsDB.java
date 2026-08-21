package T1_UsingFiles;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
/*
    Trying out several file replacement mechanisms as ways to persist data
    These comments are my own thoughts while trying-out this stuff, they may contain errors
 */
public class FileAsDB {
    public static void main(String[] args) {

    }


    //Given the path to a file , save some data into it the data is given as a byte array
    //Approach 1: In-place modification of the same file
    //Even though this method force flushes the changes to disk, it still fails atomicity & durability
    //Because we still update the file in-place, Truncating it means we can end up with an empty file in a crash
    //Not truncating it means we can end up with partially written file due-to a crash
    public static void saveData(Path path , byte[] data) throws IOException {

        try(FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )){
            ByteBuffer buffer = ByteBuffer.wrap(data);

            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }

            channel.force(true);
        }
    }

    //Approach 2: Auxillary temp file then atomic rename
    //This approach is reader-writer atomic meaning they always have a consistent view
    //however in case of power-loss it's still not sufficient
    //this also has a slight problem because renaming effectively changes the directory meta-data
    public static void saveData2(Path givenPath , byte[] data) throws IOException {

        Path path = givenPath.toAbsolutePath();

        Path tmp = path.resolveSibling(
                path.getFileName().toString() + ".tmp" + UUID.randomUUID()
        );

        boolean moved = false;

        try{
            try(FileChannel channel = FileChannel.open(
                    tmp,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE_NEW
            )){
                ByteBuffer buffer = ByteBuffer.wrap(data);

                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }

                channel.force(true);
            }

            Files.move(
                    tmp,
                    path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

            moved = true;

            // At this point the file is durably saved on the disk, however due to renaming
            // the directory meta-data hasn't reflected / persisted the change yet
            // so we have to also force the directory to persist the meta-data
            // because in-case of a power loss here a reload would read old meta-data

            Path parent = path.getParent();
            try (FileChannel dirChannel = FileChannel.open(
                    parent,
                    StandardOpenOption.READ
            )) {
                dirChannel.force(true);
            }

        }
        finally {
            if(!moved){
                Files.deleteIfExists(tmp);
            }
        }


        //The above methods all deal with persisting data using file replacement
        //To accurately represent a DB a very simple approach is an append-only log
        //Every write to the log is durable by force-flushing to the disk
        //But still if a crash happens between append & flush it will be corrupted
        //So it's inevitable that the log will corrupt in case of a crash
        //This is solved by using checksums and discarding the corrupt entries after recovery
        //By appending a fixed-size header containing entry size & checksum to each entry
        //On recovery read last entry, if size || checksum are wrong then discard / ignore

        //Problem: append-only log is infinitely growing (no compaction)
    }
}
