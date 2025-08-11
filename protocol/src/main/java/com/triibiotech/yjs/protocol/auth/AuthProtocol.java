package com.triibiotech.yjs.protocol.auth;

import com.triibiotech.yjs.utils.*;
import java.util.function.BiConsumer;

/**
 * Authentication protocol for Yjs
 */
public class AuthProtocol {

    public static final int MESSAGE_PERMISSION_DENIED = 0;

    /**
     * Permission denied handler interface
     */
    @FunctionalInterface
    public interface PermissionDeniedHandler extends BiConsumer<Doc, String> {
    }
}
