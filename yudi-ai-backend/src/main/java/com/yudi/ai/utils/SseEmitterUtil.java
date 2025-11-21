package com.yudi.ai.utils;

import cn.hutool.core.map.MapUtil;
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
     * 使用 Hutool 并发 Map 维护可手动控制的流
     */
    private static final ConcurrentMap<String, StreamHandle> STREAM_REGISTRY = MapUtil.newConcurrentHashMap();

    /**
     * 将 Flux<SseEmitter.SseEventBuilder> 转换为 SseEmitter（支持自定义事件）
     */
    public static SseEmitter fromEventFlux(Flux<SseEmitter.SseEventBuilder> eventFlux) {
        return fromEventFlux(null, eventFlux, null);
    }

    /**
     * 带标识的 SSE 转换，支持外部手动暂停
     *
     * @param streamKey   流标识（如 conversationId）
     * @param eventFlux   事件流
     * @param onTerminate 终止回调（可选）
     */
    public static SseEmitter fromEventFlux(String streamKey,
                                           Flux<SseEmitter.SseEventBuilder> eventFlux,
                                           Runnable onTerminate) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        final Disposable[] subscriptionHolder = new Disposable[1];
        final ScheduledFuture<?>[] heartbeatHolder = new ScheduledFuture<?>[1];
        final AtomicBoolean terminateOnce = new AtomicBoolean(false);
        Runnable baseCleanup = getCleanup(subscriptionHolder, heartbeatHolder);
        Runnable cleanup = () -> {
            baseCleanup.run();
            if (onTerminate != null && terminateOnce.compareAndSet(false, true)) {
                try {
                    onTerminate.run();
                } catch (Exception e) {
                    log.error("SSE终止回调执行失败", e);
                }
            }
            if (StrUtil.isNotBlank(streamKey)) {
                STREAM_REGISTRY.remove(streamKey);
            }
        };

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

        if (StrUtil.isNotBlank(streamKey)) {
            STREAM_REGISTRY.put(streamKey, new StreamHandle(streamKey, emitter, cleanup));
        }

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
     * 主动停止指定流
     */
    public static boolean stopStream(String streamKey, String reason) {
        if (StrUtil.isBlank(streamKey)) {
            return false;
        }
        StreamHandle handle = STREAM_REGISTRY.remove(streamKey);
        if (handle == null) {
            return false;
        }
        handle.stop(reason);
        return true;
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

    private static class StreamHandle {
        private final String key;
        private final SseEmitter emitter;
        private final Runnable cleanup;
        private final AtomicBoolean pausedEventSent = new AtomicBoolean(false);

        private StreamHandle(String key, SseEmitter emitter, Runnable cleanup) {
            this.key = key;
            this.emitter = emitter;
            this.cleanup = cleanup;
        }

        private void stop(String reason) {
            if (pausedEventSent.compareAndSet(false, true)) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("paused")
                            .data(StrUtil.blankToDefault(reason, "stream manually stopped")));
                } catch (IOException e) {
                    log.debug("发送暂停事件失败: {}", e.getMessage());
                }
            }
            cleanup.run();
            log.info("SSE流已手动停止，key={}", key);
        }
    }
}