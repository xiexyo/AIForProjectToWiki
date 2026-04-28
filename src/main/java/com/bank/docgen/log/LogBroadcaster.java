// src/main/java/com/bank/docgen/log/LogBroadcaster.java
package com.bank.docgen.log;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 日志广播器。
 * 维护所有 SSE 连接，把日志事件推给前端。
 */
@Component
public class LogBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter createEmitter() {
        // 超时 30 分钟，文档生成可能很久
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    /**
     * 广播一条日志给所有连接的前端。
     */
    public void broadcast(String message) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(message));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * 广播事件（如构建完成）。
     */
    public void broadcastEvent(String eventName, String data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}