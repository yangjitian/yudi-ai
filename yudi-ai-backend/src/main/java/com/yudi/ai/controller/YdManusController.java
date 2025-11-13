package com.yudi.ai.controller;

import cn.hutool.core.util.StrUtil;
import com.yudi.ai.agent.YdManus;
import com.yudi.ai.utils.SseEmitterUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * YdManus智能体接口控制器
 * <p>
 * 提供基于YdManus智能体的对话接口，支持非流式和流式两种响应方式
 */
@Slf4j
@RestController
@RequestMapping("/yd_manus")
public class YdManusController {

    @Resource
    private YdManus ydManus;

    /**
     * YdManus智能体对话接口（非流式）
     *
     * @param query 用户查询
     * @return AI生成的回答
     */
    @GetMapping("/chat")
    public String chat(@RequestParam(value = "query") String query) {
        log.info("YdManus聊天请求: {}", query);

        // 参数校验
        if (StrUtil.isBlank(query)) {
            return "查询内容不能为空";
        }

        try {
            // 确保agent处于空闲状态
            ydManus.reset();
            // 执行agent并获取结果
            String result = ydManus.run(query);
            // 重置agent状态，准备下次使用
            ydManus.reset();
            return result;
        } catch (Exception e) {
            log.error("YdManus执行失败: {}", e.getMessage(), e);
            ydManus.reset(); // 确保重置状态
            return "执行失败: " + e.getMessage();
        }
    }

    /**
     * YdManus智能体对话接口（流式，使用SseEmitter）
     *
     * @param query 用户查询
     * @return SSE流式响应
     */
    @GetMapping("/chat/stream")
    public SseEmitter chatStream(@RequestParam(value = "query") String query) {
        log.info("YdManus聊天请求（流式）: {}", query);

        // 参数校验
        if (StrUtil.isBlank(query)) {
            return SseEmitterUtil.error("查询内容不能为空");
        }

        try {
            // 确保agent处于空闲状态
            ydManus.reset();

            // 使用流式执行方法
            Flux<String> flux = Flux.defer(() -> ydManus.runStream(query))
                    .doOnError(error -> {
                        // 处理流中的异常
                        log.error("YdManus流式执行失败: {}", error.getMessage(), error);
                        ydManus.reset();
                    })
                    .doFinally(signalType -> {
                        // 执行完成后重置agent状态
                        ydManus.reset();
                        log.info("YdManus流式执行完成，信号类型: {}", signalType);
                    })
                    .onErrorResume(error -> {
                        // 将错误转换为错误消息流
                        return Flux.just("执行失败: " + error.getMessage());
                    });

            return SseEmitterUtil.fromFlux(flux);

        } catch (Exception e) {
            // 只捕获同步异常（如 reset() 失败）
            log.error("YdManus初始化失败: {}", e.getMessage(), e);
            ydManus.reset();
            return SseEmitterUtil.error("初始化失败: " + e.getMessage());
        }
    }
}
