package com.triibiotech.yjs.utils;

import java.util.*;

/**
 * ObservableV2: A strongly typed event bus.
 *
 * @author zbs
 * @date 2025/07/31 22:48:58
 */
public class ObservableV2<EVENTS> {

    private final Map<EVENTS, Set<Handler>> observers = new HashMap<>();

    public Map<EVENTS, Set<Handler>> getObservers() {
        return observers;
    }

    // === 核心 Handler 接口 ===

    /** 多参 handler，对应 JS 的 (...args) => void */
    @FunctionalInterface
    public interface Handler {
        void apply(Object... args);
    }

    @FunctionalInterface
    public interface Handler1<T> {
        void apply(T arg);
    }

    // === 注册 ===

    /**
     * Registers a general handler (multi-arg)
     */
    public <E extends EVENTS> Handler on(E event, Handler handler) {
        observers.computeIfAbsent(event, k -> new LinkedHashSet<>()).add(handler);
        return handler;
    }

    /**
     * Registers a no-arg handler
     */
    public <E extends EVENTS> Handler on(E event, Runnable handler) {
        Handler wrapper = args -> handler.run();
        return on(event, wrapper);
    }

    public <E extends EVENTS, T> Handler on(E event, Handler1<T> handler) {
        Handler wrapper = args -> handler.apply((T) args[0]);
        return on(event, wrapper);
    }


    // === once ===

    /**
     * Registers a general handler (multi-arg) that triggers once
     */
    public <E extends EVENTS> void once(E event, Handler handler) {
        Handler wrapper = new Handler() {
            @Override
            public void apply(Object... args) {
                off(event, this);
                handler.apply(args);
            }
        };
        on(event, wrapper);
    }

    /**
     * Registers a no-arg handler that triggers once
     */
    public <E extends EVENTS> void once(E event, Runnable handler) {
        once(event, args -> handler.run());
    }

    // === 移除 ===

    public <E extends EVENTS> void off(E event, Handler handler) {
        Set<Handler> set = observers.get(event);
        if (set != null) {
            set.remove(handler);
            if (set.isEmpty()) {
                observers.remove(event);
            }
        }
    }

    public <E extends EVENTS, T> void off(E event, Handler1<T> handler) {
        Set<Handler> set = observers.get(event);
        if (set != null) {
            set.removeIf(h -> {
                if (h instanceof Handler1<?> h1) {
                    return h1.equals(handler);
                }
                return false;
            });
            if (set.isEmpty()) {
                observers.remove(event);
            }
        }
    }


    // === 触发 ===

    public <E extends EVENTS> void emit(E event, Object... args) {
        Set<Handler> handlers = observers.getOrDefault(event, Collections.emptySet());
        for (Handler handler : new ArrayList<>(handlers)) {
            try {
                handler.apply(args);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // === 清除 ===
    public void destroy() {
        observers.clear();
    }
}
