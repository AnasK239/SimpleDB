package T2_BTREE;

public class BTree {

    public static final int PAGE_SIZE = 4096;
    public static final int MAX_KEY_SIZE = 1000;
    public static final int MAX_VAL_SIZE = 3000;

    //maxNode size
    int nodeMax = 4 + 8 + 2 + 4 + MAX_KEY_SIZE + MAX_VAL_SIZE; // these are based on my defined serialization format


}