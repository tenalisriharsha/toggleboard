package com.toggleboard.api.streaming;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans out {@link FlagChangeEvent}s to every SSE subscriber of an
 * environment's change stream. A client's connection naturally times out
 * after {@link #EMITTER_TIMEOUT_MILLIS}; the client SDK is expected to
 * reconnect (see its own backoff logic) rather than this server holding
 * connections open forever.
 */
@Component
public class FlagChangePublisher {

    private static final long EMITTER_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> subscribersByEnvironment = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String environmentKey) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        register(environmentKey, emitter);
        return emitter;
    }

    void register(String environmentKey, SseEmitter emitter) {
        List<SseEmitter> subscribers = subscribersByEnvironment.computeIfAbsent(environmentKey, key -> new CopyOnWriteArrayList<>());
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
    }

    public void publish(FlagChangeEvent event) {
        List<SseEmitter> subscribers = subscribersByEnvironment.get(event.environmentKey());
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("flag-change").data(event));
            } catch (IOException | IllegalStateException e) {
                subscribers.remove(emitter);
                emitter.completeWithError(e);
            }
        }
    }

    public int subscriberCount(String environmentKey) {
        List<SseEmitter> subscribers = subscribersByEnvironment.get(environmentKey);
        return subscribers == null ? 0 : subscribers.size();
    }
}
