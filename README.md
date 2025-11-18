# Yjs4j

基于 Yjs 13.6.27 的 Java 实现，尝试为 Java 后端提供 CRDT 协同编辑能力。

## 项目简介

Yjs4j 项目的目标是将 JavaScript 版本的 Yjs 移植到 Java 平台。

## 模块说明

- **yjs4j** - 核心 CRDT 实现，移植自 Yjs 13.6.27
- **websocket** - WebSocket 服务端实现，参考 y-websocket-server

## 注意事项

- 虽然在项目中测试了一些基础场景，但仍可能存在未发现的问题，请谨慎在生产环境使用。
- 建议在使用前进行充分测试
- 欢迎反馈问题和建议
