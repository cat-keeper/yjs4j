package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.types.YXmlText;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.event.EventOperator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 撤消重做测试
 *
 * @author zbs
 * @date 2025/09/05  14:36:01
 */
public class UndoRedoTest {

    void testYjsMerge(Doc ydoc) {
        YXmlText content = ydoc.get("text", YXmlText.class);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("bold", null);
        content.format(0, 6, attributes);

        attributes = new HashMap<>();
        attributes.put("type", "text");
        content.format(6, 4, attributes);
        List<EventOperator> delta = content.toDelta();
        Assertions.assertEquals("text", delta.get(0).attributes.get("type"));
        Assertions.assertEquals("Merge Test", delta.get(0).insert);
        Assertions.assertEquals("text", delta.get(1).attributes.get("type"));
        Assertions.assertEquals(true, delta.get(1).attributes.get("italic"));
        Assertions.assertEquals(" After", delta.get(1).insert);
    }

    Doc initializeYDoc() {
        DocOptions docOptions = new DocOptions();
        docOptions.gc = false;
        Doc yDoc = new Doc(docOptions);
        YXmlText content = yDoc.get("text", YXmlText.class);
        content.insert(0, " After", Map.of("type", "text", "italic", true));
        content.insert(0, "Test", Map.of("type", "text"));
        content.insert(0, "Merge ", Map.of("type", "text", "bold", true));
        return yDoc;
    }

    @Test
    void testInconsistentFormat() {
        {
            Doc yDoc = initializeYDoc();
            testYjsMerge(yDoc);
        }
        {
            Doc initialYDoc = initializeYDoc();
            DocOptions docOptions = new DocOptions();
            docOptions.gc = false;
            Doc yDoc = new Doc(docOptions);

            EncodingUtil.applyUpdate(yDoc, EncodingUtil.encodeStateAsUpdate(initialYDoc, null));
            testYjsMerge(yDoc);
        }
    }

    @Test
    void testUndoText() {

    }
}
