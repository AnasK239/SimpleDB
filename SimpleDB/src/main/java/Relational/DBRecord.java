package Relational;

import java.util.ArrayList;
import java.util.List;

public class DBRecord {

    public final List<String> cols = new ArrayList<>();
    public final List<Value> vals = new ArrayList<>();

    public DBRecord addStr(String colName ,byte[] value){

        Value cell = Value.ofBytes(value);

        cols.add(colName);
        vals.add(cell);

        return this;
    }

    public DBRecord addInt64(String colName ,long value) {

        Value cell = Value.ofInt64(value);

        cols.add(colName);
        vals.add(cell);

        return this;
    }

    public Value get(String colName) {

        for (int i = 0; i < cols.size() ; i++) {
            if(cols.get(i).equals(colName)){
                return vals.get(i);
            }
        }
        return null;
    }
}
