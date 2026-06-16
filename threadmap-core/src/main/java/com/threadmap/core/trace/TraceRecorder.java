package com.threadmap.core.trace;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 线程内栈式记录器:enter 压栈并挂到父节点,exit 出栈并记耗时。
 *
 * <p>并发模型:本类是共享单例,被切面在所有请求线程上调用,因此 {@code recording}
 * 用 volatile 保证 start()/stop() 对各线程可见;{@code root} 同理用于安全发布给
 * stop() 之后的 getRoot()。单条 trace 只在一个请求线程上采集(M1 通过测试触发 /
 * 入口隔离保证同时只录一条),故节点 id、栈与树构建均为单线程,无需原子化。
 * 跨线程异步分支不在 M1 捕获范围内。
 */
public class TraceRecorder {
    private final ThreadLocal<Deque<TraceNode>> stack = ThreadLocal.withInitial(ArrayDeque::new);
    private int nextId = 0;
    private volatile boolean recording = false;
    private volatile TraceNode root;

    public void start() {
        root = null;
        nextId = 0;
        stack.get().clear();
        recording = true;   // arm last, after state is reset
    }

    public void stop() { recording = false; }

    public boolean isRecording() { return recording; }

    public TraceNode getRoot() { return root; }

    public void enter(String signature, String file, int line) {
        if (!recording) return;
        TraceNode node = new TraceNode(nextId++, signature, file, line);
        Deque<TraceNode> s = stack.get();
        if (s.isEmpty()) {
            root = node;
        } else {
            s.peek().addChild(node);
        }
        s.push(node);
    }

    /**
     * 出栈当前帧并记录其总耗时。调用方负责测量耗时(例如在方法调用前后取
     * {@code System.nanoTime()} 的差值)。未配对的 exit(空栈)会被安全忽略。
     */
    public void exit(long elapsedMs) {
        if (!recording) return;
        Deque<TraceNode> s = stack.get();
        if (s.isEmpty()) return;
        s.pop().setElapsedMs(elapsedMs);
    }
}
