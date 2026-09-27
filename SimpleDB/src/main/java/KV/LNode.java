package KV;

import BTree.BTREE;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LNode {

    public static final int HEADER_SIZE = Long.BYTES;
    public static final int CAPACITY = (BTREE.PAGE_SIZE - HEADER_SIZE) / Long.BYTES;

    private final ByteBuffer buffer;

    public LNode(byte[] data) {
        if(data.length != BTREE.PAGE_SIZE) {
            throw new IllegalArgumentException("free list node must occupy 1 page of " + BTREE.PAGE_SIZE + " but was " + data.length);
        }

        buffer = ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN);
    }


    public long getNext(){
        return buffer.getLong(0);
    }

    public void setNext(long pageNumber){
        buffer.putLong(0, pageNumber);
    }


    public long getPtr(int index){
        checkIndex(index);

        return buffer.getLong(HEADER_SIZE + index * Long.BYTES);
    }

    public void setPtr(int index, long pageNumber){
        checkIndex(index);

        buffer.putLong(HEADER_SIZE + index * Long.BYTES, pageNumber);
    }

    private static void checkIndex(int index){
        if(index < 0 || index >= CAPACITY) {
            throw new IllegalArgumentException("free-list slot index out of bounds: " + index);
        }
    }
}
