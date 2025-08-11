package com.triibiotech.yjs.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author zbs
 * @date 2025/8/1 16:05
 **/
public class Observable<N> {

    /**
     * 注册的事件监听器，key 是事件名，value 是监听器列表。
     */
    private final Map<N, List<Consumer<Object[]>>> observers = new HashMap<>();

    /**
     * 注册监听器。
     */
    public void on(N name, Consumer<Object[]> listener) {
        observers.computeIfAbsent(name, k -> new ArrayList<>()).add(listener);
    }

    /**
     * 注册一次性监听器。
     */
    public void once(N name, Consumer<Object[]> listener) {
        Consumer<Object[]> wrapper = new Consumer<>() {
            @Override
            public void accept(Object[] args) {
                listener.accept(args);
                off(name, this);
            }
        };
        on(name, wrapper);
    }

    /**
     * 取消监听器。
     */
    public void off(N name, Consumer<Object[]> listener) {
        List<Consumer<Object[]>> listeners = observers.get(name);
        if (listeners != null) {
            listeners.removeIf(l -> l == listener);
            if (listeners.isEmpty()) {
                observers.remove(name);
            }
        }
    }

    /**
     * 触发事件。
     */
    public void emit(N name, Object... args) {
        List<Consumer<Object[]>> listeners = observers.get(name);
        if (listeners != null) {
            // 复制列表，防止监听器修改 observers 影响当前迭代
            for (Consumer<Object[]> listener : new ArrayList<>(listeners)) {
                try {
                    listener.accept(args);
                } catch (Exception e) {
                    // 可以加日志记录异常
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 清除所有监听器。
     */
    public void destroy() {
        observers.clear();
    }
}
