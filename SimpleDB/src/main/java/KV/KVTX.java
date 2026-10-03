package KV;

import BTree.BIter;
import BTree.BTREE;
import BTree.UpdateMode;
import BTree.UpdateReq;
import BTree.UpdateResult;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class KVTX implements AutoCloseable {

    private final KV db;

    // Starting metadata, used by KV.abort() and failed commits
    final KV.Meta before;

    KVTX(KV db, KV.Meta before) {
        this.db = db;
        this.before = before;
    }

    public boolean isActive() {
        return db.isActive(this);
    }

    public byte[] get(byte[] key) throws IOException {
        BTREE tree = db.transactionTree(this);

        try {
            return tree.get(key);
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }

    public BIter seek(byte[] key, Comparison comparison) {
        return db.transactionTree(this).seek(key, comparison);
    }

    public UpdateResult update(byte[] key, byte[] value, UpdateMode mode) throws Exception {

        BTREE tree = db.transactionTree(this);
        UpdateReq request = new UpdateReq(key, value, mode);

        try {
            tree.update(request);
        } catch (Exception error) {
            // A tree operation may have partially changed pending state
            db.abort(this);
            throw error;
        }

        return new UpdateResult(
                request.applied,
                request.added,
                request.oldValue
        );
    }

    public void set(byte[] key, byte[] value) throws Exception {
        update(key, value, UpdateMode.UPSERT);
    }

    public boolean delete(byte[] key) throws Exception {
        BTREE tree = db.transactionTree(this);

        try {
            return tree.delete(key);
        } catch (Exception error) {
            db.abort(this);
            throw error;
        }
    }

    public void commit() throws IOException {
        db.commit(this);
    }

    public void abort() {
        db.abort(this);
    }

    @Override
    public void close() {
        if (isActive()) {
            abort();
        }
    }
}