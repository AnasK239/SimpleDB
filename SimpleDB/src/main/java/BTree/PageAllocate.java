package BTree;

@FunctionalInterface
public interface PageAllocate {
    long allocate(byte[] data);
}
