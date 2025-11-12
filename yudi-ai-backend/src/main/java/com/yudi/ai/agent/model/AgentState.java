package com.yudi.ai.agent.model;

/**
 * 代理执行状态
 */
public enum AgentState {

    /**
     * 空闲
     */
    IDLE,

    /**
     * 执行
     */
    RUNNING,

    /**
     * 已完成
     */
    FINISHED,

    /**
     * 错误
     */
    ERROR
}
