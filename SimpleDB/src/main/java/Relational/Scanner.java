package Relational;

import BTree.BIter;
import KV.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class Scanner {

    public final Comparison cmp1;
    public final Comparison cmp2;

    public final DBRecord key1;
    public final DBRecord key2;

    private DBTX tx;
    private TableDef table;
    private int index;

    private BIter iterator;
    private byte[] endKey;
    private int[] indexTypes;

    public Scanner(
            Comparison cmp1,
            DBRecord key1,
            Comparison cmp2,
            DBRecord key2
    ) {
        this.cmp1 = cmp1;
        this.key1 = key1;
        this.cmp2 = cmp2;
        this.key2 = key2;
    }

    void init(DBTX tx, TableDef table, int index, BIter iterator, byte[] endKey) {
        this.tx = tx;
        this.table = table;
        this.index = index;
        this.iterator = iterator;
        this.endKey = endKey;

        this.indexTypes = table.indexTypes(index);
    }

    public boolean valid() {
        if (tx == null
                || !tx.isActive()
                || iterator == null
                || !iterator.valid()
        ) {
            return false;
        }

        byte[] key = iterator.deref().key();

        // Remain inside the selected index's key space.
        if (key.length < Integer.BYTES || ByteBuffer.wrap(key).getInt() != (int) table.prefixes[index]) {
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
        DBRecord row;

        if (index == 0) {
            // The primary entry already contains the whole row.
            row = DBTX.decodeRow(table, pair.key(), pair.value());
        } else {
            // A secondary entry contains indexed columns
            // and all primary-key columns inside its key.
            byte[] encodedColumns = Arrays.copyOfRange(
                    pair.key(),
                    Integer.BYTES,
                    pair.key().length
            );

            Value[] values = RowCodec.decodeValues(
                    encodedColumns,
                    indexTypes
            );

            String[] columns = table.indexes[index];

            DBRecord indexed = new DBRecord();
            indexed.cols.addAll(Arrays.asList(columns));
            indexed.vals.addAll(Arrays.asList(values));

            // Build a primary-key-only lookup.
            row = new DBRecord();

            for (String primaryColumn : table.indexes[0]) {
                row.cols.add(primaryColumn);
                row.vals.add(indexed.get(primaryColumn));
            }

            // dbGet replaces the lookup with the complete row.
            if (!tx.dbGet(table, row)) {
                throw new IOException("Secondary index points to a missing row");
            }
        }

        record.cols.clear();
        record.cols.addAll(row.cols);

        record.vals.clear();
        record.vals.addAll(row.vals);
    }
}