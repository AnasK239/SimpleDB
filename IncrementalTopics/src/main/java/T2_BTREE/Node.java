package T2_BTREE;

import java.io.IOException;

public class Node {

    NodeType type;
    private byte[][] keys;

    private byte[][] values;     //leaf values
    Node[] children;            // internal nodes






    public byte[] Encode(Node node) {
        int x  = NodeType.NODE.getVal();
        return null;
    }

    public Node Decode(Node node) throws IOException {
        return null;
    }

}
