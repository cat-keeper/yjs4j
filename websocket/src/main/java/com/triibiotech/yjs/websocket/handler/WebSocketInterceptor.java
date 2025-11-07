package com.triibiotech.yjs.websocket.handler;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.extra.spring.SpringUtil;
import jakarta.annotation.Nonnull;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * @author zbs
 * @date 2025/10/28 15:27
 **/
public class WebSocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(@Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response, @Nonnull WebSocketHandler handler,
                                   @Nonnull Map<String, Object> attr) {

        // 未登录情况下拒绝握手
//        if (!StpKit.USER.isLogin()) {
//            log.warn("---- 未授权客户端，连接失败");
//            return false;
//        }
//        LoginUser loginUser = (LoginUser) StpKit.USER.getSession().get((String) StpKit.USER.getLoginId());
//        TimeSetting webSessionSetting = SpringUtil.getBean(SettingsUseCase.class).getTimeSetting(loginUser.getTenantId());
//        StpKit.USER.renewTimeout(webSessionSetting.getExpiredMinutes() * 60);
        // 标记 userId，握手成功
        attr.put("userId", UUID.randomUUID().toString());
        attr.put("loginUser", new Object());

        String path = request.getURI().getPath();
        if (path.contains("doc-collaboration")) {
            String[] split = path.split("/");
            String documentId = split[split.length - 1];
            if (NumberUtil.isNumber(documentId)) {
                attr.put("documentId", Integer.parseInt(documentId));
            } else {
                try {
                    // 响应失败原因
                    response.setStatusCode(HttpStatusCode.valueOf(400));
                    response.getHeaders().add("Content-Type", "text/plain;charset=UTF-8");
                    response.getBody().write("documentId is not found".getBytes());
                } catch (Exception ignored) {
                }
                return false;
            }
        }

        return true;
    }

    @Override
    public void afterHandshake(@Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response, @Nonnull WebSocketHandler handler,
                               Exception exception) {

    }

}
