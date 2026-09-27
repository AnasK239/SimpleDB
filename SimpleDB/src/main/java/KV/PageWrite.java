package KV;

@FunctionalInterface
public interface PageWrite {
    byte[] write(long pageNumber);
}
