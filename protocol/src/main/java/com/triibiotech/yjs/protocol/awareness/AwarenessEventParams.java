package com.triibiotech.yjs.protocol.awareness;

import java.util.Collections;
import java.util.List;

/**
 * Awareness event data
 *
 * @author zbs
 * @date 2025/08/01  16:12:39
 */
public class AwarenessEventParams {
    public final List<Long> added;
    public final List<Long> updated;
    public final List<Long> removed;
    public final String origin;

    public AwarenessEventParams(List<Long> added, List<Long> updated, List<Long> removed, String origin) {
        this.added = added == null ? Collections.emptyList() : added;
        this.updated = updated == null? Collections.emptyList() : updated;
        this.removed = removed == null? Collections.emptyList() : removed;
        this.origin = origin == null? "" : origin;
    }
}
