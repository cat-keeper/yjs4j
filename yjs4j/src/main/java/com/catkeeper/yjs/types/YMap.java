package com.catkeeper.yjs.types;

import com.catkeeper.yjs.structs.ContentType;
import com.catkeeper.yjs.structs.Item;
import com.catkeeper.yjs.utils.DSDecoder;
import com.catkeeper.yjs.utils.DSEncoder;
import com.catkeeper.yjs.utils.Doc;
import com.catkeeper.yjs.utils.Transaction;
import com.catkeeper.yjs.utils.encoding.EncodingUtil;
import com.catkeeper.yjs.utils.event.YMapEvent;

import java.util.*;

/**
 * YMap 是 Yjs 的共享 Map 类型，提供键值对存储，支持嵌套共享类型。
 *
 * <p>YMap 的内部实现与普通 Map 不同：每个 key 对应一个 Item 链表，
 * 只有链表头部（最新的）Item 是有效值，旧值被标记为删除但保留在链表中，
 * 用于处理并发冲突。当两个客户端同时设置同一个 key 时，clientId 更大的胜出。
 *
 * <p>主要操作：
 * <ul>
 *   <li>{@link #set(String, Object)} — 设置键值对</li>
 *   <li>{@link #get(String)} — 获取值</li>
 *   <li>{@link #delete(String)} — 删除键</li>
 *   <li>{@link #has(String)} — 检查键是否存在</li>
 *   <li>{@link #clear()} — 清空所有键值对</li>
 *   <li>{@link #toJson()} — 转为普通 Map</li>
 * </ul>
 *
 * <p>支持嵌套类型：
 * <pre>{@code
 * YMap<Object> map = doc.getMap("config");
 * map.set("name", "Alice");
 * map.set("nested", new YMap<>());  // 嵌套共享类型
 * ((YMap) map.get("nested")).set("key", "value");
 * }</pre>
 *
 * <p>对应 JS 版本的 Y.Map。
 *
 * @author zbs
 * @date 2025/07/29  17:37:42
 */
public class YMap<Type> extends AbstractType<YMapEvent> {

    public Map<String, Type> prelimContent;

    public YMap() {
        super();
        this.prelimContent = new HashMap<>();
    }

    public YMap(Iterable<Map.Entry<String, ? extends Type>> entries) {
        if (entries == null) {
            this.prelimContent = new HashMap<>();
        } else {
            this.prelimContent = new HashMap<>();
            for (Map.Entry<String, ? extends Type> entry : entries) {
                this.prelimContent.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void integrate(Doc y, Item item) {
        super.integrate(y, item);
        this.prelimContent.forEach(this::set);
    }

    @Override
    public YMap<Type> copy() {
        return new YMap<>();
    }

    @Override
    public YMap<Type> clone() {
        YMap<Type> map = new YMap<>();
        this.forEach((value, key, yMap) -> {
            if (value instanceof AbstractType) {
                map.set(key, (Type) ((AbstractType<?>) value).clone());
            } else {
                map.set(key, value);
            }
        });
        return map;
    }

    @Override
    public void callObserver(Transaction transaction, Set<String> parentSubs) {
        callTypeObservers(this, transaction, new YMapEvent(this, transaction, parentSubs));
    }

    @Override
    public Map<String, Object> toJson() {
        if (doc == null) {
            EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
        }
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Item> entry : this.map.entrySet()) {
            Item item = entry.getValue();
            if (!item.isDeleted()) {
                Object[] content = item.content.getContent();
                if (content.length > 0) {
                    Object o = content[Math.toIntExact(item.length - 1)];
                    if(o instanceof AbstractType<?>) {
                        map.put(entry.getKey(), ((AbstractType<?>) o).toJson());
                    } else {
                        map.put(entry.getKey(), o);
                    }
                }
            }
        }
        return map;
    }

    public int getSize() {
        Iterator<Map.Entry<String, Item>> mapIterator = createMapIterator(this);
        int size = 0;
        while (mapIterator.hasNext()) {
            size++;
            mapIterator.next();
        }
        return size;
    }

    public Set<String> keys() {
        Iterator<Map.Entry<String, Item>> mapIterator = createMapIterator(this);
        Set<String> keys = new LinkedHashSet<>();
        while (mapIterator.hasNext()) {
            keys.add(mapIterator.next().getKey());
        }
        return keys;
    }

    public Iterable<Type> values() {
        return () -> new Iterator<>() {
            private final Iterator<Map.Entry<String, Item>> mapIterator = createMapIterator(YMap.this);

            @Override
            public boolean hasNext() {
                return mapIterator.hasNext();
            }

            @Override
            public Type next() {
                Map.Entry<String, Item> entry = mapIterator.next();
                Item item = entry.getValue();

                // 获取最新版本的值
                List<Object> contentList = List.of(item.getContent().getContent());
                long length = item.getLength();
                if (length == 0 || contentList.size() < length) {
                    throw new NoSuchElementException("Invalid item content");
                }

                @SuppressWarnings("unchecked")
                Type value = (Type) contentList.get((int) (length - 1));
                return value;
            }
        };
    }

    public Iterator<Map.Entry<String, Object>> entries() {
        Iterator<Map.Entry<String, Item>> rawIterator = createMapIterator(this);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return rawIterator.hasNext();
            }

            @Override
            public Map.Entry<String, Object> next() {
                Map.Entry<String, Item> entry = rawIterator.next();
                String key = entry.getKey();
                Object value = List.of(entry.getValue().getContent()).get((int) (entry.getValue().getLength() - 1));
                return new AbstractMap.SimpleEntry<>(key, value);
            }
        };
    }

    public void forEach(TriConsumer<Type, String, YMap<Type>> f) {
        if (doc == null) {
            EncodingUtil.log.warn("Invalid access: Add Yjs type to a document before reading data.");
        }
        this.map.forEach((key, item) -> {
            if (!item.isDeleted()) {
                f.accept((Type) item.content.getContent()[(int) item.length - 1], key, this);
            }
        });
    }

    public Iterator<Map.Entry<String, Object>> iterator() {
        return this.entries();
    }

    public void delete(String key) {
        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                typeMapDelete(transaction, this, key);
                return true;
            }, null, true);
        } else {
            this.prelimContent.remove(key);
        }
    }


    public Type set(String key, Type value) {
        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                typeMapSet(transaction, this, key, value);
                return null;
            }, null, true);
        } else {
            this.prelimContent.put(key, value);
        }
        return value;
    }


    public Object get(String key) {
        return typeMapGet(this, key);
    }

    public boolean has(String key) {
        return typeMapHas(this, key);
    }

    public void clear() {
        if (doc != null) {
            Transaction.transact(doc, transaction -> {
                this.forEach((value, key, map) -> {
                    typeMapDelete(transaction, map, key);
                });
                return true;
            }, null, true);
        } else {
            this.prelimContent.clear();
        }
    }

    @Override
    public void write(DSEncoder encoder) {
        encoder.writeTypeRef(ContentType.YMapRefID);
    }

    public static YMap<?> readYMap(DSDecoder decoder) {
        return new YMap<>();
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof YMap<?> yMap)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(prelimContent, yMap.prelimContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), prelimContent);
    }
}
