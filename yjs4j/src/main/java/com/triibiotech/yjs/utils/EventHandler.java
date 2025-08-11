package com.triibiotech.yjs.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author zbs
 * @date 2025/7/28 15:18
 **/
public class EventHandler<ARG0, ARG1> {
    private List<Function<ARG0, ARG1>> l;

    private EventHandler() {
        this.l = new ArrayList<>();
    }

    public int getFunctionCount() {
        return l.size();
    }

    public static <ARG0, ARG1> EventHandler<ARG0, ARG1> createEventHandler() {
        return new EventHandler<>();
    }

    public void addEventHandlerListener(Function<ARG0, ARG1> f) {
        l.add(f);
    }

    public void removeEventHandlerListener(Function<ARG0, ARG1> f) {
        int len = l.size();
        l = l.stream().filter(g -> g != f).toList();
        if (len == l.size()) {
            System.out.println("[yjs] Tried to remove event handler that doesn\\'t exist.");
        }
    }

    public void removeAllEventHandlerListeners() {
        l = new ArrayList<>();
    }

    public static <ARG0, ARG1> void callEventHandlerListeners(EventHandler<ARG0, ARG1> eventHandler, ARG0 arg0, ARG1 arg1) {
        for (Function<ARG0, ARG1> f : eventHandler.l) {
            f.apply(arg0, arg1);
        }
    }

    @FunctionalInterface
    public interface Function<ARG0, ARG1> {
        void apply(ARG0 arg0, ARG1 arg1);
    }


}
