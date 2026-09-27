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

    private boolean dbGet(TableDef table, DBRecord record) throws IOException {

        //Validate primary-key columns and put them in schema order
        Value[] ordered = RecordChecks.checkRecord(
                table,
                record,
                table.PKeys
        );


        byte[] key = RowCodec.encodeKey(
                table.prefix,
                Arrays.copyOf(ordered, table.PKeys)
        );

        byte[] stored = kv.get(key);

        if (stored == null) {
            return false;
        }

        int[] remainingTypes = Arrays.copyOfRange(
                table.types,
                table.PKeys,
                table.types.length
        );

        Value[] remaining = RowCodec.decodeValues(stored, remainingTypes);

        System.arraycopy(
                remaining,
                0,
                ordered,
                table.PKeys,
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

        boolean forward =
                scanner.cmp1 == Comparison.GE
                        || scanner.cmp1 == Comparison.GT;

        boolean validEndComparison = forward
                ? scanner.cmp2 == Comparison.LE
                || scanner.cmp2 == Comparison.LT
                : scanner.cmp2 == Comparison.GE
                || scanner.cmp2 == Comparison.GT;

        if (!validEndComparison) {
            throw new IllegalArgumentException(
                    "Scan bounds must face opposite directions"
            );
        }

        Value[] startValues = RecordChecks.checkRecord(
                table,
                scanner.key1,
                table.PKeys
        );

        Value[] endValues = RecordChecks.checkRecord(
                table,
                scanner.key2,
                table.PKeys
        );

        byte[] startKey = RowCodec.encodeKey(
                table.prefix,
                Arrays.copyOf(startValues, table.PKeys)
        );

        byte[] endKey = RowCodec.encodeKey(
                table.prefix,
                Arrays.copyOf(endValues, table.PKeys)
        );

        BIter iterator = kv.seek(startKey, scanner.cmp1);

        scanner.init(table, iterator, endKey);
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
                table.prefix,
                Arrays.copyOf(ordered, table.PKeys)
        );

        byte[] value = RowCodec.encodeValues(
                Arrays.copyOfRange(
                        ordered,
                        table.PKeys,
                        ordered.length
                )
        );

        return new EncodedRow(key, value);
    }

    private UpdateResult dbUpdate(TableDef table, DBRecord record, UpdateMode mode) throws Exception {

        EncodedRow encoded = encodeRow(table, record);

        return kv.update(encoded.key(), encoded.value(), mode);
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
                table.PKeys
        );

        byte[] key = RowCodec.encodeKey(
                table.prefix,
                Arrays.copyOf(ordered, table.PKeys)
        );

        return kv.delete(key);
    }

    public void tableNew(TableDef definition) throws Exception {
        RecordChecks.validateTable(definition);
        checkUserTableName(definition.name);

        if (definition.prefix != 0) {
            throw new IllegalArgumentException(
                    "The database assigns the table prefix; supply 0"
            );
        }

        if (getTableDef(definition.name) != null) {
            throw new IllegalArgumentException(
                    "Table already exists: " + definition.name
            );
        }

        // Read the next unused prefix.
        DBRecord counterLookup = new DBRecord()
                .addStr("key", PREFIX_COUNTER_KEY);

        long nextPrefix = FIRST_USER_PREFIX;

        if (dbGet(InternalTables.TDEF_META, counterLookup)) {

            byte[] bytes = counterLookup.get("val").str;

            nextPrefix = ByteBuffer.wrap(bytes).getLong();
        }

        // Keep the caller's definition unchanged until success.
        TableDef assigned = new TableDef(
                definition.name,
                Arrays.copyOf(
                        definition.types,
                        definition.types.length
                ),
                Arrays.copyOf(
                        definition.cols,
                        definition.cols.length
                ),
                definition.PKeys,
                nextPrefix
        );

        byte[] schemaJson = JSON.toJson(assigned).getBytes(StandardCharsets.UTF_8);

        DBRecord schemaRecord = new DBRecord()
                .addStr(
                        "name",
                        assigned.name.getBytes(StandardCharsets.UTF_8)
                )
                .addStr("def", schemaJson);

        // Validate/encode the schema before changing the counter
        EncodedRow encodedSchema = encodeRow(
                InternalTables.TDEF_TABLE,
                schemaRecord
        );

        byte[] nextCounter = ByteBuffer
                .allocate(Long.BYTES)
                .putLong(nextPrefix + 1)
                .array();

        DBRecord counterRecord = new DBRecord()
                .addStr("key", PREFIX_COUNTER_KEY)
                .addStr("val", nextCounter);

        // First committed write: reserve the prefix
        dbUpdate(
                InternalTables.TDEF_META,
                counterRecord,
                UpdateMode.UPSERT
        );

        // Second committed write: store the table definition
        UpdateResult result = kv.update(
                encodedSchema.key(),
                encodedSchema.value(),
                UpdateMode.INSERT_ONLY
        );

        if (!result.applied()) {
            throw new IllegalStateException(
                    "Table definition was not inserted"
            );
        }

        definition.prefix = assigned.prefix;
    }

    private static void checkUserTableName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Table name is empty");
        }
    }
}