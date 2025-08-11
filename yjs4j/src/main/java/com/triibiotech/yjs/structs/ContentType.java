package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.types.*;
import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.StructStore;
import com.triibiotech.yjs.utils.Transaction;

import java.util.HashMap;

/**
 * 内容类型
 *
 * @author zbs
 * @date 2025/07/29  09:13:00
 */
public class ContentType extends AbstractContent {
    private final AbstractType<?> type;

    public final static Integer YArrayRefID = 0;
    public final static Integer YMapRefID = 1;
    public final static Integer YTextRefID = 2;
    public final static Integer YXmlElementRefID = 3;
    public final static Integer YXmlFragmentRefID = 4;
    public final static Integer YXmlHookRefID = 5;
    public final static Integer YXmlTextRefID = 6;

    public ContentType(AbstractType<?> type) {
        this.type = type;
    }

    @Override
    public long getLength() {
        return 1;
    }

    @Override
    public Object[] getContent() {
        return new Object[]{type};
    }

    @Override
    public boolean isCountable() {
        return true;
    }

    @Override
    public AbstractContent copy() {
        return new ContentType(this.type.copy());
    }

    @Override
    public AbstractContent splice(int offset) {
        throw new UnsupportedOperationException("Cannot splice ContentType");
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        return false;
    }

    @Override
    public void integrate(Transaction transaction, Item item) {
        type.integrate(transaction.doc, item);
    }

    @Override
    public void delete(Transaction transaction) {
        AbstractStruct struct = this.type.getStart();
        while (struct != null) {
            if (!struct.isDeleted()) {
                struct.delete(transaction);
            } else if (struct.id.clock < (transaction.beforeState.get(struct.id.client) == null ? 0 : transaction.beforeState.get(struct.id.client))) {
                transaction._mergeStructs.addLast(struct);
            }
            if (struct instanceof Item) {
                struct = struct.right;
            } else {
                struct = null;
            }
        }
        this.type.getMap().forEach((key, value) -> {
            if (!value.isDeleted()) {
                value.delete(transaction);
            } else if (value.id.clock < (transaction.beforeState.get(value.id.client) == null ? 0 : transaction.beforeState.get(value.id.client))) {
                // same as above
                transaction._mergeStructs.addLast(value);
            }
        });
        transaction.changed.remove(this.type);
    }

    @Override
    public void gc(StructStore store) {
        AbstractStruct item = this.type.getStart();
        while (item != null) {
            if (item instanceof Item) {
                ((Item) item).gc(store, true);
                item = ((Item) item).right;
            } else {
                item = null;
            }
        }
        this.type.setStart(null);
        this.type.getMap().forEach((key, value) -> {
            while (value != null) {
                value.gc(store, true);
                value = (Item) value.left;
            }
        });
        this.type.setMap(new HashMap<>());
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        this.type.write(encoder);
    }

    @Override
    public int getRef() {
        return 7;
    }

    public static ContentType readContentType(DSDecoder decoder) {
        long index = decoder.readTypeRef();
        return switch ((int) index) {
            case 0 -> new ContentType(YArray.readYArray(decoder));
            case 1 -> new ContentType(YMap.readYMap(decoder));
            case 2 -> new ContentType(YText.readYText(decoder));
            case 3 -> new ContentType(YXmlElement.readYXmlElement(decoder));
            case 4 -> new ContentType(YXmlFragment.readYXmlFragment(decoder));
            case 5 -> new ContentType(YXmlHook.readYXmlHook(decoder));
            case 6 -> new ContentType(YXmlText.readYXmlText(decoder));
            default -> null;
        };
    }

    public AbstractType<?> getType() {
        return type;
    }
}
