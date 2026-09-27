package BTree;

import KV.*;

import java.util.Arrays;
import java.util.List;

public final class BIter {
    private final BTREE tree;

    private final List<BNode> path;
    private final List<Integer> pos;

    BIter(BTREE tree, List<BNode> path, List<Integer> pos) {
        this.tree = tree;
        this.path = path;
        this.pos = pos;
    }

    boolean hasPosition() {
        if (path.isEmpty()) {
            return false;
        }

        int level = path.size() - 1;
        int index = pos.get(level);

        return index >= 0
                && index < path.get(level).getNumberOfKeys();
    }

    int compareKey(byte[] key) {
        int level = path.size() - 1;

        return Arrays.compareUnsigned(
                path.get(level).getKey(pos.get(level)),
                key
        );
    }

    public boolean valid() {
        if (!hasPosition()) {
            return false;
        }

        int level = path.size() - 1;

        return path.get(level)
                .getKey(pos.get(level))
                .length != 0;
    }

    public KVPair deref() {
        if (!valid()) {
            throw new IllegalStateException("Iterator is not positioned on a data entry");
        }

        int level = path.size() - 1;
        BNode leaf = path.get(level);
        int index = pos.get(level);

        return new KVPair(
                leaf.getKey(index),
                leaf.getValue(index)
        );
    }

    public void next() {
        if (!hasPosition()) {
            return;
        }

        if (!move(path.size() - 1, true)) {
            invalidate();
        }
    }

    public void prev() {
        if (!hasPosition()) {
            return;
        }

        if (!move(path.size() - 1, false)) {
            invalidate();
        }
    }

    private boolean move(int level, boolean forward) {
        BNode node = path.get(level);

        int candidate = pos.get(level) + (forward ? 1 : -1);

        // There is another entry inside the current node
        if (candidate >= 0 && candidate < node.getNumberOfKeys()) {
            pos.set(level, candidate);
            return true;
        }

        // We reached the edge of the root: no further branch
        if (level == 0) {
            return false;
        }

        // Ask the parent to move to another branch
        if (!move(level - 1, forward)) {
            return false;
        }

        BNode parent = path.get(level - 1);
        int parentIndex = pos.get(level - 1);

        BNode child = tree.getNode(
                parent.getPtr(parentIndex)
        );

        // Replace this level with the newly selected child
        path.set(level, child);

        // Enter its first entry when moving forward
        // or its last entry when moving backward
        pos.set(
                level,
                forward ? 0 : child.getNumberOfKeys() - 1
        );

        return true;
    }

    private void invalidate() {
        path.clear();
        pos.clear();
    }
}