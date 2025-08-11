package com.triibiotech.yjs.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventEmitter<T> {
    private final Map<String, List<Consumer<T>>> listeners = new ConcurrentHashMap<>();
    
    public void on(String event, Consumer<T> listener) {
        listeners.computeIfAbsent(event, k -> new ArrayList<>()).add(listener);
    }
    
    public void off(String event, Consumer<T> listener) {
        List<Consumer<T>> eventListeners = listeners.get(event);
        if (eventListeners != null) {
            eventListeners.remove(listener);
            if (eventListeners.isEmpty()) {
                listeners.remove(event);
            }
        }
    }
    
    public void emit(String event, T data) {
        List<Consumer<T>> eventListeners = listeners.get(event);
        if (eventListeners != null) {
            for (Consumer<T> listener : new ArrayList<>(eventListeners)) {
                try {
                    listener.accept(data);
                } catch (Exception e) {
                    System.err.println("Error in event listener: " + e.getMessage());
                }
            }
        }
    }
    
    public void removeAllListeners() {
        listeners.clear();
    }
    
    public void clear() {
        listeners.clear();
    }
}