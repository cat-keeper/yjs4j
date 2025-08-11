package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.ID;
import com.triibiotech.yjs.utils.StructStore;
import com.triibiotech.yjs.utils.Transaction;

import java.util.Objects;

/**
 * Abstract base class for all structs in Yjs.
 * Matches the functionality of AbstractStruct.js from the original Yjs implementation.
 *
 * @author zbs
 * @date 2025/07/29  17:18:11
 */
public abstract class AbstractStruct {
    /**
     * The unique identifier for this struct
     */
    public ID id;

    /**
     * The length of this struct (number of operations it represents)
     */
    public long length;

    /**
     * The item that is currently to the left of this item
     */
    public AbstractStruct left;

    /**
     * The item that is currently to the right of this item
     */
    public AbstractStruct right;

    /**
     * Creates a new AbstractStruct with the given ID and length.
     *
     * @param id     The unique identifier for this struct
     * @param length The length of this struct
     * @throws IllegalArgumentException if id is null or length is negative
     */
    protected AbstractStruct(ID id, long length) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length);
        }
        this.length = length;
    }

    /**
     * Returns whether this struct is deleted.
     * This is an abstract property that must be implemented by subclasses.
     *
     * @return true if this struct is deleted, false otherwise
     */
    public abstract boolean isDeleted();

    public void delete(Transaction transaction) {

    }

    /**
     * Attempts to merge this struct with the struct to the right.
     * This method assumes that this.id.clock + this.length === right.id.clock.
     * This method does NOT remove right from StructStore!
     *
     * @param right The struct to merge with
     * @return true if this struct was successfully merged with right, false otherwise
     */
    public boolean mergeWith(AbstractStruct right) {
        // Default implementation - no merging
        return false;
    }

    /**
     * Writes this struct to the encoder.
     *
     * @param encoder The encoder to write data to
     * @param offset  The offset within this struct to start writing from
     * @throws UnsupportedOperationException if not implemented by subclass
     */
    public abstract void write(DSEncoder encoder, long offset);

    /**
     * Integrates this struct into the document.
     *
     * @param transaction The transaction context
     * @param offset      The offset within this struct
     * @throws UnsupportedOperationException if not implemented by subclass
     */
    public abstract void integrate(Transaction transaction, long offset);

    /**
     * Gets missing dependencies for this struct.
     * Returns the client ID that this struct depends on, or null if no dependencies.
     *
     * @param transaction The transaction context
     * @param store       The struct store
     * @return The client ID that this struct depends on, or null if no dependencies
     */
    public Long getMissing(Transaction transaction, StructStore store) {
        // Default implementation - no dependencies
        return null;
    }

    /**
     * Splits this struct at the given difference.
     * Creates a new struct representing the right part after the split.
     *
     * @param diff The position to split at
     * @return A new struct representing the right part
     * @throws UnsupportedOperationException if splitting is not supported
     */
    public AbstractStruct splice(int diff) {
        throw new UnsupportedOperationException("Splice not supported for " + getClass().getSimpleName());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", length=" + length + ", deleted=" + isDeleted() + "}";
    }

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }
}
