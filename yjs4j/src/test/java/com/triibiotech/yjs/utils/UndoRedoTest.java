package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.types.YArray;
import com.triibiotech.yjs.types.YText;
import com.triibiotech.yjs.types.YXmlText;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.event.EventOperator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
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

    @Test
    void testEmptyTypeScope() {
        Doc ydoc = new Doc();
        UndoManagerOptions options = new UndoManagerOptions();
        UndoManager um = new UndoManager(ydoc, options);
        YArray<Object> yArray = ydoc.getArray("");
        um.addToScope(yArray);
        yArray.insert(0, 1);
        um.undo();
        Assertions.assertEquals(0, yArray.getLength());
    }

    @Test
    void testRejectUpdateExample() {
        Doc tmpydoc1 = new Doc();
        tmpydoc1.getArray("restricted").insert(0, 1);
        tmpydoc1.getArray("public").insert(0, 1);
        byte[] update1 = EncodingUtil.encodeStateAsUpdate(tmpydoc1, null);
        Doc tmpydoc2 = new Doc();
        tmpydoc2.getArray("public").insert(0, 2);
        byte[] update2 = EncodingUtil.encodeStateAsUpdate(tmpydoc2, null);
        Doc ydoc = new Doc();
        YArray<Object> restrictedType = ydoc.getArray("restricted");
        updateHandler(update1, ydoc, restrictedType);
        updateHandler(update2, ydoc, restrictedType);
        long length = restrictedType.getLength();
        long l1 = ydoc.getArray("public").getLength();
        Assertions.assertEquals(0, length);
        Assertions.assertEquals(2, l1);
    }

    void updateHandler(byte[] update, Doc ydoc, Object typeScope) {
        UndoManagerOptions options = new UndoManagerOptions();
        HashSet<Object> trackedOrigins = new HashSet<>();
        trackedOrigins.add("remote change");
        options.setTrackedOrigins(trackedOrigins);
        UndoManager um = new UndoManager(typeScope, options);

        byte[] beforePendingDs = ydoc.store.pendingDs;
        byte[] beforePendingStructs = null;
        if(ydoc.store.pendingStructs != null) {
            beforePendingStructs = ydoc.store.pendingStructs.update;
        }
        try {
            EncodingUtil.applyUpdate(ydoc, update, "remote change");
        } finally {
            while (!um.undoStack.isEmpty()) {
                um.undo();
            }
            um.destroy();
            ydoc.store.pendingDs = beforePendingDs;
            ydoc.store.pendingStructs = null;
            if (beforePendingStructs != null) {
                EncodingUtil.applyUpdateV2(ydoc, beforePendingStructs);
            }
        }
    }

    @Test
    void testGlobalScope() {
        Doc ydoc = new Doc();
        UndoManager um = new UndoManager(ydoc, new UndoManagerOptions());
        YArray<Object> yArray = ydoc.getArray("");
        yArray.insert(0, 1);
        um.undo();
        Assertions.assertEquals(0, yArray.getLength());
    }

    @Test
    void testDoubleUndo() {
        Doc ydoc = new Doc();
        YText text = ydoc.getText("");
        text.insert(0, "1221");
        UndoManager um = new UndoManager(text, new UndoManagerOptions());
        text.insert(2, "3");
        text.insert(3, "3");
        um.undo();
        um.undo();
        text.insert(2, "3");
        Assertions.assertEquals("12321", text.toString());
    }

    @Test
    void testUndoMap() {

    }

    @Test
    void testUndoArray() {

    }

    @Test
    void testUndoXml() {

    }

    @Test
    void testUndoEvents() {

    }

    @Test
    void testTrackClass() {

    }

    @Test
    void testTypeScope() {

    }


}
