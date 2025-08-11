package com.triibiotech.yjs.types;

import com.triibiotech.yjs.structs.*;
import com.triibiotech.yjs.utils.*;
import com.triibiotech.yjs.utils.encoding.EncodingUtil;
import com.triibiotech.yjs.utils.event.YEvent;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * Abstract base class for all Yjs types
 *
 * @author zbs
 * @date 2025/07/29  09:17:12
 */
@SuppressWarnings("unused")
public class AbstractType<EventType extends YEvent<?>> {

    /**
     * The item that contains this type (if it's nested)
     */
    protected Item item = null;

    /**
     * Map of key-value pairs for map-like types
     */
    protected Map<String, Item> map = new HashMap<>();

    /**
     * The first item in the list for array-like types
     */
    protected Item start = null;

    /**
     * The document this type belongs to
     */
    public Doc doc = null;

    /**
     * The length of this type
     */
    protected long length = 0;

    /**
     * Event handlers
     */
    protected EventHandler<EventType, Transaction> eventHandler = EventHandler.createEventHandler();

    /**
     * Deep event handlers
     */
    protected EventHandler<List<YEvent<?>>, Transaction> deepEventHandler = EventHandler.createEventHandler();

    /**
     * Search markers for efficient indexing
     */
    protected List<ArraySearchMarker> searchMarker = null;


    public AbstractType() {
        // Initialize empty type
    }

    /**
     * Get the parent type
     */
    public AbstractType<?> getParent() {
        return item != null ? (AbstractType<?>) item.parent : null;
    }

    public Doc getDoc() {
        return doc;
    }

    /**
     * Get the length of this type
     */
    public long getLength() {
        return length;
    }

    // Getters and setters
    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Map<String, Item> getMap() {
        return map;
    }

    public void setMap(Map<String, Item> map) {
        this.map = map;
    }

    public Item getStart() {
        return start;
    }

    public EventHandler<EventType, Transaction> getEventHandler() {
        return eventHandler;
    }

    public EventHandler<List<YEvent<?>>, Transaction> getDeepEventHandler() {
        return deepEventHandler;
    }

    public void setStart(Item start) {
        this.start = start;
    }

    public Doc getDocument() {
        return doc;
    }

    public void setDocument(Doc doc) {
        this.doc = doc;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public List<ArraySearchMarker> getSearchMarker() {
        return searchMarker;
    }

    public void setSearchMarker(List<ArraySearchMarker> searchMarker) {
        this.searchMarker = searchMarker;
    }

    static void logDocInvalidAccess() {
        EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
    }
    /**
     * Accumulate all (list) children of a type and return them as an Array.
     *
     */
    public static List<Item> getTypeChildren(AbstractType<?> type) {
        if (type.doc == null) {
            logDocInvalidAccess();
        }
        Item s = type.start;
        List<Item> arr = new ArrayList<>();
        while (s != null) {
            arr.add(s);
            s = (Item) s.right;
        }
        return arr;
    }

    /**
     * Call event listeners with an event. This will also add an event to all
     * parents (for `.observeDeep` handlers).
     */
    public void callTypeObservers(Transaction transaction, EventType event) {
        AbstractType<?> changedType = this;
        Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes = transaction.changedParentTypes;
        while (true) {
            changedParentTypes.computeIfAbsent(changedType, t -> new ArrayList<>()).add(event);
            if (changedType.item == null) {
                break;
            }
            changedType = (AbstractType<?>) changedType.item.parent;
        }
        EventHandler.callEventHandlerListeners(this.eventHandler, event, transaction);
    }

    /**
     * Integrate this type into the document
     */
    public void integrate(Doc y, Item item) {
        this.doc = y;
        this.item = item;
    }


    /**
     * Create a copy of this type
     */
    public AbstractType<?> copy() {
        return null;
    }

    /**
     * Clone this type
     */
    @Override
    public AbstractType<?> clone() {
        return null;
    }

    /**
     * Write this type to an encoder
     */
    public void write(DSEncoder encoder) {
        return;
    }

    public void copyPropertiesTo(AbstractType<?> other) {
        other.item = this.item;
        other.map = this.map;
        other.start = this.start;
        other.doc = this.doc;
        other.length = this.length;
        other.searchMarker = this.searchMarker;
    }

    /**
     * Get the first non-deleted item
     */
    public Item getFirst() {
        Item n = start;
        while (n != null && n.isDeleted()) {
            n = (Item) n.right;
        }
        return n;
    }

    /**
     * Call observers for this type
     */
    public void callObserver(Transaction transaction, Set<String> parentSubs) {
        if (!transaction.local && this.searchMarker != null) {
            this.searchMarker.clear();
        }
    }

    /**
     * Observe events on this type
     */
    public void observe(EventHandler.Function<EventType, Transaction> f) {
        this.eventHandler.addEventHandlerListener(f);
    }

    /**
     * Observe deep events on this type and its children
     */
    public void observeDeep(EventHandler.Function<List<YEvent<?>>, Transaction> f) {
        this.deepEventHandler.addEventHandlerListener(f);
    }

    /**
     * Unregister an observer
     */
    public void unobserve(EventHandler.Function<EventType, Transaction> f) {
        this.eventHandler.removeEventHandlerListener(f);
    }

    /**
     * Unregister a deep observer
     */
    public void unobserveDeep(EventHandler.Function<List<YEvent<?>>, Transaction> f) {
        this.deepEventHandler.removeEventHandlerListener(f);
    }

    /**
     * Convert to JSON
     */
    public Object toJson() {
        return null;
    }

    /**
     * 切片方法，返回指定范围内的元素
     *
     * @param type  输入类型
     * @param start 起始位置
     * @param end   结束位置
     * @return 包含指定范围内元素的 List
     */
    public static List<Object> typeListSlice(AbstractType<?> type, long start, long end) {
        if (type.doc == null) {
            logDocInvalidAccess();
        }
        // 负索引转换
        if (start < 0) {
            start = type.length + start;
        }
        if (end < 0) {
            end = type.length + end;
        }
        var len = end - start;
        List<Object> cs = new ArrayList<>();
        var n = type.start;
        // 遍历节点，提取元素
        while (n != null && len > 0) {
            if (n.countable() && !n.isDeleted()) {
                Object[] c = n.content.getContent();
                if (c.length <= start) {
                    start -= c.length;
                } else {
                    for (long i = start; i < c.length && len > 0; i++) {
                        cs.add(c[Math.toIntExact(i)]);
                        len--;
                    }
                    start = 0;
                }
            }
            n = (Item) n.right;
        }
        return cs;
    }

    /**
     * 将类型列表转换为一个数组
     *
     * @param type 输入的 AbstractType 类型
     * @return 返回类型列表转换后的 ArrayList
     */
    public static List<Object> typeListToArray(AbstractType<?> type) {
        if (type.doc == null) {
            logDocInvalidAccess();
        }
        List<Object> cs = new ArrayList<>();
        var n = type.start;
        // 遍历节点，提取内容并添加到数组中
        while (n != null) {
            if (n.countable() && !n.isDeleted()) {
                Object[] c = n.content.getContent();
                cs.addAll(List.of(c));
            }
            n = (Item) n.right;
        }

        return cs;
    }


    public static List<Object> typeListToArraySnapshot(AbstractType<?> type, Snapshot snapshot) {
        List<Object> cs = new ArrayList<>();
        Item n = type.getStart();
        while (n != null) {
            if (n.countable() && Snapshot.isVisible(n, snapshot)) {
                Object[] c = n.content.getContent();
                cs.addAll(Arrays.asList(c));
            }
            n = (Item) n.right;
        }
        return cs;
    }

    public static void typeListForEach(AbstractType<?> type, TriConsumer<Object, Integer, AbstractType<?>> consumer) {
        if (type.doc == null) {
            throw new IllegalStateException("Type accessed before being integrated into a document.");
        }

        int index = 0;
        Item n = type.getStart();

        while (n != null) {
            if (n.countable() && !n.isDeleted()) {
                Object[] content = n.content.getContent();
                for (Object c : content) {
                    consumer.accept(c, index++, type);
                }
            }
            n = (Item) n.right;
        }
    }

    public static <R> List<R> typeListMap(AbstractType<?> type, TriFunction<Object, Integer, AbstractType<?>, R> mapper) {
        if (type.doc == null) {
            throw new IllegalStateException("Type accessed before being integrated into a document.");
        }

        List<R> result = new ArrayList<>();
        typeListForEach(type, (c, index, type1) ->
                result.add(mapper.apply(c, index, type1))
        );
        return result;
    }

    // 定义迭代器类
    public static class TypeListIterator implements Iterator<Object> {
        private Item n;
        private Object[] currentContent = null;
        private int currentContentIndex = 0;

        public TypeListIterator(AbstractType<?> type) {
            this.n = type.start;
        }

        @Override
        public boolean hasNext() {
            // 找到有效内容
            if (currentContent == null) {
                while (n != null && n.isDeleted()) {
                    n = (Item) n.right;
                }
                // 如果没有内容了
                if (n == null) {
                    return false;
                }
                // 找到了有效的节点，初始化 currentContent
                currentContent = n.content.getContent();
                currentContentIndex = 0;
                n = (Item) n.right; // 移动到下一个节点
            }
            return currentContentIndex < currentContent.length;
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            // 返回当前值
            Object value = currentContent[currentContentIndex++];
            // 检查是否需要清空 currentContent
            if (currentContentIndex >= currentContent.length) {
                currentContent = null;
            }
            return value;
        }
    }

    // 类型列表的迭代器方法
    public static Iterator<Object> typeListCreateIterator(AbstractType<?> type) {
        return new TypeListIterator(type);
    }


    @FunctionalInterface
    public interface Function<any, number extends Number, abstractType extends AbstractType<?>> {
        void accept(any arg0, number arg1, abstractType arg2);
    }

    public static void typeListForEachSnapshot(AbstractType<?> type, Function<Object, Long, AbstractType<?>> f, Snapshot snapshot) {
        long index = 0;
        var n = type.start;

        // 遍历节点
        while (n != null) {
            if (n.countable() && Snapshot.isVisible(n, snapshot)) {
                Object[] c = n.content.getContent();
                for (Object o : c) {
                    f.accept(o, index++, type);
                }
            }
            n = (Item) n.right;
        }
    }

    /**
     * 获取指定索引位置的元素
     *
     * @param type  输入的 AbstractType 类型
     * @param index 索引位置
     * @return 返回索引位置的元素
     */
    public static Object typeListGet(AbstractType<?> type, long index) {
        if (type.doc == null) {
            logDocInvalidAccess();
        }

        ArraySearchMarker marker = ArraySearchMarker.findMarker(type, index);
        var n = type.start;
        // 如果找到了标记，调整遍历节点的起始位置
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
        }

        // 遍历节点，找到指定的元素
        while (n != null) {
            if (!n.isDeleted() && n.countable()) {
                if (index < n.length) {
                    // 返回指定索引的元素
                    return n.content.getContent()[Math.toIntExact(index)];
                }
                index -= n.length;
            }
            n = (Item) n.right;
        }

        return null;
    }


    /**
     * Get subdocuments contained in this type
     */
    public java.util.LinkedHashSet<Doc> getSubDocs() {
        java.util.LinkedHashSet<Doc> subDocs = new java.util.LinkedHashSet<>();

        // Iterate through all items to find subdocuments
        Item item = start;
        while (item != null) {
            if (!item.isDeleted() && item.content instanceof ContentDoc contentDoc) {
                subDocs.add(contentDoc.getDoc());
            }
            item = (Item) item.right;
        }

        return subDocs;
    }


    public static <EventType extends YEvent<?>> void callTypeObservers(AbstractType<EventType> type, Transaction transaction, EventType event) {
        AbstractType<EventType> changedType = type;
        Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes = transaction.changedParentTypes;

        while (true) {
            // 把事件加入到 changedParentTypes 中，按 type 作为 key
            changedParentTypes
                    .computeIfAbsent(type, k -> new ArrayList<>())
                    .add(event);

            Item item = type.getItem();
            if (item == null) {
                break;
            }

            // 注意 parent 是 AbstractType 类型
            type = (AbstractType<EventType>) item.parent;
        }
        EventHandler.callEventHandlerListeners(changedType.eventHandler, event, transaction);
    }


    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }


    @FunctionalInterface
    public interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
    }


    public static void typeListInsertGenerics(Transaction transaction, AbstractType<?> parent, long index, Object... content) {
        if (index > parent.length) {
            throw new RuntimeException("length of parent type is less than index");
        }
        if (index == 0) {
            if (parent.searchMarker != null) {
                ArraySearchMarker.updateMarkerChanges(parent.searchMarker, index, content.length);
            }
            typeListInsertGenericsAfter(transaction, parent, null, content);
            return;
        }
        long startIndex = index;
        ArraySearchMarker marker = ArraySearchMarker.findMarker(parent, index);
        Item n = parent.start;
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
            if (index == 0) {
                n = n.getPrev();
                index += (n != null && n.countable() && !n.isDeleted()) ? n.length : 0;
            }
        }
        for (; n != null; n = (Item) n.right) {
            if (!n.isDeleted() && n.countable()) {
                if (index <= n.length) {
                    if (index < n.length) {
                        // insert in-between
                        StructStore.getItemCleanStart(transaction, ID.createId(n.id.client, n.id.clock + index));
                    }
                    break;
                }
                index -= n.length;
            }
        }
        if (parent.searchMarker != null) {
            ArraySearchMarker.updateMarkerChanges(parent.searchMarker, startIndex, content.length);
        }
        typeListInsertGenericsAfter(transaction, parent, n, content);
    }

    public static class ItemRef {
        public Item value;
    }

    public static void typeListInsertGenericsAfter(Transaction transaction, AbstractType<?> parent, Item referenceItem, Object... content) {
        ItemRef leftRef = new ItemRef();
        leftRef.value = referenceItem;

        Doc doc = transaction.doc;
        long ownClientId = doc.clientId;
        StructStore store = doc.store;
        Item right = referenceItem == null ? parent.start : (Item) referenceItem.right;
        List<Object> jsonContent = new ArrayList<>();
        for (Object c : content) {
            if (c == null || c instanceof Number || c instanceof Boolean || c instanceof String || c instanceof Map || c instanceof List) {
                jsonContent.add(c);
            } else {
                packJsonContent(leftRef, jsonContent, right, parent, transaction, ownClientId, store);
                AbstractContent contentStruct;
                if (c instanceof byte[] || c instanceof ByteBuffer) {
                    contentStruct = new ContentBinary(c instanceof byte[] ? (byte[]) c : ((ByteBuffer) c).array());
                } else if (c instanceof Doc) {
                    contentStruct = new ContentDoc((Doc) c);
                } else if (c instanceof AbstractType) {
                    contentStruct = new ContentType((AbstractType<?>) c);
                } else {
                    throw new IllegalArgumentException("Unexpected content type in insert operation: " + c.getClass());
                }

                ID id = ID.createId(ownClientId, store.getState(ownClientId));
                leftRef.value = new Item(id,
                        leftRef.value,
                        leftRef.value != null ? leftRef.value.getLastId() : null,
                        right,
                        right != null ? right.id : null,
                        parent,
                        null,
                        contentStruct);
                leftRef.value.integrate(transaction, 0);
            }
        }
        packJsonContent(leftRef, jsonContent, right, parent, transaction, ownClientId, store);
    }

    private static void packJsonContent(ItemRef leftRef, List<Object> jsonContent, Item right,
                                        AbstractType<?> parent, Transaction txn, long clientId, StructStore store) {
        if (!jsonContent.isEmpty()) {
            ID id = ID.createId(clientId, store.getState(clientId));
            ContentAny contentAny = new ContentAny(jsonContent.toArray());
            leftRef.value = new Item(id,
                    leftRef.value,
                    leftRef.value != null ? leftRef.value.getLastId() : null,
                    right,
                    right != null ? right.id : null,
                    parent,
                    null,
                    contentAny);
            leftRef.value.integrate(txn, 0);
            jsonContent.clear();
        }
    }

    /**
     * 在父节点的最后插入内容
     *
     * @param transaction 当前事务
     * @param parent      父节点
     * @param content     要插入的内容
     * @return 插入操作的结果
     */
    public static Object typeListPushGenerics(Transaction transaction, AbstractType<?> parent, Object... content) {
        List<ArraySearchMarker> markers = parent.searchMarker == null ? new ArrayList<>() : parent.searchMarker;
        Item n = parent.getStart();

        // 找到具有最大 index 的 searchMarker
        if (!markers.isEmpty()) {
            ArraySearchMarker maxMarker = null;
            for (ArraySearchMarker marker : markers) {
                if (maxMarker == null || marker.index > maxMarker.index) {
                    maxMarker = marker;
                }
            }
            if (maxMarker != null && maxMarker.p != null) {
                n = maxMarker.p;
            }
        }

        // 遍历节点，找到最后一个节点
        // 移动到链表最右端（即最后一个 Item）
        while (n != null && n.getRight() != null) {
            n = (Item) n.getRight();
        }
        // 执行插入操作
        typeListInsertGenericsAfter(transaction, parent, n, content);
        return null;
    }

    /**
     * 删除指定范围内的元素
     *
     * @param transaction 当前事务
     * @param parent      父节点
     * @param index       删除起始索引
     * @param length      删除的长度
     */
    public static void typeListDelete(Transaction transaction, AbstractType<?> parent, long index, long length) {
        if (length == 0) {
            return;
        }
        long startIndex = index;
        long startLength = length;
        ArraySearchMarker marker = ArraySearchMarker.findMarker(parent, index);
        Item n = parent.start;

        // 调整起始节点
        if (marker != null) {
            n = marker.p;
            index -= marker.index;
        }

        // 计算并删除第一个要删除的元素
        while (n != null && index > 0) {
            if (!n.isDeleted() && n.countable()) {
                if (index < n.length) {
                    StructStore.getItemCleanStart(transaction, ID.createId(n.id.client, n.id.clock + index));
                }
                index -= n.length;
            }
            n = (Item) n.right;
        }

        // 删除所有符合条件的元素
        while (length > 0 && n != null) {
            if (!n.isDeleted()) {
                if (length < n.length) {
                    StructStore.getItemCleanStart(transaction, ID.createId(n.id.client, n.id.clock + length));
                }
                n.delete(transaction);
                length -= n.length;
            }
            n = (Item) n.right;
        }

        // 如果长度超过了可删除范围，抛出异常
        if (length > 0) {
            throw new RuntimeException("Length exceeded!");
        }

        // 更新标记变化
        if (parent.searchMarker != null) {
            ArraySearchMarker.updateMarkerChanges(parent.searchMarker, startIndex, -startLength + length);
        }
    }

    /**
     * 从父类型的映射中删除指定键对应的内容
     *
     * @param transaction 当前事务
     * @param parent      父节点类型
     * @param key         要删除的键
     */
    public static void typeMapDelete(Transaction transaction, AbstractType<?> parent, String key) {
        Item c = parent.map.get(key);
        if (c != null) {
            // 调用 delete 方法删除内容
            c.delete(transaction);
        }
    }

    public static void typeMapSet(Transaction transaction, AbstractType<?> parent, String key, Object value) {
        Item left = parent.getMap().get(key);
        Doc doc = transaction.getDoc();
        long ownClientId = doc.getClientId();
        AbstractContent content;

        if (value == null) {
            content = new ContentAny(new Object[]{null});
        } else if (value instanceof Number
                || value instanceof Boolean
                || value instanceof String
                || value instanceof Date
                || value instanceof Map<?, ?>
                || value instanceof List<?>) {
            content = new ContentAny(new Object[]{value});
        } else if (value instanceof byte[]) {
            content = new ContentBinary((byte[]) value);
        } else if (value instanceof Doc) {
            content = new ContentDoc((Doc) value);
        } else if (value instanceof AbstractType) {
            content = new ContentType((AbstractType<?>) value);
        } else {
            throw new IllegalArgumentException("Unexpected content type: " + value.getClass());
        }

        ID id = ID.createId(ownClientId, doc.getStore().getState(ownClientId));

        Item item = new Item(
                id,
                left,
                left != null ? left.getLastId() : null,
                null,
                null,
                parent,
                key,
                content
        );

        item.integrate(transaction, 0);
    }

    /**
     * 获取 Yjs 类型中的 key 对应的值。
     *
     * @param parent AbstractType 对象
     * @param key    要获取的键
     * @return 返回最后一项的内容，如果不存在或已删除，返回 null
     */
    public static Object typeMapGet(AbstractType<?> parent, String key) {
        // 检查文档是否加载
        if (parent.getDoc() == null) {
            logDocInvalidAccess();
        }

        Item item = parent.getMap().get(key);
        if (item != null && !item.isDeleted()) {
            Object[] contentList = item.getContent().getContent();
            // 返回最新值
            return contentList[contentList.length - 1];
        }
        return null;
    }

    public static Map<String, Object> typeMapGetAll(AbstractType<?> parent) {
        if (parent.getDoc() == null) {
            logDocInvalidAccess();
        }

        Map<String, Object> res = new HashMap<>();
        for (Map.Entry<String, Item> entry : parent.getMap().entrySet()) {
            Item value = entry.getValue();
            if (!value.isDeleted()) {
                Object[] content = value.getContent().getContent();
                res.put(entry.getKey(), content[content.length - 1]);
            }
        }
        return res;
    }

    public static boolean typeMapHas(AbstractType<?> parent, String key) {
        if (parent.getDoc() == null) {
            logDocInvalidAccess();
        }

        Item val = parent.getMap().get(key);
        return val != null && !val.isDeleted();
    }

    public static Object typeMapGetSnapshot(AbstractType<?> parent, String key, Snapshot snapshot) {
        Item v = parent.getMap().getOrDefault(key, null);

        while (v != null && (!snapshot.sv.containsKey(v.id.client) || v.id.clock >= snapshot.sv.getOrDefault(v.id.client, 0L))) {
            v = (Item) v.left;
        }

        if (v != null && Snapshot.isVisible(v, snapshot)) {
            Object[] content = v.getContent().getContent();
            return content[content.length - 1];
        }
        return null;
    }

    public static Map<String, Object> typeMapGetAllSnapshot(AbstractType<?> parent, Snapshot snapshot) {
        Map<String, Object> res = new HashMap<>();

        for (Map.Entry<String, Item> entry : parent.getMap().entrySet()) {
            String key = entry.getKey();
            Item v = entry.getValue();

            while (v != null && (!snapshot.sv.containsKey(v.id.client) || v.id.clock >= snapshot.sv.getOrDefault(v.id.client, 0L))) {
                v = (Item) v.left;
            }

            if (v != null && Snapshot.isVisible(v, snapshot)) {
                Object[] content = v.getContent().getContent();
                res.put(key, content[content.length - 1]);
            }
        }

        return res;
    }


    public static Iterator<Map.Entry<String, Item>> createMapIterator(AbstractType<?> type) {
        if (type.getDoc() == null) {
            logDocInvalidAccess();
        }

        return type.getMap().entrySet().stream()
                .filter(entry -> !entry.getValue().isDeleted())
                .iterator();
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractType<?> that = (AbstractType<?>) o;
        return length == that.length && Objects.equals(item, that.item) && Objects.equals(map, that.map) && Objects.equals(start, that.start) && Objects.equals(doc, that.doc) && Objects.equals(eventHandler, that.eventHandler) && Objects.equals(deepEventHandler, that.deepEventHandler) && Objects.equals(searchMarker, that.searchMarker);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
