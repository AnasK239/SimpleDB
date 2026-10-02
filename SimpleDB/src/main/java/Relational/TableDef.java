package Relational;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TableDef {

    public TableDef() {

    }

    public String name;
    public int[] types;
    public String []cols;

    public String[][] indexes;

    public long[] prefixes;


    public TableDef(String name, int[] types, String[] cols, String[][] indexes) {
        this.name = name;
        this.types = types;
        this.cols = cols;
        this.indexes = indexes;
        this.prefixes = new long[indexes.length];
    }

    public TableDef(
            String name,
            int[] types,
            String[] cols,
            int primaryKeyCount,
            long prefix
    ) {
        this(name, types, cols, new String[][]{
                                Arrays.copyOf(cols, primaryKeyCount)}
        );

        prefixes[0] = prefix;
    }

    public int primaryKeyCount() {
        return indexes[0].length;
    }

    public int columnIndex(String colName){

        for (int i = 0; i < cols.length; i++) {
            if (cols[i].equals(colName)) return i;
        }

        throw new IllegalArgumentException("Unknown column: " + name);
    }

    public int[] indexTypes(int index){
        String[] cols = indexes[index];
        int[] result = new int[cols.length];

        for (int i = 0; i < cols.length; i++) {
            result[i] = types[columnIndex(cols[i])];
        }

        return result;
    }

    public void completeIndexes(){

        for (int i = 1; i < indexes.length; i++) {
            List<String> cols = new ArrayList<>(
                    Arrays.asList(indexes[i])
            );

            for(String primaryCol : indexes[0]){
                if(!cols.contains(primaryCol)){
                    cols.add(primaryCol);
                }
            }

            indexes[i] = cols.toArray(String[]::new);
        }
    }

}
