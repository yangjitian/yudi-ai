package com.yudi.ai.tools;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import com.yudi.ai.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 文件下载工具
 */
@Component
public class ResourceDownloadTool {

    /**
     * 下载文件到指定路径
     *
     * @param fileUrl  文件下载链接
     * @param fileName 保存的文件名
     * @return 下载结果信息
     */
    @Tool(name = "download", description = "Download the file to the specified path locally")
    public String download(@ToolParam(description = "文件URL") String fileUrl,
                           @ToolParam(description = "保存的文件名") String fileName) {

        if (StrUtil.isBlank(fileUrl) || StrUtil.isBlank(fileName)) {
            return "文件URL或文件名不能为空。";
        }
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;

        // 创建目录
        File dir = new File(fileDir);
        if (!dir.exists() && !dir.mkdirs()) {
            return "创建目录失败：" + fileDir;
        }
        // 下载文件
        try (InputStream inputStream = HttpRequest.get(fileUrl).execute().bodyStream();
             FileOutputStream fos = new FileOutputStream(filePath)) {
            if (inputStream == null) {
                return "文件下载失败：未获取到输入流";
            }
            IoUtil.copy(inputStream, fos);
            return "文件下载成功，保存路径：" + filePath;
        } catch (IOException e) {
            return "文件下载异常：" + e.getMessage();
        }
    }
}
