package BTree;


import java.util.Arrays;


public class BTREE {
    public static final int PAGE_SIZE = 4096;
    public static final int MAX_KEY_SIZE = 1000;
    public static final int MAX_VAL_SIZE = 3000;
    private static final int HEADER_SIZE = 4;



    private long root;
    private static final byte[] EMPTY_VALUE = new byte[0];

    private final PageGet pageGetter;
    private final PageAllocate pageAllocator;
    private final PageDelete pageDeleter;

    public BTREE(
            PageGet pageGetter,
            PageAllocate pageAllocator,
            PageDelete pageDeleter
    ) {
        this.pageGetter = pageGetter;
        this.pageAllocator = pageAllocator;
        this.pageDeleter = pageDeleter;
    }


    public byte[] get(byte[] key) {
        checkLimit(key, EMPTY_VALUE);

        long page = root;

        while (page != 0) {
            BNode node = getNode(page);
            int idx = node.lookupLE(key);

            if (idx < 0) {
                return null;
            }

            if (node.getType() == NodeType.LEAF.getVal()) {
                return Arrays.equals(node.getKey(idx), key)
                        ? node.getValue(idx)
                        : null;
            }

            if (node.getType() != NodeType.NODE.getVal()) {
                throw new IllegalStateException("Unknown node type");
            }

            page = node.getPtr(idx);
        }

        return null;
    }

    public void insert(byte[] key, byte[] value) throws Exception {

        checkLimit(key, value);

        if (root == 0) {

            BNode newRoot = new BNode(new byte[PAGE_SIZE]);

            newRoot.setHeader(NodeType.LEAF.getVal(), 2);

            newRoot.appendKV(0, 0, EMPTY_VALUE, EMPTY_VALUE);

            newRoot.appendKV(1, 0, key, value);

            root = allocateNode(newRoot);
            return;
        }

        long oldRootPage = root;

        BNode updatedRoot = treeInsert(
                getNode(oldRootPage),
                key,
                value
        );

        SplitResult splitResult = nodeSplit3(updatedRoot);

        deleteNode(oldRootPage);

        if (splitResult.count() > 1) {

            BNode newRoot = new BNode(new byte[PAGE_SIZE]);

            newRoot.setHeader(NodeType.NODE.getVal(), splitResult.count());

            for (int i = 0; i < splitResult.count(); i++) {

                BNode child = splitResult.nodes()[i];

                long childPage = allocateNode(child);

                byte[] childFirstKey = child.getKey(0);

                newRoot.appendKV(i, childPage, childFirstKey, EMPTY_VALUE);
            }

            root = allocateNode(newRoot);

        } else {
            root = allocateNode(splitResult.nodes()[0]);
        }
    }

    public boolean delete(byte[] key) throws Exception {

        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        if (key.length == 0) {
            throw new IllegalArgumentException("Key cannot be empty");
        }

        if (key.length > MAX_KEY_SIZE) {
            throw new IllegalArgumentException("Key is too large. Maximum size is "
                            + MAX_KEY_SIZE + " bytes");
        }

        if (root == 0) {
            return false;
        }

        long oldRootPage = root;

        BNode updatedRoot = treeDelete(
                getNode(oldRootPage),
                key
        );

        if (updatedRoot == null) {
            return false;
        }

        deleteNode(oldRootPage);

        if (updatedRoot.getType() == NodeType.NODE.getVal() && updatedRoot.getNumberOfKeys() == 1) {
            root = updatedRoot.getPtr(0);
        }

        else {
            root = allocateNode(updatedRoot);
        }

        return true;
    }

    private BNode nodeDelete(
            BNode node,
            int idx,
            byte[] key
    ) {
        long childPtr = node.getPtr(idx);

        BNode updated = treeDelete(getNode(childPtr), key);

        if (updated == null) {
            return null;
        }

        deleteNode(childPtr);

        BNode newNode = new BNode(new byte[PAGE_SIZE]);

        MergeDecision decision = shouldMerge(node, idx, updated);

        int mergeDir = decision.direction();
        BNode sibling = decision.sibling();

        if (mergeDir < 0) {

            BNode merged = new BNode(new byte[PAGE_SIZE]);

            nodeMerge(merged, sibling, updated);

            deleteNode(node.getPtr(idx - 1));

            long mergedPtr = allocateNode(merged);

            nodeReplace2Kid(
                    newNode,
                    node,
                    idx - 1,
                    mergedPtr,
                    merged.getKey(0)
            );
        }

        else if (mergeDir > 0) {

            BNode merged = new BNode(new byte[PAGE_SIZE]);

            nodeMerge(merged, updated, sibling);

            deleteNode(node.getPtr(idx + 1));

            long mergedPtr = allocateNode(merged);

            nodeReplace2Kid(
                    newNode,
                    node,
                    idx,
                    mergedPtr,
                    merged.getKey(0)
            );
        }

        else if (updated.getNumberOfKeys() == 0) {

            if (node.getNumberOfKeys() != 1 || idx != 0) {
                throw new IllegalStateException("Empty child without sibling");
            }

            newNode.setHeader(NodeType.NODE.getVal(), 0);
        }

        else {

            nodeReplaceChildN(
                    newNode,
                    node,
                    idx,
                    new BNode[]{updated}
            );
        }

        return newNode;
    }

    private BNode treeDelete(
            BNode node,
            byte[] key
    ) {
        int idx = node.lookupLE(key);

        if (node.getType() == NodeType.LEAF.getVal()) {

            if (!Arrays.equals(key, node.getKey(idx))) {
                return null;
            }

            BNode newNode = new BNode(new byte[PAGE_SIZE]);

            leafDelete(
                    newNode,
                    node,
                    idx
            );

            return newNode;
        }

        else if (node.getType() == NodeType.NODE.getVal()) {

            return nodeDelete(
                    node,
                    idx,
                    key
            );
        }

        else {
            throw new IllegalStateException("Unknown node type");
        }
    }

    private MergeDecision shouldMerge(
            BNode node,
            int idx,
            BNode updated
    ) {
        if (updated.getSizeBytes() > PAGE_SIZE / 4) {
            return new MergeDecision(0, null);
        }

        if (idx > 0) {

            BNode sibling = getNode(node.getPtr(idx - 1));

            int mergedSize = sibling.getSizeBytes()
                            + updated.getSizeBytes()
                            - HEADER_SIZE;

            if (mergedSize <= PAGE_SIZE) {
                return new MergeDecision(-1, sibling);
            }
        }

        if (idx + 1 < node.getNumberOfKeys()) {

            BNode sibling = getNode(node.getPtr(idx + 1));

            int mergedSize = sibling.getSizeBytes()
                            + updated.getSizeBytes()
                            - HEADER_SIZE;

            if (mergedSize <= PAGE_SIZE) {
                return new MergeDecision(1, sibling);
            }
        }

        return new MergeDecision(0, null);
    }

    public static void nodeMerge(
            BNode newNode,
            BNode left,
            BNode right
    ) {

        int leftNKeys = left.getNumberOfKeys();
        int rightNKeys = right.getNumberOfKeys();

        newNode.setHeader(left.getType(), leftNKeys + rightNKeys);

        newNode.appendRange(
                left,
                0,
                0,
                leftNKeys
        );

        newNode.appendRange(
                right,
                leftNKeys,
                0,
                rightNKeys
        );
    }

    public static void nodeReplace2Kid(
            BNode newNode,
            BNode oldNode,
            int idx,
            long ptr,
            byte[] key
    ) {

        int oldNKeys = oldNode.getNumberOfKeys();

        newNode.setHeader(NodeType.NODE.getVal(), oldNKeys - 1);

        newNode.appendRange(
                oldNode,
                0,
                0,
                idx
        );

        newNode.appendKV(idx , ptr , key , EMPTY_VALUE);

        newNode.appendRange(
                oldNode,
                idx+1,
                idx+2,
                oldNKeys - idx - 2
        );
    }

    public static void leafDelete(
            BNode newNode,
            BNode oldNode,
            int idx
    ){
        int oldNKeys = oldNode.getNumberOfKeys();

        newNode.setHeader(NodeType.LEAF.getVal(), oldNKeys - 1);

        newNode.appendRange(
                oldNode,
                0,
                0,
                idx
        );

        newNode.appendRange(
                oldNode,
                idx,
                idx+1,
                oldNKeys - idx - 1
        );
    }


    public static void leafInsert(
            BNode newNode,
            BNode oldNode,
            int idx,
            byte[] key,
            byte[] val
    ) {
        newNode.setHeader(
                NodeType.LEAF.getVal(),
                oldNode.getNumberOfKeys() + 1
        );

        newNode.appendRange(
                oldNode,
                0,
                0,
                idx
        );

        newNode.appendKV(
                idx,
                0,
                key,
                val
        );

        newNode.appendRange(
                oldNode,
                idx + 1,
                idx,
                oldNode.getNumberOfKeys() - idx
        );
    }

    public static void leafUpdate(
            BNode newNode,
            BNode oldNode,
            int idx,
            byte[] key,
            byte[] val
    ) {
        newNode.setHeader(
                NodeType.LEAF.getVal(),
                oldNode.getNumberOfKeys()
        );

        newNode.appendRange(
                oldNode,
                0,
                0,
                idx
        );

        newNode.appendKV(
                idx,
                0,
                key,
                val
        );

        newNode.appendRange(
                oldNode,
                idx + 1,
                idx + 1,
                oldNode.getNumberOfKeys() - idx - 1
        );
    }


    public static void nodeSplit2(
            BNode left,
            BNode right,
            BNode old
    ) {
        int nkeys = old.getNumberOfKeys();

        if (nkeys < 2) {
            throw new IllegalArgumentException(
                    "Cannot split a node with fewer than 2 keys"
            );
        }

        int nleft = nkeys / 2;

        while (calculateLeftSize(old, nleft) > PAGE_SIZE) {
            nleft--;
        }

        if (nleft < 1) {
            throw new IllegalStateException(
                    "Left side must contain at least one key"
            );
        }

        while (calculateRightSize(old, nleft) > PAGE_SIZE) {
            nleft++;
        }

        if (nleft >= nkeys) {
            throw new IllegalStateException(
                    "Right side must contain at least one key"
            );
        }

        int nright = nkeys - nleft;

        left.setHeader(
                old.getType(),
                nleft
        );

        right.setHeader(
                old.getType(),
                nright
        );

        left.appendRange(
                old,
                0,
                0,
                nleft
        );

        right.appendRange(
                old,
                0,
                nleft,
                nright
        );

        if (right.getSizeBytes() > PAGE_SIZE) {
            throw new IllegalStateException(
                    "Right node still exceeds page size"
            );
        }
    }

    public static SplitResult nodeSplit3(BNode old) {

        if (old.getSizeBytes() <= PAGE_SIZE) {

            BNode node = new BNode(
                    Arrays.copyOf(
                            old.getData(),
                            PAGE_SIZE
                    )
            );

            return new SplitResult(
                    1,
                    new BNode[]{node}
            );
        }

        BNode left = new BNode(new byte[2 * PAGE_SIZE]);

        BNode right = new BNode(new byte[PAGE_SIZE]);

        nodeSplit2(left, right, old);

        if (left.getSizeBytes() <= PAGE_SIZE) {

            BNode resizedLeft = new BNode(
                    Arrays.copyOf(
                            left.getData(),
                            PAGE_SIZE
                    )
            );

            return new SplitResult(
                    2,
                    new BNode[]{resizedLeft, right}
            );
        }

        BNode leftLeft = new BNode(new byte[PAGE_SIZE]);

        BNode middle = new BNode(new byte[PAGE_SIZE]);

        nodeSplit2(
                leftLeft,
                middle,
                left
        );

        if (leftLeft.getSizeBytes() > PAGE_SIZE) {
            throw new IllegalStateException("Left-left node still exceeds page size");
        }

        return new SplitResult(
                3,
                new BNode[]{
                        leftLeft,
                        middle,
                        right
                }
        );

    }

    private BNode treeInsert(
            BNode node,
            byte[] key,
            byte[] val
    ) {

        BNode newNode = new BNode(new byte[2 * PAGE_SIZE]);

        int idx = node.lookupLE(key);

        if (node.getType() == NodeType.LEAF.getVal()) {

            if (Arrays.equals(key, node.getKey(idx))) {

                leafUpdate(
                        newNode,
                        node,
                        idx,
                        key,
                        val
                );

            } else {

                leafInsert(
                        newNode,
                        node,
                        idx + 1,
                        key,
                        val
                );
            }

        } else if (node.getType() == NodeType.NODE.getVal()) {

            long childPtr = node.getPtr(idx);

            BNode childNode = getNode(childPtr);

            BNode updatedChild = treeInsert(childNode, key, val);

            SplitResult result = nodeSplit3(updatedChild);

            deleteNode(childPtr);

            nodeReplaceChildN(
                    newNode,
                    node,
                    idx,
                    result.nodes()
            );

        } else {
            throw new IllegalStateException("Unknown node type");
        }

        return newNode;
    }



    private void nodeReplaceChildN(
            BNode newNode,
            BNode oldNode,
            int idx,
            BNode[] kids
    ) {

        int nKids = kids.length;
        int oldNKeys = oldNode.getNumberOfKeys();

        newNode.setHeader(
                NodeType.NODE.getVal(),
                oldNKeys + nKids - 1
        );

        newNode.appendRange(
                oldNode,
                0,
                0,
                idx
        );

        for (int i = 0; i < nKids; i++) {

            BNode child = kids[i];

            long pageNumber = allocateNode(child);

            newNode.appendKV(
                    idx + i,
                    pageNumber,
                    child.getKey(0),
                    EMPTY_VALUE
            );
        }

        newNode.appendRange(
                oldNode,
                idx + nKids,
                idx + 1,
                oldNKeys - idx - 1
        );
    }


    BNode getNode(long pageNumber) {
        return new BNode(pageGetter.get(pageNumber));
    }

    private long allocateNode(BNode node) {
        return pageAllocator.allocate(node.getData());
    }

    private void deleteNode(long pageNumber) {
        pageDeleter.delete(pageNumber);
    }

    private static int calculateLeftSize(BNode old, int nleft) {
        return 4 + 8 * nleft + 2 * nleft + old.getOffset(nleft);
    }

    private static int calculateRightSize(BNode old, int nleft) {
        return old.getSizeBytes() - calculateLeftSize(old, nleft) + 4;
    }

    private static void checkLimit(byte[] key, byte[] value) {

        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        if (key.length == 0) {
            throw new IllegalArgumentException("Key cannot be empty");
        }

        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        if (key.length > MAX_KEY_SIZE) {
            throw new IllegalArgumentException("Key is too large. Maximum size is "
                            + MAX_KEY_SIZE + " bytes"
            );
        }

        if (value.length > MAX_VAL_SIZE) {
            throw new IllegalArgumentException("Value is too large. Maximum size is "
                            + MAX_VAL_SIZE + " bytes"
            );
        }
    }

    public long getRoot() {
        return root;
    }

    public void setRoot(long root) {
        if (root < 0) {
            throw new IllegalArgumentException("Invalid root page");
        }

        this.root = root;
    }

    public void update(UpdateReq request) throws Exception {
        request.added = false;
        request.applied = false;

        checkLimit(request.key, request.value);

        boolean exists = get(request.key) != null;

        if (request.mode == UpdateMode.INSERT_ONLY && exists) {
            return;
        }

        if (request.mode == UpdateMode.UPDATE_ONLY && !exists) {
            return;
        }

        insert(request.key, request.value);

        request.added = !exists;
        request.applied = true;
    }

    private record MergeDecision(int direction, BNode sibling) {}
}
