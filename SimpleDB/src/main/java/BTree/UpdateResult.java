package BTree;

public record UpdateResult(
        boolean applied,
        boolean added
) {}