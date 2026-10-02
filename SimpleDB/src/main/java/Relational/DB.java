package Relational;

import BTree.*;
import KV.*;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

public final class DB implements AutoCloseable {
    private static final Gson JSON = new Gson();

    private static final long FIRST_USER_PREFIX = 3;
    private static final long MAX_PREFIX = 0xFFFF_FFFFL;

    private static final byte[] PREFIX_COUNTER_KEY = "next_prefix".getBytes(StandardCharsets.UTF_8);

    private final KV kv;

    public DB(Path path) {
        kv = new KV(path);
    }

    public void open() throws IOException {
        kv.open();
    }

    @Override
    public void close() throws IOException {
        kv.close();
    }

    public boolean get(String tableName, DBRecord record) throws IOException {

        TableDef table = requireTable(tableName);
        return dbGet(table, record);
    }

    boolean dbGet(TableDef table, DBRecord record) throws IOException {

        //Validate primary-key columns and put them in schema order
        Value[] ordered = RecordChecks.checkRecord(
                table,
                record,
                table.primaryKeyCount()
        );


        byte[] key = RowCodec.encodeKey(
                table.prefixes[0],
                Arrays.copyOf(ordered, table.primaryKeyCount())
        );

        byte[] stored = kv.get(key);

        if (stored == null) {
            return false;
        }

        int[] remainingTypes = Arrays.copyOfRange(
                table.types,
                table.primaryKeyCount(),
                table.types.length
        );

        Value[] remaining = RowCodec.decodeValues(stored, remainingTypes);

        System.arraycopy(
                remaining,
                0,
                ordered,
                table.primaryKeyCount(),
                remaining.length
        );

        record.cols.clear();
        record.cols.addAll(Arrays.asList(table.cols));

        record.vals.clear();
        record.vals.addAll(Arrays.asList(ordered));

        return true;
    }

    private TableDef getTableDef(String name) throws IOException {
        checkUserTableName(name);

        DBRecord lookup = new DBRecord().addStr(
                        "name",
                        name.getBytes(StandardCharsets.UTF_8)
                );

        if (!dbGet(InternalTables.TDEF_TABLE, lookup)) {
            return null;
        }

        Value definition = lookup.get("def");
        TableDef table;

        try {
            table = JSON.fromJson(
                    new String(
                            definition.str,
                            StandardCharsets.UTF_8
                    ),
                    TableDef.class
            );

            RecordChecks.validateTable(table);
        } catch (JsonParseException | IllegalArgumentException error) {
            throw new IOException("Invalid stored table schema", error);
        }


        return table;
    }

    public void scan(String tableName, Scanner scanner) throws IOException {

        TableDef table = requireTable(tableName);

        boolean forward = scanner.cmp1 == Comparison.GE || scanner.cmp1 == Comparison.GT;

        boolean validEndComparison = forward
                ? scanner.cmp2 == Comparison.LE
                || scanner.cmp2 == Comparison.LT
                : scanner.cmp2 == Comparison.GE
                || scanner.cmp2 == Comparison.GT;

        if (!validEndComparison) {
            throw new IllegalArgumentException("Scan bounds must face opposite directions");
        }

        int selectedIndex = -1;

        for (int i = 0; i < table.indexes.length; i++) {
            if (matchesIndex(table.indexes[i], scanner.key1)
                    && matchesIndex(table.indexes[i], scanner.key2)) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex < 0) {
            throw new IllegalArgumentException("No index matches the scan columns");
        }

        Value[] startValues = RecordChecks.checkIndexPrefix(
                table,
                selectedIndex,
                scanner.key1
        );

        Value[] endValues = RecordChecks.checkIndexPrefix(
                table,
                selectedIndex,
                scanner.key2
        );

        long prefix = table.prefixes[selectedIndex];

        byte[] startKey = RowCodec.encodeKeyPartial(
                prefix,
                startValues,
                scanner.cmp1
        );

        byte[] endKey = RowCodec.encodeKeyPartial(
                prefix,
                endValues,
                scanner.cmp2
        );

        BIter iterator = kv.seek(startKey, scanner.cmp1);

        scanner.init(
                this,
                table,
                selectedIndex,
                iterator,
                endKey
        );
    }

    public UpdateResult insert(String tableName, DBRecord record) throws Exception {

        return dbUpdate(
                requireTable(tableName),
                record,
                UpdateMode.INSERT_ONLY
        );
    }

    public UpdateResult update(String tableName, DBRecord record) throws Exception {

        return dbUpdate(
                requireTable(tableName),
                record,
                UpdateMode.UPDATE_ONLY
        );
    }

    public UpdateResult upsert(String tableName, DBRecord record) throws Exception {

        return dbUpdate(
                requireTable(tableName),
                record,
                UpdateMode.UPSERT
        );
    }

    private record EncodedRow(byte[] key, byte[] value) {}

    private EncodedRow encodeRow(TableDef table, DBRecord record) throws IOException {

        Value[] ordered = RecordChecks.checkRecord(
                table,
                record,
                table.cols.length
        );

        byte[] key = RowCodec.encodeKey(
                table.prefixes[0],
                Arrays.copyOf(ordered, table.primaryKeyCount())
        );

        byte[] value = RowCodec.encodeValues(
                Arrays.copyOfRange(
                        ordered,
                        table.primaryKeyCount(),
                        ordered.length
                )
        );

        return new EncodedRow(key, value);
    }

    static DBRecord decodeRow(TableDef table, byte[] key, byte[] value) throws IOException {

        int primaryCount = table.primaryKeyCount();

        Value[] primary = RowCodec.decodeValues(
                Arrays.copyOfRange(key, Integer.BYTES, key.length),
                Arrays.copyOf(table.types, primaryCount)
        );

        Value[] remaining = RowCodec.decodeValues(
                value,
                Arrays.copyOfRange(
                        table.types,
                        primaryCount,
                        table.types.length
                )
        );

        DBRecord row = new DBRecord();

        row.cols.addAll(Arrays.asList(table.cols));
        row.vals.addAll(Arrays.asList(primary));
        row.vals.addAll(Arrays.asList(remaining));

        return row;
    }

    private UpdateResult dbUpdate(TableDef table, DBRecord record, UpdateMode mode) throws Exception {

        EncodedRow encoded = encodeRow(table, record);
        byte[][] newKeys = secondaryKeys(table, record);

        UpdateResult result = kv.update(
                encoded.key(),
                encoded.value(),
                mode
        );

        if (!result.applied()) {
            return result;
        }

        if (!result.added()) {
            DBRecord oldRow = decodeRow(
                    table,
                    encoded.key(),
                    result.oldValue()
            );

            deleteSecondaryKeys(secondaryKeys(table, oldRow));
        }

        addSecondaryKeys(newKeys);

        return result;
    }

    private TableDef requireTable(String name) throws IOException {
        TableDef table = getTableDef(name);

        if (table == null) {
            throw new IllegalArgumentException("Table does not exist: " + name);
        }

        return table;
    }

    public boolean delete(String tableName, DBRecord record) throws Exception {

        TableDef table = requireTable(tableName);

        Value[] ordered = RecordChecks.checkRecord(
                table,
                record,
                table.primaryKeyCount()
        );

        byte[] key = RowCodec.encodeKey(
                table.prefixes[0],
                Arrays.copyOf(ordered, table.primaryKeyCount())
        );

        byte[] oldValue = kv.get(key);

        if (oldValue == null) {
            return false;
        }

        DBRecord oldRow = decodeRow(table, key, oldValue);
        byte[][] oldKeys = secondaryKeys(table, oldRow);

        if (!kv.delete(key)) {
            return false;
        }

        deleteSecondaryKeys(oldKeys);

        return true;
    }

    public void tableNew(TableDef definition) throws Exception {
        RecordChecks.validateTable(definition);
        checkUserTableName(definition.name);

        for (long prefix : definition.prefixes) {
            if (prefix != 0) {
                throw new IllegalArgumentException("The database assigns index prefixes");
            }
        }

        if (getTableDef(definition.name) != null) {
            throw new IllegalArgumentException(
                    "Table already exists: " + definition.name
            );
        }

        DBRecord counterLookup = new DBRecord()
                .addStr("key", PREFIX_COUNTER_KEY);

        long nextPrefix = FIRST_USER_PREFIX;

        if (dbGet(InternalTables.TDEF_META, counterLookup)) {
            nextPrefix = ByteBuffer.wrap(counterLookup.get("val").str)
                    .getLong();
        }

        int indexCount = definition.indexes.length;

        if (nextPrefix < FIRST_USER_PREFIX || nextPrefix > MAX_PREFIX - indexCount + 1) {
            throw new IllegalStateException("No index prefixes remaining");
        }

        // Work on a separate schema until its writes succeed.
        String[][] indexes = new String[indexCount][];

        for (int i = 0; i < indexCount; i++) {
            indexes[i] = definition.indexes[i].clone();
        }

        TableDef assigned = new TableDef(
                definition.name,
                definition.types.clone(),
                definition.cols.clone(),
                indexes
        );

        assigned.completeIndexes();

        for (int i = 0; i < indexCount; i++) {
            assigned.prefixes[i] = nextPrefix + i;
        }

        byte[] schemaJson = JSON.toJson(assigned)
                .getBytes(StandardCharsets.UTF_8);

        DBRecord schemaRecord = new DBRecord()
                .addStr(
                        "name",
                        assigned.name.getBytes(StandardCharsets.UTF_8)
                )
                .addStr("def", schemaJson);

        EncodedRow encodedSchema = encodeRow(
                InternalTables.TDEF_TABLE,
                schemaRecord
        );

        byte[] nextCounter = ByteBuffer
                .allocate(Long.BYTES)
                .putLong(nextPrefix + indexCount)
                .array();

        DBRecord counterRecord = new DBRecord()
                .addStr("key", PREFIX_COUNTER_KEY)
                .addStr("val", nextCounter);

        dbUpdate(
                InternalTables.TDEF_META,
                counterRecord,
                UpdateMode.UPSERT
        );

        UpdateResult result = kv.update(
                encodedSchema.key(),
                encodedSchema.value(),
                UpdateMode.INSERT_ONLY
        );

        if (!result.applied()) {
            throw new IllegalStateException("Table definition was not inserted");
        }

        definition.indexes = assigned.indexes;
        definition.prefixes = assigned.prefixes;
    }

    private static void checkUserTableName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Table name is empty");
        }
    }

    private byte[][] secondaryKeys(TableDef table, DBRecord row) throws IOException {

        byte[][] keys = new byte[table.indexes.length - 1][];

        for (int index = 1; index < table.indexes.length; index++) {
            String[] columns = table.indexes[index];
            Value[] values = new Value[columns.length];

            for (int i = 0; i < columns.length; i++) {
                values[i] = row.get(columns[i]);
            }

            keys[index - 1] = RowCodec.encodeKey(table.prefixes[index], values);
        }

        return keys;
    }

    private void addSecondaryKeys(byte[][] keys) throws Exception {
        for (byte[] key : keys) {
            kv.update(key, new byte[0], UpdateMode.UPSERT);
        }
    }

    private void deleteSecondaryKeys(byte[][] keys) throws Exception {
        for (byte[] key : keys) {
            kv.delete(key);
        }
    }

    private static boolean matchesIndex(String[] index, DBRecord bound) {
        if (bound.cols.size() > index.length) {
            return false;
        }

        for (int i = 0; i < bound.cols.size(); i++) {
            if (!index[i].equals(bound.cols.get(i))) {
                return false;
            }
        }

        return true;
    }
}