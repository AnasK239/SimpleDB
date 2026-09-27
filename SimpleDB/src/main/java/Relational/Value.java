package Relational;

import java.util.Arrays;

public class Value {
    public static final int TYPE_BYTES = 1;
    public static final int TYPE_INT64 = 2;


    public final int type;

    public final long i64;

    public final byte[] str;


    private Value(int type, long i64, byte[] str) {
        this.type = type;
        this.i64 = i64;
        this.str = str;
    }

    public static Value ofBytes(byte[] val) {

        byte[] copy = val == null ?
                new byte[0]
                : Arrays.copyOf(val, val.length);

        return new Value(TYPE_BYTES , 0, copy);
    }

    public static Value ofInt64(long val) {
        return new Value(TYPE_INT64 , val, null);
    }
}
