package com.yudi.ai.utils;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE (Server-Sent Events) 工具类
 * 用于将 Flux<String> 转换为 SseEmitter
 *
 * @author yudi
 */
@Slf4j
public class SseEmitterUtil {

    // 共享的心跳线程池
    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR = Executors.newScheduledThreadPool(
            1, ThreadUtil.createThreadFactory("sse-heartbeat")
    );

    // 默认超时时间：1小时
    private static final long DEFAULT_TIMEOUT = TimeUnit.HOURS.toMillis(1);
    // 心跳间隔：15秒
    private static final int HEARTBEAT_INTERVAL = 15;

    /**
     * 将 Flux<String> 转换为 SseEmitter（默认事件名为 message）
     * 注意：不过滤空白字符串，因为可能包含重要的换行符等空白字符
     */
    public static SseEmitter fromFlux(Flux<String> flux) {
        return fromEventFlux(
                flux.filter(data -> data != null) // 只过滤 null，保留空白字符串（可能包含换行符）
                        .map(data -> SseEmitter.event().name("message").data(data))
        );
    }

    /**
     * 将 Flux<SseEmitter.SseEventBuilder> 转换为 SseEmitter（支持自定义事件）
     *
     * @param eventFlux 事件流
     * @return SseEmitter 实例
     */
    public static SseEmitter fromEventFlux(Flux<SseEmitter.SseEventBuilder> eventFlux) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        final Disposable[] subscriptionHolder = new Disposable[1];
        final ScheduledFuture<?>[] heartbeatHolder = new ScheduledFuture<?>[1];
        Runnable cleanup = getCleanup(subscriptionHolder, heartbeatHolder);

        emitter.onTimeout(() -> {
            log.debug("SSE连接超时");
            cleanup.run();
        });
        emitter.onError(throwable -> {
            log.debug("SSE连接错误: {}", throwable.getMessage());
            cleanup.run();
        });
        emitter.onCompletion(() -> {
            log.debug("SSE连接完成");
            cleanup.run();
        });

        heartbeatHolder[0] = HEARTBEAT_EXECUTOR.scheduleWithFixedDelay(() -> {
            try {
                emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
            } catch (IOException e) {
                log.debug("心跳发送失败，关闭连接");
                safeComplete(emitter);
                cleanup.run();
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

        subscriptionHolder[0] = eventFlux.subscribe(
                event -> {
                    try {
                        emitter.send(event);
                    } catch (IOException e) {
                        log.debug("发送数据失败: {}", e.getMessage());
                        safeComplete(emitter);
                        cleanup.run();
                    }
                },
                error -> {
                    log.error("流处理错误", error);
                    try {
                        emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    } catch (IOException ignore) {}
                    safeCompleteWithError(emitter, error);
                    cleanup.run();
                },
                () -> {
                    log.debug("流处理完成");
                    try {
                        emitter.send(SseEmitter.event().name("complete").data("done"));
                    } catch (IOException ignore) {}
                    safeComplete(emitter);
                    cleanup.run();
                }
        );

        return emitter;
    }

    private static @NotNull Runnable getCleanup(Disposable[] subscriptionHolder, ScheduledFuture<?>[] heartbeatHolder) {
        final AtomicBoolean cleaned = new AtomicBoolean(false);

        // 资源清理方法
        return () -> {
            if (!cleaned.compareAndSet(false, true)) {
                return;
            }
            // 取消订阅
            if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                subscriptionHolder[0].dispose();
            }
            // 取消心跳
            if (heartbeatHolder[0] != null && !heartbeatHolder[0].isCancelled()) {
                heartbeatHolder[0].cancel(false);
            }
            log.debug("SSE连接资源已清理");
        };
    }

    /**
     * 创建错误响应的SseEmitter
     */
    public static SseEmitter error(String message) {
        SseEmitter emitter = new SseEmitter(5000L);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(StrUtil.blankToDefault(message, "未知错误")));
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        } finally {
            safeComplete(emitter);
        }
        return emitter;
    }

    /**
     * 安全完成Emitter
     */
    private static void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.trace("Emitter已关闭", e);
        }
    }

    /**
     * 安全完成Emitter（带错误）
     */
    private static void safeCompleteWithError(SseEmitter emitter, Throwable error) {
        try {
            emitter.completeWithError(error);
        } catch (Exception e) {
            log.trace("Emitter已关闭", e);
        }
    }
}