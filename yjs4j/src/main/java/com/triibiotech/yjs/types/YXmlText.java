package com.triibiotech.yjs.types;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.triibiotech.yjs.structs.ContentType;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.utils.DSDecoder;
import com.triibiotech.yjs.utils.DSEncoder;
import com.triibiotech.yjs.utils.event.EventOperator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents text in a Dom Element. In the future this type will also handle
 * simple formatting information like bold and italic.
 *
 * @author zbs
 * @date 2025/07/31  14:50:10
 */
public class YXmlText extends YText {

    public YXmlText() {
        super();
    }

    public YXmlText(String string) {
        super(string);
    }

    public YXmlText getNextSibling() {
        Item n = item != null ? item.getNext() : null;
        return n != null ? (YXmlText) n.content.getContent()[0] : null;
    }

    public YXmlText getPrevSibling() {
        Item n = item != null ? item.getPrev() : null;
        return n != null ? (YXmlText) n.content.getContent()[0] : null;
    }

    @Override
    public YXmlText copy() {
        return new YXmlText();
    }

    @Override
    public YXmlText clone() {
        super.clone();
        YXmlText text = new YXmlText();
        text.applyDelta(this.toDelta(null, null, null), true);
        return text;
    }

    private static class NestedNode {
        String nodeName;
        List<Attr> attrs;

        NestedNode(String nodeName, List<Attr> attrs) {
            this.nodeName = nodeName;
            this.attrs = attrs;
        }
    }

    private static class Attr {
        String key;
        Object value;

        Attr(String key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (EventOperator delta : toDelta()) {
            List<NestedNode> nestedNodes = new ArrayList<>();
            JSONObject attributes = JSON.parseObject(JSON.toJSONString(delta.attributes));
            if (attributes != null) {
                for (String nodeName : attributes.keySet()) {
                    List<Attr> attrs = new ArrayList<>();
                    Object object = attributes.get(nodeName);
                    if (object instanceof String) {
                        for (int i = 0; i < ((String) object).length(); i++) {
                            attrs.add(new Attr(i + "", ((String) object).charAt(i)));
                        }
                    }
                    // 对属性排序，确保唯一顺序
                    attrs.sort(Comparator.comparing(attr -> attr.key));

                    nestedNodes.add(new NestedNode(nodeName, attrs));
                }
            }
            // 对 nodeName 排序，确保唯一顺序
            nestedNodes.sort(Comparator.comparing(n -> n.nodeName));

            StringBuilder str = new StringBuilder();
            for (NestedNode node : nestedNodes) {
                str.append("<").append(node.nodeName);
                for (Attr attr : node.attrs) {
                    str.append(" ").append(attr.key).append("=\"").append(attr.value).append("\"");
                }
                str.append(">");
            }
            str.append(delta.getInsert());

            for (int i = nestedNodes.size() - 1; i >= 0; i--) {
                str.append("</").append(nestedNodes.get(i).nodeName).append(">");
            }

            result.append(str);
        }
        return result.toString();
    }


    @Override
    public String toJson() {
        return this.toString();
    }

    @Override
    public void write(DSEncoder encoder) {
        encoder.writeTypeRef(ContentType.YXmlTextRefID);
    }

    public static YXmlText readYXmlText(DSDecoder decoder) {
        return new YXmlText();
    }


}
