package com.triibiotech.yjs.types;

import com.triibiotech.yjs.structs.ContentType;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.Doc;
import com.triibiotech.yjs.utils.Transaction;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.event.YXmlEvent;

import java.util.*;
import java.util.function.Predicate;

/**
 * Represents a list of {@link YXmlElement}.and {@link YXmlText} types.
 * A YxmlFragment is similar to a {@link YXmlElement}, but it does not have a
 * nodeName and it does not have attributes. Though it can be bound to a DOM
 * element - in this case the attributes and the nodeName are not shared.
 *
 * @author zbs
 * @date 2025/07/30  14:35:43
 */
public class YXmlFragment extends AbstractType<YXmlEvent> {

    public static final int Y_XML_FRAGMENT_REF_ID = 4;

    private List<Object> prelimContent;

    public YXmlFragment() {
        super();
        this.prelimContent = new ArrayList<>();
    }

    public Object getFirstChild() {
        Item first = getFirst();
        return first != null ? first.content.getContent()[0] : null;
    }

    @Override
    public void integrate(Doc y, Item item) {
        super.integrate(y, item);
        if (prelimContent != null) {
            this.insert(0, prelimContent.toArray());
            prelimContent = null;
        }
    }

    @Override
    public YXmlFragment copy() {
        return new YXmlFragment();
    }

    @Override
    public AbstractType<YXmlEvent> clone() {
        YXmlFragment el = new YXmlFragment();
        List<Object> content = toArray();
        List<Object> clonedContent = new ArrayList<>();
        for (Object item : content) {
            if (item instanceof AbstractType) {
                clonedContent.add(((AbstractType<?>) item).clone());
            } else {
                clonedContent.add(item);
            }
        }
        el.insert(0, clonedContent);
        return el;
    }

    @Override
    public long getLength() {
        if (doc == null) {
            EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
        }
        return this.prelimContent == null ? this.length : this.prelimContent.size();
    }

    public YXmlTreeWalker createTreeWalker(Predicate<AbstractType<?>> filter) {
        return new YXmlTreeWalker(this, filter);
    }

    public Object querySelector(String query) {
        String finalQuery = query.toUpperCase();
        YXmlTreeWalker iterator = new YXmlTreeWalker(this, element -> {
            if (element instanceof YXmlElement) {
                return ((YXmlElement) element).nodeName.toUpperCase().equals(finalQuery);
            }
            return false;
        });

        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }

    public List<Object> querySelectorAll(String query) {
        String finalQuery = query.toUpperCase();
        List<Object> results = new ArrayList<>();
        YXmlTreeWalker iterator = new YXmlTreeWalker(this, element -> {
            if (element instanceof YXmlElement) {
                return ((YXmlElement) element).nodeName.toUpperCase().equals(finalQuery);
            }
            return false;
        });

        while (iterator.hasNext()) {
            results.add(iterator.next());
        }
        return results;
    }

    @Override
    public void callObserver(Transaction transaction, Set<String> parentSubs) {
        callTypeObservers(this, transaction, new YXmlEvent(this, parentSubs, transaction));
    }

    @Override
    public String toString() {
        List<String> content = typeListMap(this, (t, u, v) -> t.toString());
        return String.join("", content);
    }

    @Override
    public Object toJson() {
        return toString();
    }


    public void insert(int index, Object... content) {
        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                typeListInsertGenerics(transaction, this, index, content);
                return true;
            }, null, true);
        } else if (prelimContent != null) {
            prelimContent.addAll(index, Arrays.stream(content).toList());
        }
    }

    public void insertAfter(Object ref, List<Object> content) {
        if (doc != null) {
            Transaction.transact(doc,transaction -> {
                Item refItem = (ref instanceof AbstractType) ? ((AbstractType<?>) ref).item : (Item) ref;
                typeListInsertGenericsAfter(transaction, this, refItem, content);
                return true;
            }, null, true);
        } else if (prelimContent != null) {
            int index = ref == null ? 0 : prelimContent.indexOf(ref) + 1;
            if (index == 0 && ref != null) {
                throw new RuntimeException("Reference item not found");
            }
            prelimContent.addAll(index, content);
        }
    }

    public void delete(int index, int length) {
        if (doc != null) {
            Transaction.transact(doc,transaction -> {
                typeListDelete(transaction, this, index, length);
                return true;
            }, null, true);
        } else if (prelimContent != null) {
            for (int i = 0; i < length && index < prelimContent.size(); i++) {
                prelimContent.remove(index);
            }
        }
    }

    public List<Object> toArray() {
        return typeListToArray(this);
    }

    public void push(List<Object> content) {
        this.insert((int) getLength(), content);
    }

    public void unshift(List<Object> content) {
        insert(0, content);
    }

    public Object get(int index) {
        return typeListGet(this, index);
    }

    public List<Object> slice(Long start, Long end) {
        if(start == null) {
            start = 0L;
        }
        if (end == null) {
            end = getLength();
        }
        return typeListSlice(this, start, end);
    }

    public void forEach(TriConsumer<Object, Integer, AbstractType<?>> consumer) {
        typeListForEach(this, consumer);
    }

    @Override
    public void write(DSEncoder encoder) {
        encoder.writeTypeRef(ContentType.YXmlFragmentRefID);
    }

    public static YXmlFragment readYXmlFragment(DSDecoder decoder) {
        return new YXmlFragment();
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof YXmlFragment that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(prelimContent, that.prelimContent);
    }

}
