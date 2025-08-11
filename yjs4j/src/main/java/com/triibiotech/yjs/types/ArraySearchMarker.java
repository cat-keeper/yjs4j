package com.triibiotech.yjs.types;

import com.triibiotech.yjs.structs.Item;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author zbs
 * @date 2025/7/30 23:01
 **/
public class ArraySearchMarker {
    public Item p;
    public long index;
    public int timestamp;

    /**
     * A unique timestamp that identifies each marker.
     * Time is relative,.. this is more like an ever-increasing clock.
     *
     */
    static int globalSearchMarkerTimestamp = 0;

    static final int MAX_SEARCH_MARKER = 80;


    public ArraySearchMarker(Item p, long index) {
        p.setMarker(true);
        this.p = p;
        this.index = index;
        this.timestamp = globalSearchMarkerTimestamp++;
    }

    public static void refreshMarkerTimestamp(ArraySearchMarker marker) {
        marker.timestamp = globalSearchMarkerTimestamp++;
    }

    /**
     * This is rather complex so this function is the only thing that should overwrite a marker
     *
     */
    public static void  overwriteMarker(ArraySearchMarker marker, Item p, long index) {
        marker.p.setMarker(false);
        marker.p = p;
        p.setMarker(true);
        marker.index = index;
        marker.timestamp = globalSearchMarkerTimestamp++;
    }

    public static ArraySearchMarker markPosition(List<ArraySearchMarker> searchMarker, Item p, long index) {
        if (searchMarker.size() >= MAX_SEARCH_MARKER) {
            // 找到 timestamp 最小（最旧）的 marker，覆盖它
            ArraySearchMarker oldest = Collections.min(searchMarker, Comparator.comparingLong(m -> m.timestamp));
            ArraySearchMarker.overwriteMarker(oldest, p, index);
            return oldest;
        } else {
            // 创建新 marker
            ArraySearchMarker pm = new ArraySearchMarker(p, index);
            searchMarker.add(pm);
            return pm;
        }
    }


    /**
     * Find a marker for efficient array access
     */
    public static ArraySearchMarker findMarker(AbstractType<?> yArray, long index) {
        if (yArray.getStart() == null || index == 0 || yArray.getSearchMarker() == null || yArray.getSearchMarker().isEmpty()) {
            return null;
        }

        // 找到距离 index 最近的 marker
        ArraySearchMarker marker = null;
        if (!yArray.getSearchMarker().isEmpty()) {
            marker = Collections.min(yArray.getSearchMarker(), Comparator.comparingInt(
                    m -> Math.toIntExact(Math.abs(index - m.index))
            ));
        }
        Item p = yArray.getStart();
        long pindex = 0;
        if (marker != null) {
            p = marker.p;
            pindex = marker.index;
            // we used it, we might need to use it again
            ArraySearchMarker.refreshMarkerTimestamp(marker);
        }

        // 向右遍历直到接近 index
        while (p.right != null && pindex < index) {
            if (!p.isDeleted() && p.countable()) {
                if (index < pindex + p.length) {
                    break;
                }
                pindex += p.length;
            }
            p = (Item) p.right;
        }

        // 向左调整（如果 overshoot）
        while (p.left != null && pindex > index) {
            p = (Item) p.left;
            if (!p.isDeleted() && p.countable()) {
                pindex -= p.length;
            }
        }

        // 确保不能与左侧合并（避免逻辑错误）
        while (p.left != null &&
                p.left.id.client == p.id.client &&
                (p.left.id.clock + p.left.length == p.id.clock)) {
            p = (Item) p.left;
            if (!p.isDeleted() && p.countable()) {
                pindex -= p.length;
            }
        }

        // 判断是否使用旧 marker
        if (p.parent instanceof YArray<?> parent) {
            if (marker != null && Math.abs(marker.index - pindex) < (parent.getLength() / MAX_SEARCH_MARKER)) {
                overwriteMarker(marker, p, pindex);
                return marker;
            } else {
                return markPosition(yArray.getSearchMarker(), p, pindex);
            }
        }
        if (p.parent instanceof YText parent) {
            if (marker != null && Math.abs(marker.index - pindex) < (parent.getLength() / MAX_SEARCH_MARKER)) {
                ArraySearchMarker.overwriteMarker(marker, p, pindex);
                return marker;
            } else {
                return markPosition(yArray.getSearchMarker(), p, pindex);
            }
        }
        return null;
    }


    /**
     * Update markers when a change happened.
     * This should be called before doing a deletion!
     *
     * @param searchMarkers {Array<ArraySearchMarker>} searchMarker
     * @param index         {number} index
     * @param len           {number} len If insertion, len is positive. If deletion, len is negative.
     */
    public static void updateMarkerChanges(List<ArraySearchMarker> searchMarkers, long index, long len) {
        for (int i = searchMarkers.size() - 1; i >= 0; i--) {
            ArraySearchMarker m = searchMarkers.get(i);

            if (len > 0) {
                Item p = m.p;
                p.setMarker(false);

                // 回退找到上一个未删除且 countable 的 item
                while (p != null && (p.isDeleted() || !p.countable())) {
                    p = (Item) p.left;
                    if (p != null && !p.isDeleted() && p.countable()) {
                        m.index -= p.length;
                    }
                }

                // 如果无效或 marker 重复，就移除该 marker
                if (p == null || p.marker()) {
                    // remove search marker if updated position is null or if position is already marked
                    searchMarkers.remove(i);
                    continue;
                }

                m.p = p;
                p.setMarker(true);
            }

            // a simple index <= m.index check would actually suffice
            if (index < m.index || (len > 0 && index == m.index)) {
                m.index = Math.max(index, m.index + len);
            }
        }
    }
}
