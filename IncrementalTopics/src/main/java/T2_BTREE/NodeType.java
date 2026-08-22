package T2_BTREE;

public enum NodeType {
    NODE(1),
    LEAF(2);

    private final int val;

    NodeType(int i) {
        this.val = i;
    }

    public int getVal() {
        return val;
    }
}
