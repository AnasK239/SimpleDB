package Relational;

import java.util.HashSet;
import java.util.Set;

public final class RecordChecks {
    private RecordChecks() {}

    public static void validateTable(TableDef table) {
        if (table.cols.length != table.types.length || table.indexes.length == 0) {
            throw new IllegalArgumentException("Invalid table definition");
        }

        Set<String> names = new HashSet<>();

        for (int i = 0; i < table.cols.length; i++) {
            String name = table.cols[i];

            if (name == null || name.isBlank() || !names.add(name)) {
                throw new IllegalArgumentException("Empty or duplicate column name: " + name);
            }

            int type = table.types[i];

            if (type != Value.TYPE_BYTES && type != Value.TYPE_INT64) {
                throw new IllegalArgumentException("Unsupported type for column: " + name);
            }
        }

        for (String[] index : table.indexes) {
            if (index.length == 0) {
                throw new IllegalArgumentException("An index needs columns");
            }

            Set<String> used = new HashSet<>();

            for (String column : index) {
                if (!names.contains(column) || !used.add(column)) {
                    throw new IllegalArgumentException("Unknown or repeated index column: " + column);
                }
            }
        }

        for (int i = 0; i < table.primaryKeyCount(); i++) {
            if (!table.cols[i].equals(table.indexes[0][i])) {
                throw new IllegalArgumentException("Primary-key columns must come first in schema order");
            }
        }
    }

    public static Value[] checkRecord(TableDef table, DBRecord record, int requiredColumns) {

        validateTable(table);

        if (requiredColumns != table.primaryKeyCount() && requiredColumns != table.cols.length) {
            throw new IllegalArgumentException(
                    "Expected primary-key columns or a complete row"
            );
        }

        if (record == null || record.cols.size() != record.vals.size() || record.cols.size() != requiredColumns) {
            throw new IllegalArgumentException("Incorrect number of record columns");
        }

        Value[] ordered = new Value[table.cols.length];

        for (int i = 0; i < record.cols.size(); i++) {
            String name = record.cols.get(i);
            int index = -1;

            for (int j = 0; j < table.cols.length; j++) {
                if (table.cols[j].equals(name)) {
                    index = j;
                    break;
                }
            }

            if (index < 0) {
                throw new IllegalArgumentException("Unknown column: " + name);
            }

            if (index >= requiredColumns) {
                throw new IllegalArgumentException("Only primary-key columns are expected here");
            }

            if (ordered[index] != null) {
                throw new IllegalArgumentException("Duplicate column: " + name);
            }

            Value value = record.vals.get(i);

            if (value == null || value.type != table.types[index]) {
                throw new IllegalArgumentException(
                        "Incorrect value type for column: " + name
                );
            }

            ordered[index] = value;
        }

        for (int i = 0; i < requiredColumns; i++) {
            if (ordered[i] == null) {
                throw new IllegalArgumentException("Missing column: " + table.cols[i]);
            }
        }

        return ordered;
    }

    public static Value[] checkIndexPrefix(TableDef table, int index, DBRecord record) {
        String[] columns = table.indexes[index];

        if (record.cols.size() != record.vals.size() || record.cols.size() > columns.length) {
            throw new IllegalArgumentException("Invalid scan bound");
        }

        Value[] values = new Value[record.cols.size()];

        for (int i = 0; i < values.length; i++) {
            String column = record.cols.get(i);

            if (!columns[i].equals(column)) {
                throw new IllegalArgumentException("Scan columns must match the beginning of the index");
            }

            Value value = record.vals.get(i);
            int expectedType = table.types[table.columnIndex(column)];

            if (value == null || value.type != expectedType) {
                throw new IllegalArgumentException("Incorrect value type for column: " + column);
            }

            values[i] = value;
        }

        return values;
    }
}