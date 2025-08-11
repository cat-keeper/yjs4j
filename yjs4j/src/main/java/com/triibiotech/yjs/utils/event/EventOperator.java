package com.triibiotech.yjs.utils.event;

import java.util.Objects;

/**
 * @author zbs
 * @date 2025/7/31 10:06
 **/
public class EventOperator {
    public Object insert;
    public Long retain;
    public Long delete;
    public Object attributes;
    public Boolean isInsertDefined;
    public Boolean isRetainDefined;
    public Boolean isDeleteDefined;
    public Boolean isAttributesDefined;

    public EventOperator() {

    }

    public Object getInsert() {
        return insert;
    }

    public void setInsert(Object insert) {
        this.insert = insert;
    }

    public Long getRetain() {
        return retain;
    }

    public void setRetain(Long retain) {
        this.retain = retain;
    }

    public Long getDelete() {
        return delete;
    }

    public void setDelete(Long delete) {
        this.delete = delete;
    }

    public Object getAttributes() {
        return attributes;
    }

    public void setAttributes(Object attributes) {
        this.attributes = attributes;
    }

    public Boolean isInsertDefined() {
        return isInsertDefined;
    }

    public void setInsertDefined(boolean insertDefined) {
        isInsertDefined = insertDefined;
    }

    public Boolean isRetainDefined() {
        return isRetainDefined;
    }

    public void setRetainDefined(boolean retainDefined) {
        isRetainDefined = retainDefined;
    }

    public Boolean isDeleteDefined() {
        return isDeleteDefined;
    }

    public void setDeleteDefined(boolean deleteDefined) {
        isDeleteDefined = deleteDefined;
    }

    public Boolean isAttributesDefined() {
        return isAttributesDefined;
    }

    public void setAttributesDefined(boolean attributesDefined) {
        isAttributesDefined = attributesDefined;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        EventOperator that = (EventOperator) o;
        return Objects.equals(insert, that.insert) && Objects.equals(retain, that.retain) && Objects.equals(delete, that.delete) && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(insert, retain, delete, attributes);
    }
}
