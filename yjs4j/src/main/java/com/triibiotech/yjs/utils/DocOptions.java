package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.Item;
import java.util.UUID;
import java.util.function.Function;

/**
 * Configuration options for Doc
 * Equivalent to DocOpts in JavaScript
 */
public class DocOptions {
    /**
     * Disable garbage collection (default: gc=true)
     */
    public boolean gc = true;

    /**
     * Will be called before an Item is garbage collected. Return false to keep the Item.
     */
    public Function<Item, Boolean> gcFilter = item -> true;

    /**
     * Define a globally unique identifier for this document
     */
    public String guid = UUID.randomUUID().toString();

    /**
     * Associate this document with a collection. This only plays a role if your provider has a concept of collection.
     */
    public String collectionid = null;

    /**
     * Any kind of meta information you want to associate with this document.
     */
    public Object meta = null;

    /**
     * If a subdocument, automatically load document.
     */
    public boolean autoLoad = false;

    /**
     * Whether the document should be synced by the provider now.
     */
    public boolean shouldLoad = true;

    public DocOptions withGc(boolean gc) {
        this.gc = gc;
        return this;
    }
}
