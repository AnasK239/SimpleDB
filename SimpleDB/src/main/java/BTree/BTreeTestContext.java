package BTree;

import java.util.HashMap;
import java.util.Map;

public class BTreeTestContext {

    public final BTREE tree;

    public final Map<String, String> ref = new HashMap<>();

    public final Map<Long, byte[]> pages = new HashMap<>();

    private long nextPageId = 1;

    public BTreeTestContext() {

        PageGet pageGet = pageNumber -> {
            byte[] page = pages.get(pageNumber);

            if (page == null) {
                throw new IllegalStateException("Page does not exist: " + pageNumber);
            }

            return page;
        };

        PageAllocate pageAllocate = data -> {

            BNode node = new BNode(data);

            if (node.getSizeBytes() > BTREE.PAGE_SIZE) {
                throw new IllegalStateException("Node exceeds page size");
            }

            long pageNumber = nextPageId++;

            if (pages.containsKey(pageNumber)) {
                throw new IllegalStateException("Page already exists: " + pageNumber);
            }

            pages.put(pageNumber, data);

            return pageNumber;
        };

        PageDelete pageDelete = pageNumber -> {

            if (!pages.containsKey(pageNumber)) {
                throw new IllegalStateException("Cannot delete missing page: " + pageNumber);
            }

            pages.remove(pageNumber);
        };

        tree = new BTREE(
                pageGet,
                pageAllocate,
                pageDelete
        );
    }

    public void add(String key, String value) throws Exception {

        tree.insert(key.getBytes(), value.getBytes());

        ref.put(key, value);
    }

    public boolean delete(String key) throws Exception {

        boolean deleted = tree.delete(key.getBytes());

        if (deleted) {
            ref.remove(key);
        }

        return deleted;
    }
}