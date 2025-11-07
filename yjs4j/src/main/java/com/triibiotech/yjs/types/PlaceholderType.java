package com.triibiotech.yjs.types;

import com.triibiotech.yjs.utils.event.YEvent;

/**
 * @author zbs
 * @date 2025/10/27 17:18
 **/
public class PlaceholderType extends AbstractType<YEvent<PlaceholderType>> {
    public Object prelimContent;
    public Object pending;
    public Object hasFormatting = false;
    public Object nodeName;
    public Object prelimAttrs;
    public Object hookName;

    public Object toJson() {
        return "";
    }
}
