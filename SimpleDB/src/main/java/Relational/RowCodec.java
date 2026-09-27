package Relational;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class RowCodec {

    private RowCodec() {
    }

    public static byte[] encodeKey(long prefix, Value[] primaryKey) throws IOException {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {

            out.writeInt((int) prefix);
            writeValues(out, primaryKey);
        }

        return bytes.toByteArray();
    }

    public static byte[] encodeValues(Value[] values) throws IOException {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeValues(out, values);
        }

        return bytes.toByteArray();
    }

    private static void writeValues(DataOutputStream out, Value[] values) throws IOException {

        for (Value value : values) {
            switch (value.type) {

                case Value.TYPE_INT64 ->
                        out.writeLong(value.i64 ^ Long.MIN_VALUE);

                case Value.TYPE_BYTES ->
                        writeBytes(out, value.str);

                default ->
                        throw new IllegalArgumentException("Unsupported value type: " + value.type);
            }
        }
    }

    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {

        for (byte raw : bytes) {
            int value = Byte.toUnsignedInt(raw);

            switch (value) {
                case 0 -> {
                    out.writeByte(1);
                    out.writeByte(1);
                }

                case 1 -> {
                    out.writeByte(1);
                    out.writeByte(2);
                }

                default -> out.writeByte(value);
            }
        }

        out.writeByte(0);
    }

    public static Value[] decodeValues(byte[] data, int[] types) throws IOException {

        ByteBuffer buffer = ByteBuffer
                .wrap(data)
                .order(ByteOrder.BIG_ENDIAN);

        Value[] values = new Value[types.length];

        try {
            for (int i = 0; i < types.length; i++) {
                switch (types[i]) {

                    case Value.TYPE_INT64 ->
                            values[i] = Value.ofInt64(buffer.getLong() ^ Long.MIN_VALUE);

                    case Value.TYPE_BYTES ->
                            values[i] = Value.ofBytes(readBytes(buffer));

                    default ->
                            throw new IOException("Unsupported stored column type: " + types[i]);
                }
            }

        } catch (BufferUnderflowException e) {
            throw new IOException("Incomplete encoded row", e);
        }

        if (buffer.hasRemaining()) {
            throw new IOException("Unexpected bytes after encoded row");
        }

        return values;
    }

    private static byte[] readBytes(ByteBuffer buffer) throws IOException {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        while (true) {
            int value = Byte.toUnsignedInt(buffer.get());

            // End of this byte string.
            if (value == 0) {
                return bytes.toByteArray();
            }

            if (value == 1) {
                int escaped = Byte.toUnsignedInt(buffer.get());

                switch (escaped) {
                    case 1 -> bytes.write(0);
                    case 2 -> bytes.write(1);

                    default -> throw new IOException("Invalid byte-string escape: " + escaped);
                }
            } else {
                bytes.write(value);
            }
        }
    }
}