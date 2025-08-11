@echo off
echo ===================================
echo Yjs WebSocket Server - Java
echo ===================================
echo Port: 9002
echo WebSocket Path: /yjs-websocket/{roomName}
echo Health Check: http://localhost:9002/
echo Status: http://localhost:9002/status
echo ===================================
echo.

mvn exec:java -Dexec.mainClass="com.triibiotech.websocket.server.YjsWebSocketServer"