package com.triibiotech.yjs.structs;

import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.StructStore;
import com.triibiotech.yjs.utils.Transaction;

/**
 * Content that represents embedded objects
 *
 * @author zbs
 * @date 2025/08/01 22:34:32
 */
public class ContentEmbed extends AbstractContent {

    private Object embed;

    public ContentEmbed(Object embed) {
        this.embed = embed;
    }

    public void setEmbed(Object embed) {
        this.embed = embed;
    }

    public Object getEmbed() {
        return embed;
    }

    @Override
    public long getLength() {
        return 1;
    }

    @Override
    public Object[] getContent() {
        return new Object[]{embed};
    }

    @Override
    public boolean isCountable() {
        return true;
    }

    @Override
    public AbstractContent copy() {
        return new ContentEmbed(embed);
    }

    @Override
    public AbstractContent splice(int offset) {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        return false;
    }

    @Override
    public void integrate(Transaction transaction, Item item) {
    }

    @Override
    public void delete(Transaction transaction) {
    }

    @Override
    public void gc(StructStore store) {
    }

    @Override
    public void write(DSEncoder encoder, long offset) {
        encoder.writeJSON(this.embed);
    }

    @Override
    public int getRef() {
        return 5;
    }

    public static ContentEmbed readContentEmbed(DSDecoder decoder) {
        return new ContentEmbed(decoder.readJson());
    }
}
