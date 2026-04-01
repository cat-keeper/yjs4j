package cn.timegap.yjs.websocket.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;

import static org.mockito.Mockito.*;

class DocWebSocketHandlerTest {

    @Mock
    private WebSocketSession session;

    private DocWebSocketHandler handler;

    /* 消息的base64编码 */
    String request1 = "AALzAwEBuZqH/wMCKAEJcmV2aXNpb25zL3JldmlzaW9uLWQ4OTI5OWQyLTM5ZmQtNGRlMy05OTYxLTMyYzdiM2ExYWQ3Mi0wAXYMAmlkdy9yZXZpc2lvbi1kODkyOTlkMi0zOWZkLTRkZTMtOTk2MS0zMmM3YjNhMWFkNzItMAR0eXBldwZpbnNlcnQEZnJvbX0DAnRvfQQHY29udGVudHcBMQ1yZWxhdGl2ZVN0YXJ0fQALcmVsYXRpdmVFbmR9AQ9oaWdobGlnaHRSYW5nZXN1AXYDBGZyb219AwJ0b30EB2NvbnRlbnR3ATEPb3JpZ2luYWxDb250ZW50dwAGbm9kZUlkdyRkODkyOTlkMi0zOWZkLTRkZTMtOTk2MS0zMmM3YjNhMWFkNzIEdXNlcnYFAmlkdwdkZWZhdWx0BG5hbWV3Ceadjua4r+a+swVjb2xvcncHIzk2Q0VCNAZhdmF0YXJ3dGh0dHBzOi8vY21tcy1kZXYuZS1kc3Blbi5jb20vZGV2LWFwaS9wcm9maWxlL2NtbXMvcHVibGljL2F2YXRhci9jOWI1MWM3YS1kYWQ1LTQ2NjUtYjg0Mi0zMmVjNTFhNjg3ZjMvYW9zcjItNmQ5cDkuZ2lmBnVzZXJJZH0BCXRpbWVzdGFtcHtCeYfUh8yAAAA=";
    String request2 = "AALnAwEBnMTN5w0MqJzEzecNCgF2DQJpZHcvcmV2aXNpb24tYTY2MjRhNjktYzFkNy00Y2FmLWIyY2EtNjFiYzhlNWE3MTc0LTAEdHlwZXcGaW5zZXJ0BGZyb219PgJ0b32AAQdjb250ZW50dwIyMg1yZWxhdGl2ZVN0YXJ0fQMLcmVsYXRpdmVFbmR9BQ9oaWdobGlnaHRSYW5nZXN1AXYDBGZyb219PgJ0b32AAQdjb250ZW50dwIyMg9vcmlnaW5hbENvbnRlbnR3A2FzZAZub2RlSWR3JGE2NjI0YTY5LWMxZDctNGNhZi1iMmNhLTYxYmM4ZTVhNzE3NAR1c2VydgUCaWR3B2RlZmF1bHQEbmFtZXcJ5p2O5riv5r6zBWNvbG9ydwcjNDVCN0QxBmF2YXRhcnd0aHR0cHM6Ly9jbW1zLWRldi5lLWRzcGVuLmNvbS9kZXYtYXBpL3Byb2ZpbGUvY21tcy9wdWJsaWMvYXZhdGFyL2M5YjUxYzdhLWRhZDUtNDY2NS1iODQyLTMyZWM1MWE2ODdmMy9hb3NyMi02ZDlwOS5naWYGdXNlcklkfQEJdGltZXN0YW1we0J5h+SqSQAAEmN1cnJlbnROb2RlQ29udGVudHcFYXNkMjIBnMTN5w0BCgE=";

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        handler = new DocWebSocketHandler();
        when(session.getUri()).thenReturn(new URI("ws://localhost:9002/tiptap-demo"));
        when(session.getId()).thenReturn("test-session-id");
        when(session.isOpen()).thenReturn(true);
        java.util.Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("documentId", 1);
        when(session.getAttributes()).thenReturn(attributes);
    }

    @Test
    void testHandleBinaryMessage_SyncMessage() {
        // 构造合法的 SyncStep1 消息:
        // byte[0] = 0 (MESSAGE_SYNC)
        // 然后是 writeSyncStep1 的内容: varuint(0)=SyncStep1, varuint8array(encodeStateVector)
        // encodeStateVector 对空文档 = [0] (varuint编码的0个client)
        // writeSyncStep1 写入: varuint(0), varuint8array([0]) = varuint(0), varuint(1), 0
        // 完整消息: [0, 0, 1, 0]
        byte[] syncMessage = {0, 0, 1, 0};
        BinaryMessage message = new BinaryMessage(ByteBuffer.wrap(syncMessage));

        handler.handleBinaryMessage(session, message);

        verify(session, atLeastOnce()).isOpen();
    }

    @Test
    void testHandleBinaryMessage_AwarenessMessage() {
        // 构造合法的 Awareness 消息:
        // byte[0] = 1 (MESSAGE_AWARENESS)
        // 然后是 readVarUint8Array 读取的 awareness update 数据
        // awareness update: varuint(clientCount=0) = [0]
        // 完整消息: [1, 1, 0] = awareness, varuint8array长度=1, 内容=[0(0个client)]
        byte[] awarenessMessage = {1, 1, 0};
        BinaryMessage message = new BinaryMessage(ByteBuffer.wrap(awarenessMessage));

        handler.handleBinaryMessage(session, message);

        // 验证消息被处理
        verify(session, atLeastOnce()).getAttributes();
    }

    @Test
    void testHandleBinaryMessage_WithBase64Request() {
        byte[] decodedMessage = Base64.getDecoder().decode(request1);
        BinaryMessage message = new BinaryMessage(ByteBuffer.wrap(decodedMessage));

        handler.handleBinaryMessage(session, message);

        // 验证消息被处理（session 被访问过）
        verify(session, atLeastOnce()).getAttributes();
    }

}
