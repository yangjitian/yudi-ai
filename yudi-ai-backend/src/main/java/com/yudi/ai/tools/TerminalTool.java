package com.yudi.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.thread.ThreadUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 终端执行工具
 */
@Component
public class TerminalTool {

    /**
     * 执行系统命令
     */
    @Tool(name = "exec", description = "Execute the system command and return the output")
        public String exec(String command) {
        if (StrUtil.isBlank(command)) {
            return "命令不能为空。";
        }
        Process process = null;
        try {
            // 判断系统平台
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String[] cmd = isWindows
                    ? new String[]{"cmd.exe", "/c", command}
                    : new String[]{"/bin/bash", "-c", command};

            // 启动进程
            process = Runtime.getRuntime().exec(cmd);

            // 自动选择系统编码（Windows -> GBK，其它 -> UTF-8）
            Charset charset = isWindows ? Charset.forName("GBK") : StandardCharsets.UTF_8;

            // 同步读取命令输出与错误流
            String result = IoUtil.read(process.getInputStream(), charset);
            String error = IoUtil.read(process.getErrorStream(), charset);

            // 等待命令执行完成（非阻塞方式）
            Process finalProcess = process;
            ThreadUtil.execAsync(() -> {
                try {
                    finalProcess.waitFor();
                } catch (InterruptedException ignored) {
                }
            });

            if (StrUtil.isNotBlank(error)) {
                return "命令执行错误：\n" + error;
            }

            return StrUtil.isBlank(result) ? "（无输出）" : result.trim();

        } catch (Exception e) {
            return "执行失败：" + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
    /**
     * 查看当前系统信息（AI可用来判断平台）
     */
    @Tool(name = "systemInfo", description = "View system platform information")
    public String systemInfo() {
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        String version = System.getProperty("os.version");
        String user = System.getProperty("user.name");
        String home = System.getProperty("user.home");

        return String.format("系统：%s (%s)\n版本：%s\n用户：%s\n主目录：%s",
                os, arch, version, user, home);
    }
}
