package com.triibiotech.yjs.types;

import com.triibiotech.yjs.structs.ContentType;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.utils.Transaction;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.event.YArrayEvent;

import java.util.*;

/**
 * yarray
 *
 * @author zbs
 * @date 2025/07/31  09:48:45
 */
public class YArray<T> extends AbstractType<YArrayEvent<T>> {

    public List<Object> prelimContent;

    public YArray() {
        super();
        this.prelimContent = new ArrayList<>();
        this.searchMarker = new ArrayList<>();
    }

    /**
     * Construct a new YArray containing the specified items.
     */
    public static YArray from(Object... items) {
        YArray array = new YArray();
        if (items != null && items.length > 0) {
            array.push(items);
        }
        return array;
    }

    /**
     * Integrate this type into the Yjs instance.
     * * Save this struct in the os
     * * This type is sent to other client
     * * Observer functions are fired
     *
     * @param y    The Yjs instance
     * @param item item
     */
    @Override
    public void integrate(Doc y, Item item) {
        super.integrate(y, item);
        this.insert(0, this.prelimContent.toArray());
        this.prelimContent = null;
    }

    @Override
    public YArray copy() {
        return new YArray();
    }

    @Override
    public YArray clone() {
        YArray arr = new YArray();
        for (Object el : this.toArray()) {
            if (el instanceof AbstractType<?> elType) {
                arr.insert((int) arr.getLength(), elType.clone());
            } else {
                arr.insert((int) arr.getLength(), el);
            }
        }
        return arr;
    }

    @Override
    public long getLength() {
        if (doc == null) {
            EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
        }
        return this.length;
    }

    @Override
    public void callObserver(Transaction transaction, Set<String> parentSubs) {
        super.callObserver(transaction, parentSubs);
        callTypeObservers(this, transaction, new YArrayEvent(this, transaction));
    }

    public void insert(long index, Object... contents) {
        if (doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeListInsertGenerics(transaction, this, index, contents);
                return true;
            }, null, true);
        } else {
            this.prelimContent.addAll((int) index, List.of(contents));
        }
    }

    public void push(Object... contents) {
        if (doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeListPushGenerics(transaction, this, contents);
                return true;
            }, null, true);
        } else {
            this.prelimContent.addAll(List.of(contents));
        }
    }

    public void unshift(Object... contents) {
        this.insert(0, contents);
    }

    public void delete(long index) {
        delete(index, 1);
    }

    public void delete(long index, long length) {
        if (doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeListDelete(transaction, this, index, length);
                return true;
            }, null, true);
        } else {
            // 如果文档未绑定，则先从预备内容中移除
            for (int i = 0; i < length; i++) {
                this.prelimContent.remove(index);
            }
        }
    }

    public T get(int index) {
        return (T) typeListGet(this, index);
    }

    public List<Object> toArray() {
        return typeListToArray(this);
    }

    /**
     * Returns a portion of this YArray into a JavaScript Array selected
     * from start to end (end not included).
     */
    public List<Object> slice(Long start, Long end) {
        if (start == null) {
            start = 0L;
        }
        if (end == null) {
            end = this.length;
        }
        return typeListSlice(this, start, end);
    }

    @Override
    public List<Object> toJson() {
        return this.map((el, index, type) -> {
            if (el instanceof AbstractType<?> elType) {
                return elType.toJson();
            }
            return el;
        });
    }

    /**
     * Returns an Array with the result of calling a provided function on every
     * element of this YArray.
     */
    public <R> List<R> map(TriFunction<Object, Integer, AbstractType<?>, R> f) {
        return typeListMap(this, f);
    }

    public void forEach(TriConsumer<Object, Integer, AbstractType<?>> consumer) {
        typeListForEach(this, consumer);
    }

    public Iterator<Object> iterator() {
        return typeListCreateIterator(this);
    }

    @Override
    public void write(DSEncoder encoder) {
        encoder.writeTypeRef(ContentType.YArrayRefID);
    }

    public static YArray readYArray(DSDecoder decoder) {
        return new YArray();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        YArray yArray = (YArray) o;
        return Objects.equals(prelimContent, yArray.prelimContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), prelimContent);
    }
}
