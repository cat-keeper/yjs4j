package com.triibiotech.yjs.types;

import com.triibiotech.yjs.structs.ContentType;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Represents a subset of the nodes of a YXmlElement / YXmlFragment and a
 * position within them.
 * Can be created with {@link YXmlFragment#createTreeWalker}
 *
 * @author zbs
 * @date 2025/7/31 14:32
 **/
public class YXmlTreeWalker implements Iterator<YXmlTreeWalker.IteratorResult> {
    private final Predicate<AbstractType<?>> filter;
    private final YXmlFragment root;
    private Item currentNode;
    private boolean firstCall;

    public YXmlTreeWalker(YXmlFragment root, Predicate<AbstractType<?>> filter) {
        this.filter = filter != null ? filter : (x) -> true;
        this.root = root;
        this.currentNode = root.start;
        this.firstCall = true;
        if (root.doc == null) {
            EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
        }
    }

    @Override
    public boolean hasNext() {
        return currentNode != null;
    }

    @Override
    public IteratorResult next() {
        Item n = currentNode;
        AbstractType<?> type = (n != null && n.content instanceof ContentType) ? ((ContentType) n.content).getType() : null;

        if (n != null && (!firstCall || (n.isDeleted() || !filter.test(type)))) {
            do {
                type = ((ContentType) n.content).getType();
                if (!n.isDeleted() && (type instanceof YXmlElement) && type.start != null) {
                    // 向下遍历树
                    n = type.start;
                } else {
                    // 向右或向上遍历
                    while (n != null) {
                        Item nxt = n.getNext();
                        if (nxt != null) {
                            n = nxt;
                            break;
                        } else if (n.parent == root) {
                            n = null;
                        } else {
                            n = ((AbstractType<?>) n.parent).item;
                        }
                    }
                }
            } while (n != null && (n.isDeleted() || !filter.test(((ContentType) n.content).getType())));
        }

        firstCall = false;

        if (n == null) {
            currentNode = null;
            throw new NoSuchElementException();
        }

        currentNode = n;
        return new IteratorResult(((ContentType) n.content).getType(), false);
    }

    public static class IteratorResult {
        public Object value;
        public Boolean done;

        public IteratorResult(Object value, Boolean done) {
            this.value = value;
            this.done = done;
        }
    }
}
