package com.triibiotech.yjs.types;

import com.triibiotech.yjs.structs.ContentType;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.utils.*;

import java.util.*;

/**
 * yxml元素
 *
 * @author zbs
 * @date 2025/07/30  14:37:05
 */
@SuppressWarnings("unused")
public class YXmlElement extends YXmlFragment {

    public static final int Y_XML_ELEMENT_REF_ID = 3;

    public String nodeName;
    private Map<String, Object> prelimAttrs;

    public YXmlElement() {
        this("UNDEFINED");
    }

    public YXmlElement(String nodeName) {
        super();
        this.nodeName = nodeName;
        this.prelimAttrs = new HashMap<>();
    }

    public YXmlElement getNextSibling() {
        Item n = item != null ? item.getNext() : null;
        return n != null ? (YXmlElement) n.content.getContent()[0] : null;
    }

    public YXmlElement getPrevSibling() {
        Item n = item != null ? item.getPrev() : null;
        return n != null ? (YXmlElement) n.content.getContent()[0] : null;
    }

    @Override
    public void integrate(Doc y, Item item) {
        super.integrate(y, item);
        if (prelimAttrs != null) {
            prelimAttrs.forEach(this::setAttribute);
            prelimAttrs = null;
        }
    }

    @Override
    public YXmlElement copy() {
        return new YXmlElement(nodeName);
    }

    @Override
    public YXmlElement clone() {
        super.clone();
        YXmlElement el = new YXmlElement(nodeName);
        Map<String, Object> attrs = getAttributes(null);
        attrs.forEach(el::setAttribute);
        List<Object> content = this.toArray();
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
    public String toString() {
        Map<String, Object> attrs = getAttributes(null);
        List<String> stringBuilder = new ArrayList<>();
        List<String> keys = new ArrayList<>(attrs.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            stringBuilder.add(key + "=\"" + attrs.get(key) + "\"");
        }

        String nodeNameLower = nodeName.toLowerCase();
        String attrsString = stringBuilder.isEmpty() ? "" : " " + String.join(" ", stringBuilder);
        return "<" + nodeNameLower + attrsString + ">" + super.toString() + "</" + nodeNameLower + ">";
    }

    public void removeAttribute(String attributeName) {
        if (doc != null) {
            doc.transact(transaction -> {
                AbstractType.typeMapDelete(transaction, this, attributeName);
                return null;
            });
        } else if (prelimAttrs != null) {
            prelimAttrs.remove(attributeName);
        }
    }

    public void setAttribute(String attributeName, Object attributeValue) {
        if (doc != null) {
            Transaction.transact(this.doc, transaction -> {
                AbstractType.typeMapSet(transaction, this, attributeName, attributeValue);
                return true;
            }, null, true);
        } else if (prelimAttrs != null) {
            prelimAttrs.put(attributeName, attributeValue);
        }
    }

    public Object getAttribute(String attributeName) {
        return typeMapGet(this, attributeName);
    }

    public boolean hasAttribute(String attributeName) {
        return typeMapHas(this, attributeName);
    }

    public Map<String, Object> getAttributes(Snapshot snapshot) {
        return snapshot != null ? typeMapGetAllSnapshot(this, snapshot) : typeMapGetAll(this);
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    @Override
    public void write(DSEncoder encoder) {
        encoder.writeTypeRef(ContentType.YXmlElementRefID);
        encoder.writeKey(this.nodeName);
    }

    public static YXmlElement readYXmlElement(DSDecoder decoder) {
        return new YXmlElement(decoder.readKey());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof YXmlElement that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(nodeName, that.nodeName) && Objects.equals(prelimAttrs, that.prelimAttrs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), nodeName, prelimAttrs);
    }
}
