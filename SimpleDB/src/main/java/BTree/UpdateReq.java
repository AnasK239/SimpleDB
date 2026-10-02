package BTree;

import java.util.Objects;

public final class UpdateReq {
    // Inputs
    public final byte[] key;
    public final byte[] value;
    public final UpdateMode mode;

    // Outputs
    public boolean added;
    public boolean applied;
    public byte[] oldValue;

    public UpdateReq(byte[] key, byte[] value, UpdateMode mode) {
        this.key = key;
        this.value = value;
        this.mode = Objects.requireNonNull(mode);
    }
}