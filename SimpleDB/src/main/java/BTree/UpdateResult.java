package BTree;

public record UpdateResult(
        boolean applied,
        boolean added,
        byte[] oldValue
) {
    public UpdateResult(boolean applied, boolean added) {
        this(applied, added, null);
    }
}