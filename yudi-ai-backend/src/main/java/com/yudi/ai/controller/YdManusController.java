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
    public SseEmitter chatStream(@RequestParam(value = "query", required = true) String query) {
        log.info("YdManus聊天请求（流式）: {}", query);
        
        // 参数校验
        if (StrUtil.isBlank(query)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().data("查询内容不能为空").name("error"));
                emitter.complete();
            } catch (Exception e) {
                log.error("发送错误消息失败", e);
            }
            return emitter;
        }
        
        try {
            // 确保agent处于空闲状态
            ydManus.reset();
            // 使用流式执行方法
            Flux<String> flux = ydManus.runStream(query)
                    .doFinally(signalType -> {
                        // 执行完成后重置agent状态
                        ydManus.reset();
                        log.info("YdManus流式执行完成，信号类型: {}", signalType);
                    });
            
            return SseEmitterUtil.fromFlux(flux);
        } catch (Exception e) {
            log.error("YdManus流式执行失败: {}", e.getMessage(), e);
            ydManus.reset(); // 确保重置状态
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().data("执行失败: " + e.getMessage()).name("error"));
                emitter.complete();
            } catch (Exception ex) {
                log.error("发送错误消息失败", ex);
            }
            return emitter;
        }
    }
}

