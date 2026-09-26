package BTree;

public class BTREE_TEST {

    public static void main(String[] args) throws Exception {

        BTreeTestContext c = new BTreeTestContext();

        c.add("apple", "red");
        c.add("banana", "yellow");
        c.add("cat", "meow");
        c.add("dog", "woof");

        System.out.println("Reference data:");
        System.out.println(c.ref);

        System.out.println();
        System.out.println("Allocated pages:");
        System.out.println(c.pages.keySet());

        boolean deleted = c.tree.delete("cat".getBytes());

        System.out.println();
        System.out.println("Deleted cat: " + deleted);

        boolean deleted2 = c.tree.delete("dog".getBytes());
        System.out.println("Deleted dog: " + deleted2);

        boolean deletedAgain = c.tree.delete("elephant".getBytes());

        System.out.println("Deleted elephant: " + deletedAgain);

        for (var entry : c.pages.entrySet()) {

            long pageId = entry.getKey();
            BNode node = new BNode(entry.getValue());

            System.out.println(
                    "Page " + pageId
                            + " -> type=" + node.getType()
                            + ", keys=" + node.getNumberOfKeys()
                            + ", size=" + node.getSizeBytes()
            );

            if (node.getSizeBytes() > BTREE.PAGE_SIZE) {
                throw new AssertionError(
                        "Page is too large!"
                );
            }
        }
        System.out.println();

        System.out.println("Allocated pages:");
        System.out.println(c.pages.keySet());

        for (int i = 0; i < 1000; i++) {
            c.add(
                    String.format("key-%04d", i),
                    "value-" + i
            );
        }

        System.out.println(
                "Inserted 1000 keys. Pages = " + c.pages.size()
        );


        System.out.println();
        System.out.println("Allocated pages:");
        System.out.println(c.pages.keySet());


        for (int i = 0; i < 800; i++) {
            c.delete(String.format("key-%04d", i));
        }

        System.out.println(
                "After deleting 800 keys. Pages = " + c.pages.size()
        );

        System.out.println();
        System.out.println("Allocated pages:");
        System.out.println(c.pages.keySet());


        System.out.println();
        System.out.println("Test finished without crashing.");
    }
}