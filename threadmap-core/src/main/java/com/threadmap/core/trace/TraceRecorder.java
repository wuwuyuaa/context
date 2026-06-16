package com.threadmap.core.trace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程内栈式记录器:enter 压栈并挂到父节点,exit 出栈并记耗时。
 * M1 单线程请求路径;跨线程异步分支不在本次捕获范围内。
 */
public class TraceRecorder {
    private final ThreadLocal<Deque<TraceNode>> stack = ThreadLocal.withInitial(ArrayDeque::new);
    private final AtomicInteger ids = new AtomicInteger(0);
    private volatile boolean recording = false;
    private volatile TraceNode root;

    public void start() {
        recording = true;
        root = null;
        ids.set(0);
        stack.get().clear();
    }

    public void stop() { recording = false; }

    public boolean isRecording() { return recording; }

    public TraceNode getRoot() { return root; }

    public void enter(String signature, String file, int line) {
        if (!recording) return;
        TraceNode node = new TraceNode(ids.getAndIncrement(), signature, file, line);
        Deque<TraceNode> s = stack.get();
        if (s.isEmpty()) {
            root = node;
        } else {
            s.peek().addChild(node);
        }
        s.push(node);
    }

    public void exit(long elapsedMs) {
        if (!recording) return;
        Deque<TraceNode> s = stack.get();
        if (s.isEmpty()) return;
        s.pop().setElapsedMs(elapsedMs);
    }
}
