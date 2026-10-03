package Relational;

import KV.KV;

import java.io.IOException;
import java.nio.file.Path;

public final class DB implements AutoCloseable {

    private final KV kv;

    public DB(Path path) {
        kv = new KV(path);
    }

    public void open() throws IOException {
        kv.open();
    }

    public DBTX begin() throws IOException {
        return new DBTX(kv.begin());
    }

    @Override
    public void close() throws IOException {
        kv.close();
    }
}