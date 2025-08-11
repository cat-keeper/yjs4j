package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.utils.Transaction;

import com.triibiotech.yjs.utils.StructStore;

/**
 * Abstract base class for all content types in Yjs
 *
 * @author zbs
 * @date 2025/07/30 22:19:39
 */
public abstract class AbstractContent {

    protected Doc doc;

    public Doc getDoc() {
        return doc;
    }

    public void setDoc(Doc doc) {
        this.doc = doc;
    }

    /**
     * Get the length of this content
     */
    public abstract long getLength();

    /**
     * Get the actual content as an array
     */
    public abstract Object[] getContent();

    /**
     * Should return false if this Item is some kind of meta information
     * (e.g. format information).
     * Whether this Item should be addressable via yarray.get(i)
     * Whether this Item should be counted when computing yarray.length
     */
    public abstract boolean isCountable();

    /**
     * Create a copy of this content
     */
    public abstract AbstractContent copy();

    /**
     * Split this content at the given offset
     */
    public abstract AbstractContent splice(int offset);

    /**
     * Try to merge this content with another content
     */
    public abstract boolean mergeWith(AbstractContent right);

    /**
     * Integrate this content into the document
     */
    public abstract void integrate(Transaction transaction, Item item);

    /**
     * Delete this content
     */
    public abstract void delete(Transaction transaction);

    /**
     * Garbage collect this content
     */
    public abstract void gc(StructStore store);

    /**
     * Write this content to an encoder
     */
    public abstract void write(DSEncoder encoder, long offset);

    /**
     * Get the reference number for this content type
     */
    public abstract int getRef();
}
