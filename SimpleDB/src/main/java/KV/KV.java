package KV;

import BTree.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class KV implements AutoCloseable {

    private final Path path;

    private FileChannel file;

    private BTREE tree;

    private FreeList free;

    private static final byte[] SIGNATURE = Arrays.copyOf(
            "SimpleDB07".getBytes(StandardCharsets.UTF_8),
            16
    );

    private static final int META_SIZE = 64;

    private boolean failed;

    private long pageFlushed = 2;

    private long nappend;

    private final Map<Long , byte[]> updates = new HashMap<>();

    public KV(Path path) {
        this.path = path;
    }

    public void set(byte[] key, byte[] value) throws Exception {
        mutate(() -> {
            tree.insert(key, value);
            return true;
        });
    }

    public boolean delete(byte[] key) throws Exception {
        return mutate(() -> tree.delete(key));
    }

    public byte[] get(byte[] key) throws IOException {
        ensureOpen();

        try {
            return tree.get(key);
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    public void open() throws IOException {
        if (file != null && file.isOpen()) {
            throw new IllegalStateException("Database is already open");
        }

        file = FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE
        );

        try {
            pageFlushed = 2;
            clearPending();
            failed = false;

            free = new FreeList(
                    this::pageRead,
                    this::pageAppend,
                    this::pageWrite
            );

            tree = new BTREE(
                    this::pageRead,
                    this::pageAlloc,
                    free::pushTail
            );

            long fileSize = file.size();

            if (fileSize == 0) {

                free.restore(
                        new FreeList.State(1,0,1,0)
                );

                writeFully(
                        ByteBuffer.wrap(new byte[BTREE.PAGE_SIZE]),
                        BTREE.PAGE_SIZE
                );

                file.force(true);

                // Initialize a complete metadata page for an empty DB.
                byte[] metaPage = new byte[BTREE.PAGE_SIZE];
                byte[] meta = encodeMeta(currentMeta());

                System.arraycopy(meta, 0, metaPage, 0, meta.length);

                writeFully(ByteBuffer.wrap(metaPage), 0);
                file.force(true);

            } else {
                restoreMeta(readMeta(fileSize));
            }

        } catch (IOException | RuntimeException error) {
            try {
                file.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }

            file = null;
            tree = null;
            free = null;
            clearPending();
            failed = false;

            throw error;
        }
    }


    private byte[] pageRead(long pageNumber){
        byte[] pending = updates.get(pageNumber);

        if(pending != null){
            return pending;
        }

        return pageReadFile(pageNumber);
    }

    private byte[] pageReadFile(long pageNumber) {

        if (pageNumber <= 0 || pageNumber >= pageFlushed) {
            throw new IllegalArgumentException("Invalid or unflushed page number: " + pageNumber);
        }

        byte[] page = new byte[BTREE.PAGE_SIZE];

        ByteBuffer buffer = ByteBuffer.wrap(page);

        long offset = pageNumber * (long) BTREE.PAGE_SIZE;


        try {
            readFully(buffer , offset);
            return page;

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read page " + pageNumber, e);
        }

    }

    private long pageAppend(byte[] data) {

        if (data.length != BTREE.PAGE_SIZE) {
            throw new IllegalArgumentException("Node exceeds page size");
        }

        long pageNumber = Math.addExact(pageFlushed, nappend);
        long nextCount = Math.incrementExact(nappend);

        updates.put(pageNumber, Arrays.copyOf(data , BTREE.PAGE_SIZE));

        nappend = nextCount;

        return pageNumber;
    }

    private long pageAlloc(byte[] data){
        BNode node = new BNode(data);

        if(node.getSizeBytes() > BTREE.PAGE_SIZE){
            throw new IllegalArgumentException("Node exceeds page size");
        }

        byte[] page = Arrays.copyOf(data, BTREE.PAGE_SIZE);

        long pageNumber = free.popHead();

        if(pageNumber == 0){
            return pageAppend(page);
        }

        updates.put(pageNumber, page);
        return pageNumber;
    }

    private byte[] pageWrite(long pageNumber){
        byte[] pending = updates.get(pageNumber);

        if(pending != null){
            return pending;
        }

        byte[] writable = pageReadFile(pageNumber);

        updates.put(pageNumber , writable);
        return writable;
    }

    private void writePages() throws IOException {

        long nextFlushed = Math.addExact(pageFlushed, nappend);

        for(var entry : updates.entrySet()) {
            long pageNumber = entry.getKey();

            long offset = Math.multiplyExact(pageNumber , (long) BTREE.PAGE_SIZE);

            writeFully(
                    ByteBuffer.wrap(entry.getValue()),
                    offset
            );

        }

        pageFlushed = nextFlushed;
        clearPending();
    }

    private void clearPending() {
        updates.clear();
        nappend = 0;
    }


    public BTREE tree() {

        if (tree == null) {
            throw new IllegalStateException("Database is not open");
        }

        return tree;
    }

    private Meta currentMeta() {
        return new Meta(tree.getRoot(), pageFlushed , free.getSnapshot());
    }

    private byte[] encodeMeta(Meta meta) {
        ByteBuffer buffer = ByteBuffer
                .allocate(META_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        FreeList.State state = meta.freeState();

        buffer.put(SIGNATURE);
        buffer.putLong(meta.root());
        buffer.putLong(meta.pages());

        buffer.putLong(state.headPage());
        buffer.putLong(state.headSeq());
        buffer.putLong(state.tailPage());
        buffer.putLong(state.tailSeq());

        return buffer.array();
    }

    private void restoreMeta(Meta meta) {
        tree.setRoot(meta.root());
        pageFlushed = meta.pages();

        free.restore(meta.freeState());
    }

    private void readFully(ByteBuffer buffer , long offset) throws IOException {

        while (buffer.hasRemaining()) {
            int count = file.read(
                    buffer,
                    offset + buffer.position()
            );

            if (count < 0) {
                throw new IOException("Unexpected end of database file");
            }

            if (count == 0) {
                throw new IOException("No progress reading database file");
            }
        }
    }

    private void writeFully(ByteBuffer buffer, long offset) throws IOException {

        while (buffer.hasRemaining()) {
            int count = file.write(
                    buffer,
                    offset + buffer.position()
            );

            if (count <= 0) {
                throw new IOException("No progress writing database file");
            }
        }
    }

    private Meta readMeta(long fileSize) throws IOException {
        if (fileSize < 2L * BTREE.PAGE_SIZE) {
            throw new IOException("Database metadata page is incomplete");
        }

        ByteBuffer buffer = ByteBuffer
                .allocate(META_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);

        readFully(buffer, 0);

        buffer.flip();

        byte[] signature = new byte[SIGNATURE.length];
        buffer.get(signature);

        if (!Arrays.equals(signature, SIGNATURE)) {
            throw new IOException("Unrecognized database format");
        }

        long root = buffer.getLong();
        long pages = buffer.getLong();

        long headPage = buffer.getLong();
        long headSeq = buffer.getLong();
        long tailPage = buffer.getLong();
        long tailSeq = buffer.getLong();

        if (pages < 2 || pages > fileSize / BTREE.PAGE_SIZE) {
            throw new IOException("Invalid committed page count");
        }

        if (root < 0 || root >= pages) {
            throw new IOException("Invalid root page");
        }

        if (headPage <= 0 || headPage >= pages
                || tailPage <= 0 || tailPage >= pages) {
            throw new IOException("Invalid free-list page");
        }

        if (root != 0 && (root == headPage || root == tailPage)) {
            throw new IOException(
                    "Tree root overlaps a free-list page"
            );
        }

        if (headSeq < 0 || tailSeq < headSeq) {
            throw new IOException("Invalid free-list sequence numbers");
        }

        boolean sameSequencePage =
                headSeq / LNode.CAPACITY == tailSeq / LNode.CAPACITY;

        if ((headPage == tailPage) != sameSequencePage) {
            throw new IOException(
                    "Free-list page pointers disagree with sequence numbers"
            );
        }

        return new Meta(root, pages,
                new FreeList.State(headPage, headSeq, tailPage, tailSeq)
        );
    }

    private void writeMeta(Meta meta) throws IOException {
        writeFully(ByteBuffer.wrap(encodeMeta(meta)), 0);
    }

    private void updateFile() throws IOException {
        writePages();

        // Persist the new nodes before publishing their root
        file.force(true);

        writeMeta(currentMeta());

        // Persist the new committed root and page count
        file.force(true);

        free.setMaxSeq();
    }

    private void ensureOpen() {
        if (file == null || !file.isOpen() || tree == null) {
            throw new IllegalStateException("Database is not open");
        }
    }

    private boolean mutate(Mutation mutation) throws Exception {
        ensureOpen();

        Meta before = currentMeta();

        // A previous write failure may have left uncertain metadata
        // Restore the old metadata before writing any replacement pages
        if (failed) {
            writeMeta(before);
            file.force(true);
            failed = false;
        }

        boolean writingFile = false;

        try {
            boolean changed = mutation.apply();

            if (!changed) {
                return false;
            }

            writingFile = true;
            updateFile();

            return true;
        } catch (Exception error) {
            clearPending();
            restoreMeta(before);

            if (writingFile) {
                failed = true;
            }

            throw error;
        }
    }

    public UpdateResult update(byte[] key, byte[] value, UpdateMode mode) throws Exception {

        UpdateReq request = new UpdateReq(key, value, mode);

        mutate(() -> {
            tree.update(request);
            return request.applied;
        });

        return new UpdateResult(
                request.applied,
                request.added,
                request.oldValue
        );
    }

    public BIter seek(byte[] key, Comparison comparison) {
        ensureOpen();

        return tree.seek(key, comparison);
    }

//    long getPageFlushed() {
//        return pageFlushed;
//    }
//
//
//    int getTempPageCount() {
//        return updates.size();
//    }


    @FunctionalInterface
    private interface Mutation {
        boolean apply() throws Exception;
    }

    @Override
    public void close() throws IOException {
        try {
            if (file != null) {
                file.close();
            }
        } finally {
            file = null;
            tree = null;
            free = null;

            clearPending();
            failed = false;
        }
    }


    private record Meta(
            long root,
            long pages,
            FreeList.State freeState
    ) {}
}