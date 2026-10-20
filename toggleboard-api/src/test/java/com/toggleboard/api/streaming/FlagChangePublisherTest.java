package com.toggleboard.api.streaming;

import com.toggleboard.api.domain.Flag;
import com.toggleboard.api.domain.FlagType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class FlagChangePublisherTest {

    private final FlagChangePublisher publisher = new FlagChangePublisher();

    @Test
    void subscribingRegistersAnEmitter() {
        publisher.subscribe("production");

        assertThat(publisher.subscriberCount("production")).isEqualTo(1);
        assertThat(publisher.subscriberCount("staging")).isEqualTo(0);
    }

    @Test
    void publishSendsTheEventToEverySubscriberOfThatEnvironmentOnly() throws Exception {
        SseEmitter prodEmitter = Mockito.spy(new SseEmitter());
        SseEmitter stagingEmitter = Mockito.spy(new SseEmitter());
        publisher.register("production", prodEmitter);
        publisher.register("staging", stagingEmitter);

        FlagChangeEvent event = new FlagChangeEvent("production", "flag-a", FlagChangeAction.CREATED, flag(), Instant.now());
        publisher.publish(event);

        verify(prodEmitter).send(Mockito.any(SseEmitter.SseEventBuilder.class));
        verify(stagingEmitter, Mockito.never()).send(Mockito.any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void publishingWithNoSubscribersDoesNotThrow() {
        FlagChangeEvent event = new FlagChangeEvent("production", "flag-a", FlagChangeAction.CREATED, flag(), Instant.now());

        publisher.publish(event);
    }

    // Removal on real client disconnect is covered end-to-end by
    // StreamControllerTest#disconnectingRemovesTheSubscriberFromThePublisher,
    // since a bare SseEmitter only fires onCompletion() once Spring's async
    // servlet machinery has attached a real Handler to it.

    @Test
    void aBrokenEmitterIsRemovedRatherThanBreakingDeliveryToOthers() throws Exception {
        SseEmitter brokenEmitter = Mockito.spy(new SseEmitter());
        Mockito.doThrow(new java.io.IOException("broken pipe")).when(brokenEmitter).send(Mockito.any(SseEmitter.SseEventBuilder.class));
        SseEmitter healthyEmitter = Mockito.spy(new SseEmitter());
        publisher.register("production", brokenEmitter);
        publisher.register("production", healthyEmitter);

        publisher.publish(new FlagChangeEvent("production", "flag-a", FlagChangeAction.UPDATED, flag(), Instant.now()));

        verify(healthyEmitter).send(Mockito.any(SseEmitter.SseEventBuilder.class));
        assertThat(publisher.subscriberCount("production")).isEqualTo(1);
    }

    private Flag flag() {
        Instant now = Instant.now();
        return new Flag("id-1", "flag-a", "production", "Flag A", "desc", FlagType.BOOLEAN,
                true, 100, List.of(), null, List.of(), List.of(), now, now);
    }
}
