package BTree;

@FunctionalInterface
public interface PageGet {
    byte[] get(long pageNumber);
}
