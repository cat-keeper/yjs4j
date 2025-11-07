package com.triibiotech.yjs.utils;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.triibiotech.yjs.structs.AbstractContent;
import com.triibiotech.yjs.structs.ContentDoc;
import com.triibiotech.yjs.structs.Item;
import com.triibiotech.yjs.types.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A Yjs instance handles the state of shared data.
 *
 * @author zbs
 * @date 2025/07/29  12:04:59
 */
@SuppressWarnings("unused")
public class Doc extends ObservableV2<String> {
    public Boolean gc;
    public Function<Item, Boolean> gcFilter;
    public long clientId;
    public String guid;
    public String collectionid;
    public Map<String, AbstractType<?>> share = new ConcurrentHashMap<>();
    public StructStore store = new StructStore();
    public Transaction transaction = null;
    public List<Transaction> transactionCleanups = new ArrayList<>();
    public Set<Doc> subDocs = new LinkedHashSet<>();
    public Item item = null;
    public boolean shouldLoad;
    public boolean autoLoad;
    public Object meta;
    public boolean isLoaded = false;
    public boolean isSynced = false;
    public boolean isDestroyed = false;

    public CompletableFuture<Doc> whenLoaded;
    public Consumer<Doc> loadListener;

    public CompletableFuture<Void> whenSynced;
    public Consumer<Boolean> syncEventHandler;

    public Doc(String guid, String collectionid, boolean gc, Function<Item, Boolean> gcFilter,
               Object meta, boolean autoLoad, boolean shouldLoad) {
        this.gc = gc;
        this.gcFilter = gcFilter;
        this.guid = guid;
        this.collectionid = collectionid;
        this.meta = meta;
        this.autoLoad = autoLoad;
        this.shouldLoad = shouldLoad;
        this.clientId = generateNewClientId();
    }

    public Doc() {
        this(new DocOptions());
    }

    public Doc(DocOptions opts) {
        this.gc = opts.gc;
        this.gcFilter = opts.gcFilter;
        this.clientId = generateNewClientId();
        this.guid = opts.guid;
        this.collectionid = opts.collectionid;
        this.transaction = null;
        this.item = null;
        this.shouldLoad = opts.shouldLoad;
        this.autoLoad = opts.autoLoad;
        this.meta = opts.meta;
        this.isLoaded = false;
        this.isSynced = false;
        this.isDestroyed = false;
        this.whenLoaded = new CompletableFuture<>();

        // 设置加载监听器
        this.loadListener = doc -> {
            isLoaded = true;
            whenLoaded.complete(doc);
        };

        // 设置同步事件监听器
        this.syncEventHandler = isSynced -> {
            boolean newSyncedState = (isSynced == null) || isSynced;

            // 当连接丢失时，重新创建whenSynced Promise
            if (!newSyncedState && this.isSynced) {
                this.whenSynced = provideSyncedPromise();
            }

            // 更新同步状态
            this.isSynced = newSyncedState;

            // 如果同步完成且文档未加载，则触发加载事件
            if (this.isSynced && !this.isLoaded) {
                this.loadListener.accept(this);
            }
        };
    }

    private CompletableFuture<Void> provideSyncedPromise() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        // 注册事件监听器
        this.on("sync", this::eventHandler);
        return future;
    }

    private void eventHandler(Boolean isSynced) {
        boolean shouldResolve = (isSynced == null) || isSynced;
        if (shouldResolve) {
            // 移除事件监听器
            this.off("sync", this::eventHandler);
            this.whenSynced.complete(null);
        }
    }

    public static long generateNewClientId() {
        return Integer.toUnsignedLong(new Random().nextInt());
    }

    public void load() {
        final Item item = this.item;
        if (item != null && !this.shouldLoad) {
            Transaction.transact(((AbstractType<?>) item.parent).getDocument(), transaction -> {
                transaction.subDocsLoaded.add(this);
                return true;
            }, null, true);
        }
        this.shouldLoad = true;
        this.loadListener.accept(this);
    }

    public Set<Doc> getSubDocs() {
        return this.subDocs;
    }

    public Set<String> getSubDocGuids() {
        Set<String> guids = new LinkedHashSet<>();
        for (Doc doc : this.subDocs) {
            guids.add(doc.guid);
        }
        return guids;
    }

    /**
     * Changes that happen inside of a transaction are bundled. This means that
     * the observer fires _after_ the transaction is finished and that all changes
     * that happened inside of the transaction are sent as one message to the
     * other peers.
     *
     * @param f      The function that should be executed as a transaction
     * @param origin of who started the transaction. Will be stored on transaction.origin
     * @return T
     */
    public <T> T transact(Function<Transaction, T> f, Object origin) {
        return Transaction.transact(this, f, origin, true);
    }

    public <T> T transact(Function<Transaction, T> f) {
        return Transaction.transact(this, f, null, true);
    }

    /**
     * Define a shared data type.
     * Multiple calls of `ydoc.get(name, TypeConstructor)` yield the same result
     * and do not overwrite each other. I.e.
     * `ydoc.get(name, Y.Array) === ydoc.get(name, Y.Array)`
     * After this method is called, the type is also available on `ydoc.share.get(name)`.
     * *Best Practices:*
     * Define all types right after the Y.Doc instance is created and store them in a separate object.
     * Also use the typed methods `getText(name)`, `getArray(name)`, ..
     *
     * @param typeConstructor The constructor of the type definition. E.g. Y.Text, Y.Array, Y.Map, ...
     * @return {InstanceType<Type>} The created type. Constructed with TypeConstructor
     * @example const ydoc = new Y.Doc(..)
     * const appState = {
     * document: ydoc.getText('document')
     * comments: ydoc.getArray('comments')
     * }
     */
    @SuppressWarnings("unchecked")
    public <T extends AbstractType<?>> T get(String name, Class<T> typeConstructor) {
        AbstractType<?> existing = share.get(name);
        if (existing == null) {
            // 首次创建
            if (typeConstructor == null || typeConstructor == (Class<?>) AbstractType.class) {
                existing = new PlaceholderType();
            } else {
                existing = createInstance(typeConstructor);
            }
            existing.integrate(this, null);
            share.put(name, existing);
        } else if (typeConstructor != null && existing instanceof PlaceholderType) {
            if(typeConstructor == (Class<?>) AbstractType.class) {
                return (T) existing;
            }
            // 类型转换：从占位符转为具体类型
            T newInstance = createInstance(typeConstructor);
            BeanUtil.copyProperties(existing, newInstance);
            share.put(name, newInstance);
            existing = newInstance;
        }
        return (T) existing;
    }

    private <T extends AbstractType<?>> T createInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("Failed to instantiate type: " + e);
            throw new RuntimeException("Failed to instantiate type: " + clazz.getSimpleName(), e);
        }
    }

    public <T> YArray<T> getArray(String name) {
        return this.get(name != null ? name : "", YArray.class);
    }

    public YText getText(String name) {
        return this.get(name != null ? name : "", YText.class);
    }


    @SuppressWarnings("unchecked")
    public <T> YMap<T> getMap(String name) {
        return (YMap<T>) this.get(name != null ? name : "", YMap.class);
    }

    public YXmlElement getXmlElement(String name) {
        return this.get(name != null ? name : "", YXmlElement.class);
    }

    public YXmlFragment getXmlFragment(String name) {
        return this.get(name != null ? name : "", YXmlFragment.class);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> doc = new HashMap<>();
        this.share.forEach((key, value) -> {
            doc.put(key, value.toJson());
        });
        return doc;
    }

    public Map<String, Object> getContent() {
        Map<String, Object> doc = new HashMap<>();
        this.share.forEach((key, value) -> {
            Class<? extends AbstractType> aClass = analysisType(value);
            AbstractType type = JSON.parseObject(JSON.toJSONString(value), aClass);
            doc.put(key, type.toJson());
        });
        return doc;
    }


    private static Class<? extends AbstractType> analysisType(Object object) {
        JSONObject jsonObject = JSON.parseObject(JSON.toJSONString(object));
        if (jsonObject.containsKey("map")) {
            return YMap.class;
        }
        if (jsonObject.containsKey("start")) {
            return YText.class;
        }
        if (jsonObject.containsKey("prelimContent") && jsonObject.containsKey("searchMarker")) {
            return YArray.class;
        }
        if (jsonObject.containsKey("prelimContent")) {
            return YXmlFragment.class;
        }
        if (jsonObject.containsKey("nodeName")) {
            return YXmlElement.class;
        }
        if (jsonObject.containsKey("hookName")) {
            return YXmlHook.class;
        }
        return YText.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy() {
        this.isDestroyed = true;
        for (Doc subdoc : new ArrayList<>(this.subDocs)) {
            subdoc.destroy();
        }
        final Item item = this.item;
        if (item != null) {
            this.item = null;
            AbstractContent content = item.content;
            DocOptions opts = new DocOptions();
            opts.guid = this.guid;
            opts.shouldLoad = false;
            Map<String, Object> contentOpts = content instanceof ContentDoc ? ((ContentDoc) content).getOpts() : new HashMap<>();
            if (contentOpts.containsKey("collectionid")) {
                opts.collectionid = contentOpts.get("collectionid").toString();
            }
            if (contentOpts.containsKey("gc")) {
                opts.gc = (boolean) contentOpts.get("gc");
            }
            if (contentOpts.containsKey("gcFilter")) {
                opts.gcFilter = (Function<Item, Boolean>) contentOpts.get("gcFilter");
            }
            if (contentOpts.containsKey("meta")) {
                opts.meta = contentOpts.get("meta");
            }
            if (contentOpts.containsKey("autoLoad")) {
                opts.autoLoad = (boolean) contentOpts.get("autoLoad");
            }
            Doc contentDoc = new Doc(opts);
            contentDoc.item = item;
            content.setDoc(contentDoc);
            Transaction.transact(((AbstractType<?>) item.parent).getDocument(), transaction -> {
                final Doc doc = content.getDoc();
                if (!item.isDeleted()) {
                    transaction.subDocsAdded.add(doc);
                }
                transaction.subDocsRemoved.add(this);
                return true;
            }, null, true);
        }
        this.emit("destroyed", true);
        this.emit("destroy", this);
        super.destroy();
    }

    public StructStore getStore() {
        return store;
    }

    public long getClientId() {
        return clientId;
    }

    public String getGuid() {
        return guid;
    }


    public Transaction getTransaction() {
        return transaction;
    }

    public Map<String, AbstractType<?>> getShare() {
        return share;
    }

    public Boolean getGc() {
        return gc;
    }

    public void setGc(Boolean gc) {
        this.gc = gc;
    }

    public Function<Item, Boolean> getGcFilter() {
        return gcFilter;
    }

    public void setGcFilter(Function<Item, Boolean> gcFilter) {
        this.gcFilter = gcFilter;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public String getCollectionid() {
        return collectionid;
    }

    public void setCollectionid(String collectionid) {
        this.collectionid = collectionid;
    }

    public void setShare(Map<String, AbstractType<?>> share) {
        this.share = share;
    }

    public void setStore(StructStore store) {
        this.store = store;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public List<Transaction> getTransactionCleanups() {
        return transactionCleanups;
    }

    public void setTransactionCleanups(List<Transaction> transactionCleanups) {
        this.transactionCleanups = transactionCleanups;
    }

    public void setSubDocs(Set<Doc> subDocs) {
        this.subDocs = subDocs;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public boolean isShouldLoad() {
        return shouldLoad;
    }

    public void setShouldLoad(boolean shouldLoad) {
        this.shouldLoad = shouldLoad;
    }

    public boolean isAutoLoad() {
        return autoLoad;
    }

    public void setAutoLoad(boolean autoLoad) {
        this.autoLoad = autoLoad;
    }

    public Object getMeta() {
        return meta;
    }

    public void setMeta(Object meta) {
        this.meta = meta;
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public void setLoaded(boolean loaded) {
        isLoaded = loaded;
    }

    public boolean isSynced() {
        return isSynced;
    }

    public void setSynced(boolean synced) {
        isSynced = synced;
    }

    public boolean isDestroyed() {
        return isDestroyed;
    }

    public void setDestroyed(boolean destroyed) {
        isDestroyed = destroyed;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Doc doc)) {
            return false;
        }
        return clientId == doc.clientId && shouldLoad == doc.shouldLoad && autoLoad == doc.autoLoad && isLoaded == doc.isLoaded && isSynced == doc.isSynced && isDestroyed == doc.isDestroyed && Objects.equals(gc, doc.gc) && Objects.equals(gcFilter, doc.gcFilter) && Objects.equals(guid, doc.guid) && Objects.equals(collectionid, doc.collectionid) && Objects.equals(share, doc.share) && Objects.equals(store, doc.store) && Objects.equals(transaction, doc.transaction) && Objects.equals(transactionCleanups, doc.transactionCleanups) && Objects.equals(subDocs, doc.subDocs) && Objects.equals(item, doc.item) && Objects.equals(meta, doc.meta);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
