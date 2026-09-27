package Relational;

import BTree.BIter;
import KV.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class Scanner {

    // Inputs: the requested interval
    public final Comparison cmp1;
    public final Comparison cmp2;

    public final DBRecord key1;
    public final DBRecord key2;

    // Set by DB.scan()
    private TableDef table;
    private BIter iterator;
    private byte[] endKey;

    private int[] primaryKeyTypes;
    private int[] remainingTypes;

    public Scanner(Comparison cmp1, DBRecord key1, Comparison cmp2, DBRecord key2) {
        this.cmp1 = cmp1;
        this.key1 = key1;
        this.cmp2 = cmp2;
        this.key2 = key2;
    }

    void init(TableDef table, BIter iterator, byte[] endKey) {
        this.table = table;
        this.iterator = iterator;
        this.endKey = endKey;

        primaryKeyTypes = Arrays.copyOf(
                table.types,
                table.PKeys
        );

        remainingTypes = Arrays.copyOfRange(
                table.types,
                table.PKeys,
                table.types.length
        );
    }

    public boolean valid() {
        if (iterator == null || !iterator.valid()) {
            return false;
        }

        byte[] key = iterator.deref().key();

        if (key.length < Integer.BYTES
                || ByteBuffer.wrap(key).getInt() != (int) table.prefix) {
            return false;
        }

        int comparison = Arrays.compareUnsigned(key, endKey);

        return switch (cmp2) {
            case LT -> comparison < 0;
            case LE -> comparison <= 0;
            case GT -> comparison > 0;
            case GE -> comparison >= 0;
        };
    }

    public void next() {
        if (!valid()) {
            return;
        }

        if (cmp1 == Comparison.GE || cmp1 == Comparison.GT) {
            iterator.next();
        } else {
            iterator.prev();
        }
    }

    public void deref(DBRecord record) throws IOException {
        if (!valid()) {
            throw new IllegalStateException("Scanner is not positioned on a row");
        }

        KVPair pair = iterator.deref();

        byte[] encodedPrimaryKey = Arrays.copyOfRange(
                pair.key(),
                Integer.BYTES,
                pair.key().length
        );

        Value[] primaryKey = RowCodec.decodeValues(
                encodedPrimaryKey,
                primaryKeyTypes
        );

        Value[] remaining = RowCodec.decodeValues(
                pair.value(),
                remainingTypes
        );

        record.cols.clear();
        record.cols.addAll(Arrays.asList(table.cols));

        record.vals.clear();
        record.vals.addAll(Arrays.asList(primaryKey));
        record.vals.addAll(Arrays.asList(remaining));
    }
}