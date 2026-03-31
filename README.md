# Yjs4j

Yjs 的 Java 移植版，为 Java/JVM 后端提供 CRDT 实时协同编辑能力。移植自 [Yjs](https://github.com/yjs/yjs) 13.6.29。

## 什么是 Yjs？

Yjs 是目前最流行的开源实时协同编辑框架，被 Notion、AFFiNE 等产品采用。它的核心是一种叫 CRDT（Conflict-free Replicated Data Type，无冲突复制数据类型）的算法。

### CRDT

想象你和同事同时编辑同一份文档。你在第一行写了"Hello"，同事在第二行写了"World"。如果没有特殊处理，两个人的修改就会冲突。

传统方案（比如 Google Docs）依赖一个中心服务器来裁决冲突。但 CRDT 的思路不同——它让每个客户端都能独立编辑，然后通过数学上可证明的规则自动合并，**无论消息以什么顺序到达，最终结果都一样**。

Yjs 使用的具体算法叫 YATA（Yet Another Transformation Approach），核心思想是：

1. **每个字符都有唯一 ID**：由 `(clientID, clock)` 组成。clientID 是每个客户端的随机标识，clock 是该客户端的操作计数器。
2. **用链表存储文档**：每个字符（Item）记录了"我左边原来是谁"（origin）和"我右边原来是谁"（rightOrigin）。
3. **插入冲突时按规则排序**：当两个客户端在同一位置插入时，比较 origin 和 clientID 来决定谁排前面。这个规则保证所有客户端最终得到相同的顺序。
4. **删除是标记而非移除**：删除一个字符只是给它打上"已删除"标记（tombstone），不会真正从链表中移除，这样后续的插入操作仍然能找到正确的位置。
5. **垃圾回收**：被删除且不再被任何操作引用的 Item 会被压缩为 GC 节点，节省内存。

### 数据流

```
客户端 A                    服务端                    客户端 B
   |                         |                         |
   |-- insert("H",0) ------>|                         |
   |                         |-- update(binary) ------>|
   |                         |                         |-- insert("W",0) -->
   |<-- update(binary) -----|                         |
   |                         |                         |
   |  最终: "HW" 或 "WH"    |                         |  最终: 同上
   |  (取决于 clientID)      |                         |
```

同步过程分三步：
- **SyncStep1**：发送自己的状态向量（每个 client 的最新 clock）
- **SyncStep2**：对方根据状态向量差异，发送缺失的操作
- **Update**：后续的增量更新

## 项目结构

```
yjs4j/
├── yjs4j/          # 核心 CRDT 实现
│   ├── structs/    # 底层数据结构（Item, GC, Content*）
│   ├── types/      # 共享数据类型（YText, YArray, YMap, YXml*）
│   ├── utils/      # 工具类（Doc, Transaction, Snapshot, Encoding...）
│   └── protocol/   # 同步协议和 Awareness
└── websocket/      # WebSocket 服务端（兼容 y-websocket）
```


### 核心类说明

| 类 | 对应 JS | 说明 |
|---|---|---|
| `Doc` | `Y.Doc` | 文档实例，所有共享类型的容器 |
| `Transaction` | `Transaction` | 事务，批量操作的原子单元 |
| `Item` | `Item` | 链表节点，文档中每一段内容的载体 |
| `StructStore` | `StructStore` | 存储所有 Item，按 clientID 分组 |
| `DeleteSet` | `DeleteSet` | 记录哪些 Item 被删除 |
| `YText` | `Y.Text` | 共享富文本类型 |
| `YArray` | `Y.Array` | 共享数组类型 |
| `YMap` | `Y.Map` | 共享 Map 类型 |
| `YXmlElement` | `Y.XmlElement` | 共享 XML 元素类型 |
| `Snapshot` | `Snapshot` | 文档快照，用于历史版本 |
| `UndoManager` | `UndoManager` | 撤销/重做管理器 |
| `EncodingUtil` | `encoding.js` | 二进制编解码（与 JS 版本线协议兼容） |
| `SyncProtocol` | `y-protocols/sync` | 同步协议实现 |
| `Awareness` | `y-protocols/awareness` | 用户感知协议（光标位置等） |

### 基本用法

```java
import utils.com.catkeeper.yjs.Doc;
import encoding.utils.com.catkeeper.yjs.EncodingUtil;

// 创建文档
Doc doc = new Doc();

        // 获取共享类型
        YText text = doc.getText("content");
        YArray<Object> array = doc.getArray("list");
        YMap<Object> map = doc.getMap("config");

// 编辑文本
text.

        insert(0,"Hello ");
text.

        insert(6,"World",Map.of("bold", true));
        text.

        delete(0,6);
System.out.

        println(text.toString()); // "World"

// 操作数组
        array.

        insert(0,"a","b","c");
array.

        push("d");
array.

        delete(1,1); // 删除 "b"

// 操作 Map
map.

        set("theme","dark");
map.

        set("fontSize",14);
map.

        delete("theme");

        // 嵌套类型
        YMap<Object> nested = new YMap<>();
map.

        set("nested",nested);
nested.

        set("key","value");
```

### 事务

```java
// 事务内的多个操作会被合并为一次更新
doc.transact(tr -> {
    text.insert(0, "Line 1\n");
    text.insert(7, "Line 2\n");
    array.push(1, 2, 3);
    return null;
});
```

### 文档同步

```java
Doc doc1 = new Doc();
Doc doc2 = new Doc();

// doc1 做了一些修改
doc1.getText("text").insert(0, "Hello");

// 编码 doc1 的状态
byte[] update = EncodingUtil.encodeStateAsUpdate(doc1, null);

// 应用到 doc2
EncodingUtil.applyUpdate(doc2, update);

System.out.println(doc2.getText("text").toString()); // "Hello"
```

### 增量同步（监听更新）

```java
Doc doc1 = new Doc();
Doc doc2 = new Doc();

// 监听 doc1 的更新，转发给 doc2
doc1.on("update", (byte[] update, Object origin) -> {
    EncodingUtil.applyUpdate(doc2, update);
});

// doc1 的任何修改都会自动同步到 doc2
doc1.getText("text").insert(0, "realtime sync!");
```

### 快照与历史版本

```java
Doc doc = new Doc(new DocOptions() {{ gc = false; }}); // 关闭 GC 以保留历史
YText text = doc.getText("text");

text.insert(0, "version 1");
Snapshot snap1 = Snapshot.snapshot(doc);

text.delete(0, 9);
text.insert(0, "version 2");

// 从快照恢复
Doc restored = Snapshot.createDocFromSnapshot(doc, snap1, null);
System.out.println(restored.getText("text").toString()); // "version 1"
```

### 撤销/重做

```java
Doc doc = new Doc();
YText text = doc.getText("text");
UndoManager um = new UndoManager(text, new UndoManagerOptions());

text.insert(0, "Hello");
text.insert(5, " World");

um.undo(); // 撤销 " World"
System.out.println(text.toString()); // "Hello"

um.redo(); // 重做
System.out.println(text.toString()); // "Hello World"
```

### WebSocket 协同服务

websocket 模块提供了兼容 y-websocket 的 Java 服务端，可以直接对接前端的 y-websocket provider。

```java
// 参考 websocket 模块的 WebSocketConfig 和 DocWebSocketHandler
// 前端使用标准的 y-websocket provider 即可连接
```

## 与 JS 版本的兼容性

- 二进制协议完全兼容，Java 端和 JS 端可以互相同步
- 支持 V1 和 V2 两种编码格式
- Awareness 协议兼容

## 注意事项

- 移植自 Yjs 13.6.29，已通过 93 个单元测试
- 虽然覆盖了核心场景，但仍可能存在边界情况的差异，建议在生产环境使用前充分测试
- 欢迎反馈问题和建议
