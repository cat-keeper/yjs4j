package com.triibiotech.yjs.websocket.handler;
/**
 * @author zbs
 * @date 2025/10/28 13:20
 **/
public record CollaboratorChangeEvent(Integer userId, Boolean editable, Boolean viewable,
                                      Boolean collaboration, Boolean refresh) {

    public CollaboratorChangeEvent(Integer userId, Boolean editable, Boolean viewable, Boolean collaboration) {
        this(userId, editable, viewable, collaboration, false);
    }
}
