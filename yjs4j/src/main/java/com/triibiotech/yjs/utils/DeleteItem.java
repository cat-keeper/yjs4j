package com.triibiotech.yjs.utils;

/**
 * Represents a deleted range in the document
 */
public class DeleteItem {
    /**
     * Clock value where deletion starts
     */
    public final Long clock;

    /**
     * Length of the deleted range
     */
    public Long len;

    /**
     * @param clock Clock value where deletion starts
     * @param len Length of the deleted range
     */
    public DeleteItem(Long clock, Long len) {
        this.clock = clock;
        this.len = len;
    }

    public Long getClock() {
        return clock;
    }

    public Long getLen() {
        return len;
    }

    @Override
    public String toString() {
        return "DeleteItem{clock=" + clock + ", len=" + len + "}";
    }
}
