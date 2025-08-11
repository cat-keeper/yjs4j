package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.types.YText;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author zbs
 * @date 2025/8/5 14:45
 **/
public class RelativePositionsTest {

    void checkRelativePositions(YText ytext) {
        // test if all positions are encoded and restored correctly
        for (long i = 0; i < ytext.getLength(); i++) {
            // for all types of associations..
            for (long assoc = -1; assoc < 2; assoc++) {
                RelativePosition rpos = RelativePosition.createRelativePositionFromTypeIndex(ytext, i, assoc);
                byte[] encodedRpos = RelativePosition.encodeRelativePosition(rpos);
                RelativePosition decodedRpos = RelativePosition.decodeRelativePosition(encodedRpos);
                RelativePosition.AbsolutePosition absPos = RelativePosition.createAbsolutePositionFromRelativePosition(decodedRpos, ytext.doc, null);
                Assertions.assertNotNull(absPos);
                Assertions.assertEquals(i, absPos.index);
                Assertions.assertEquals(assoc, absPos.assoc);
            }
        }
    }

    @Test
    void testRelativePositionCase1() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("");
        ytext.insert(0, "1");
        ytext.insert(0, "abc");
        ytext.insert(0, "z");
        ytext.insert(0, "y");
        ytext.insert(0, "x");
        checkRelativePositions(ytext);
    }

    @Test
    void testRelativePositionCase2() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("");
        ytext.insert(0, "abc");
        checkRelativePositions(ytext);
    }

    @Test
    void testRelativePositionCase3() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("");
        ytext.insert(0, "abc");
        ytext.insert(0, "1");
        ytext.insert(0, "xyz");
        checkRelativePositions(ytext);
    }

    @Test
    void testRelativePositionCase4() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("");
        ytext.insert(0, "1");
        checkRelativePositions(ytext);
    }

    @Test
    void testRelativePositionCase5() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("");
        ytext.insert(0, "2");
        ytext.insert(0, "1");
        checkRelativePositions(ytext);
    }

    @Test
    void testRelativePositionCase6() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("");
        checkRelativePositions(ytext);
    }

    @Test
    void testRelativePositionCase7() {
        Doc docA = new Doc();
        YText textA = docA.getText("text");
        textA.insert(0, "abcde");
        // Create a relative position at index 2 in 'textA'
        RelativePosition relativePosition = RelativePosition.createRelativePositionFromTypeIndex(textA, 2L, null);
        // Verify that the absolutes positions on 'docA' are the same
        RelativePosition.AbsolutePosition absolutePositionWithFollow =
                RelativePosition.createAbsolutePositionFromRelativePosition(relativePosition, docA, true);
        RelativePosition.AbsolutePosition absolutePositionWithoutFollow =
                RelativePosition.createAbsolutePositionFromRelativePosition(relativePosition, docA, false);
        Assertions.assertNotNull(absolutePositionWithFollow);
        Assertions.assertEquals(2, absolutePositionWithFollow.index);
        Assertions.assertNotNull(absolutePositionWithoutFollow);
        Assertions.assertEquals(2, absolutePositionWithoutFollow.index);
    }

    @Test
    void testRelativePositionAssociationDifference() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("");
        ytext.insert(0, "2");
        ytext.insert(0, "1");
        RelativePosition rposRight = RelativePosition.createRelativePositionFromTypeIndex(ytext, 1L, 0L);
        RelativePosition rposLeft = RelativePosition.createRelativePositionFromTypeIndex(ytext, 1L, -1L);
        ytext.insert(1, "x");
        RelativePosition.AbsolutePosition posRight = RelativePosition.createAbsolutePositionFromRelativePosition(rposRight, ydoc, null);
        RelativePosition.AbsolutePosition posLeft = RelativePosition.createAbsolutePositionFromRelativePosition(rposLeft, ydoc, null);
        Assertions.assertNotNull(posRight);
        Assertions.assertEquals(2, posRight.index);
        Assertions.assertNotNull(posLeft);
        Assertions.assertEquals(1, posLeft.index);
    }

    @Test
    void testRelativePositionWithUndo() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("");
        ytext.insert(0, "hello world");
        RelativePosition rpos = RelativePosition.createRelativePositionFromTypeIndex(ytext, 1L, null);
        UndoManager um = new UndoManager(ytext, new UndoManagerOptions());
        ytext.delete(0, 6);
        RelativePosition.AbsolutePosition posRight = RelativePosition.createAbsolutePositionFromRelativePosition(rpos, ydoc, null);
        Assertions.assertNotNull(posRight);
        Assertions.assertEquals(0, posRight.index);
        um.undo();
        RelativePosition.AbsolutePosition posLeft = RelativePosition.createAbsolutePositionFromRelativePosition(rpos, ydoc, null);
        Assertions.assertNotNull(posLeft);
        Assertions.assertEquals(1, posLeft.index);
        RelativePosition.AbsolutePosition posWithoutFollow = RelativePosition.createAbsolutePositionFromRelativePosition(rpos, ydoc, false);
        System.out.println(posWithoutFollow);

        RelativePosition.AbsolutePosition position = RelativePosition.createAbsolutePositionFromRelativePosition(rpos, ydoc, false);
        Assertions.assertNotNull(position);
        Assertions.assertEquals(6, position.index);
        Doc ydocClone = new Doc();
        EncodingUtil.applyUpdate(ydocClone, EncodingUtil.encodeStateAsUpdate(ydoc, null));
        RelativePosition.AbsolutePosition posClone = RelativePosition.createAbsolutePositionFromRelativePosition(rpos, ydocClone, null);
        Assertions.assertNotNull(posClone);
        Assertions.assertEquals(6, posClone.index);
        RelativePosition.AbsolutePosition posClone1 = RelativePosition.createAbsolutePositionFromRelativePosition(rpos, ydocClone, false);
        Assertions.assertNotNull(posClone1);
        Assertions.assertEquals(6, posClone1.index);

    }
}
