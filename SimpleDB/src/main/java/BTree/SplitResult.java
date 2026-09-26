package BTree;

public record SplitResult(
        int count,
        BNode[] nodes
) {}