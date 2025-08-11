package com.triibiotech.yjs.types;

import com.triibiotech.yjs.structs.ContentType;
import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;

import java.util.Objects;

/**
 * yxml钩子
 *
 * @author zbs
 * @date 2025/07/31  14:47:24
 */
public class YXmlHook<Type> extends YMap<Type> {

    public String hookName;

    public YXmlHook(String hookName) {
        super();
        this.hookName = hookName;
    }

    public String getHookName() {
        return hookName;
    }

    public void setHookName(String hookName) {
        this.hookName = hookName;
    }

    @Override
    public YXmlHook<Type> copy() {
        return new YXmlHook<>(hookName);
    }

    @Override
    public YXmlHook<Type> clone() {
        YXmlHook<Type> el = new YXmlHook<>(hookName);
        for (String key : keys()) {
            el.set(key, (Type) get(key));
        }
        return el;
    }

    @Override
    public void write(DSEncoder encoder) {
        encoder.writeTypeRef(ContentType.YXmlHookRefID);
        encoder.writeKey(this.hookName);
    }

    public static YXmlHook<?> readYXmlHook(DSDecoder decoder) {
        return new YXmlHook<>(decoder.readKey());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof YXmlHook<?> yXmlHook)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(hookName, yXmlHook.hookName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), hookName);
    }
}
