package com.triibiotech.yjs.utils;

import com.triibiotech.yjs.structs.Item;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * @author zbs
 * @date 2025/7/31 17:27
 **/
public class UndoManagerOptions {
    /**
     * 事务之间的捕获间隔，单位：毫秒（默认 500ms）
     */
    private long captureTimeout = 500;
    /**
     * 是否捕获对所有子文档的更改（默认 true）
     */
    private Function<Transaction, Boolean> captureTransaction = transaction -> true;
    /**
     * 删除筛选器
     */
    private Function<Item, Boolean> deleteFilter = transaction -> true;
    /**
     * 要追踪的类型集合（例如 YText、YArray 等）
     */
    private Set<Object> trackedOrigins = new LinkedHashSet<>();
    /**
     * 忽略远程更改
     */
    private boolean ignoreRemoteMapChanges = false;


    /**
     * 是否跟踪所有更改（默认 true）
     */
    private boolean track = true;

    public UndoManagerOptions() {
        this.trackedOrigins.add(null);
    }

    public long getCaptureTimeout() {
        return captureTimeout;
    }

    public void setCaptureTimeout(long captureTimeout) {
        this.captureTimeout = captureTimeout;
    }

    public Function<Transaction, Boolean> getCaptureTransaction() {
        return captureTransaction;
    }

    public void setCaptureTransaction(Function<Transaction, Boolean> captureTransaction) {
        this.captureTransaction = captureTransaction;
    }

    public Function<Item, Boolean> getDeleteFilter() {
        return deleteFilter;
    }

    public void setDeleteFilter(Function<Item, Boolean> deleteFilter) {
        this.deleteFilter = deleteFilter;
    }

    public Set<Object> getTrackedOrigins() {
        return trackedOrigins;
    }

    public void setTrackedOrigins(Set<Object> trackedOrigins) {
        this.trackedOrigins = trackedOrigins;
    }

    public boolean isIgnoreRemoteMapChanges() {
        return ignoreRemoteMapChanges;
    }

    public void setIgnoreRemoteMapChanges(boolean ignoreRemoteMapChanges) {
        this.ignoreRemoteMapChanges = ignoreRemoteMapChanges;
    }

    public boolean isTrack() {
        return track;
    }

    public void setTrack(boolean track) {
        this.track = track;
    }
}
