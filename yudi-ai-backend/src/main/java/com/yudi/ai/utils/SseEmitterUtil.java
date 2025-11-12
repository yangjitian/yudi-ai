package com.yudi.ai.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * SSE (Server-Sent Events) 工具类
 * 用于将 Flux<String> 转换为 SseEmitter
 *
 * @author yudi
 */
@Slf4j
public class SseEmitterUtil {

    /**
     * 将 Flux<String> 转换为 SseEmitter
     *
     * @param flux 响应式流
     * @return SseEmitter 实例
     */
    public static SseEmitter fromFlux(Flux<String> flux) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 使用 CompletableFuture 在后台线程中处理流，避免阻塞请求线程
        CompletableFuture.runAsync(() -> {
            flux.subscribe(
                    // 处理每个数据项
                    data -> {
                        try {
                            emitter.send(SseEmitter.event()
                                    .data(data)
                                    .name("message"));
                        } catch (IOException e) {
                            log.error("发送SSE数据失败", e);
                            try {
                                emitter.completeWithError(e);
                            } catch (Exception ex) {
                                log.debug("Emitter已经关闭，忽略错误", ex);
                            }
                        }
                    },
                    // 处理错误
                    error -> {
                        log.error("Flux流处理错误", error);
                        try {
                            emitter.completeWithError(error);
                        } catch (Exception e) {
                            log.debug("Emitter已经关闭，忽略错误", e);
                        }
                    },
                    // 处理完成
                    () -> {
                        log.debug("Flux流处理完成");
                        try {
                            emitter.complete();
                        } catch (Exception e) {
                            log.debug("Emitter已经关闭，忽略完成操作", e);
                        }
                    }
            );
        });

        // 设置超时和错误处理
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时");
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("Emitter已经关闭，忽略超时完成操作", e);
            }
        });

        emitter.onError((throwable) -> {
            log.error("SSE连接发生错误", throwable);
        });

        return emitter;
    }
}

