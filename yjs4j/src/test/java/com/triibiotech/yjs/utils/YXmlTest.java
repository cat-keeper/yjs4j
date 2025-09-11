package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.types.YMap;
import com.triibiotech.yjs.types.YXmlElement;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * @author zbs
 * @date 2025/9/11 13:53
 **/
public class YXmlTest {
    @Test
    void testCustomTypings() {
        Doc ydoc = new Doc();
        YMap<YXmlElement> ymap = ydoc.getMap("");
        YXmlElement yxml = ymap.set("yxml", new YXmlElement("test"));
        Object num = yxml.getAttribute("num");
        Object str = yxml.getAttribute("str");
        Object dtrn = yxml.getAttribute("dtrn");
        Map<String, Object> attrs = yxml.getAttributes(null);
        System.out.println(num);
        System.out.println(str);
        System.out.println(dtrn);
        System.out.println(attrs);
    }


    @Test
    void testSetProperty() {
        Doc ydoc = new Doc();




    }

}
