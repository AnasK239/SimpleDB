package BTree;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class BNode {
    private byte[] data;
    private ByteBuffer buffer;

    public BNode(byte[] data) {
        this.data = data;
        this.buffer = ByteBuffer.wrap(data)
                                .order(ByteOrder.LITTLE_ENDIAN);
    }

    public byte[] getData() {
        return data;
    }
    public ByteBuffer getBuffer() {
        return buffer;
    }

    public int getType(){
        return Short.toUnsignedInt(buffer.getShort(0));
    }

    public int getNumberOfKeys(){
        return Short.toUnsignedInt(buffer.getShort(2));
    }

    public void setHeader(int type , int nKeys){
        if (type < 0 || type > 65535 ||
                nKeys < 0 || nKeys > 65535) {
            throw new IllegalArgumentException("Must fit in uint16");
        }

        buffer.putShort(0, (short)type);
        buffer.putShort(2, (short)nKeys);
    }

    public long getPtr(int idx){
        if (idx < 0 || idx >= getNumberOfKeys()) {
            throw new IndexOutOfBoundsException();
        }

        int pos = 4 + 8 * idx;
        return buffer.getLong(pos);
    }

    public void setPtr(int idx, long val) {
        if (idx < 0 || idx >= getNumberOfKeys()) {
            throw new IndexOutOfBoundsException();
        }

        int pos = 4 + 8 * idx;
        buffer.putLong(pos, val);
    }

    public int getOffset(int idx){
        if(idx == 0) return 0;

        int pos = 4 + 8 * getNumberOfKeys() + 2*(idx - 1);
        return Short.toUnsignedInt(buffer.getShort(pos));
    }

    public void setOffset(int idx, int offset) {
        if (idx <= 0 || idx > getNumberOfKeys()) {
            throw new IndexOutOfBoundsException();
        }

        if (offset < 0 || offset > 0xFFFF) {
            throw new IllegalArgumentException("Offset out of uint16 range");
        }

        int pos = 4 + 8 * getNumberOfKeys() + 2 * (idx - 1);

        buffer.putShort(pos, (short) offset);
    }

    public int getKvPos(int idx) {
        int nkeys = getNumberOfKeys();

        if (idx < 0 || idx > nkeys) {
            throw new IndexOutOfBoundsException();
        }

        return 4 + 8 * nkeys + 2 * nkeys + getOffset(idx);
    }

    public byte[] getKey(int idx) {
        if(idx < 0 || idx >= getNumberOfKeys()) {
            throw new IndexOutOfBoundsException();
        }

        int pos = getKvPos(idx);
        int keyLength = Short.toUnsignedInt(buffer.getShort(pos));

        return Arrays.copyOfRange(data, pos+4 , pos + 4 + keyLength);
    }

    public byte[] getValue(int idx) {
        if(idx < 0 || idx >= getNumberOfKeys()) {
            throw new IndexOutOfBoundsException();
        }

        int pos = getKvPos(idx);
        int keyLength = Short.toUnsignedInt(buffer.getShort(pos));
        int valueLength = Short.toUnsignedInt(buffer.getShort(pos+2));

        return Arrays.copyOfRange(
                data,
                pos + 4 + keyLength,
                pos + 4 + keyLength + valueLength
        );
    }
}
