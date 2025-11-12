package com.yudi.ai.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于文件持久化的对话记忆
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;
    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        // 设置实例化策略
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    // 构造对象时，指定文件保存目录
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            boolean created = baseDir.mkdirs();
            log.info("📁 创建记忆存储目录: {} - 结果: {}", dir, created ? "成功" : "失败");
        } else {
            log.info("📁 记忆存储目录已存在: {}", dir);
        }
        log.info("🔧 FileBasedChatMemory初始化完成，存储目录: {}", dir);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        log.info("💾 添加消息到记忆存储 - 对话ID: {}, 新增消息数量: {}", conversationId, messages.size());
        
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        int beforeSize = conversationMessages.size();
        
        conversationMessages.addAll(messages);
        int afterSize = conversationMessages.size();
        
        log.info("📊 记忆存储状态 - 对话ID: {}, 添加前: {}条, 添加后: {}条", 
                conversationId, beforeSize, afterSize);
        
        // 记录新增的消息内容
        for (int i = beforeSize; i < afterSize; i++) {
            Message msg = conversationMessages.get(i);
            log.info("📝 新增消息[{}]: {} - {}", i + 1, msg.getClass().getSimpleName(), 
                msg.getText().length() > 100 ? msg.getText().substring(0, 100) + "..." : msg.getText());
        }
        
        saveConversation(conversationId, conversationMessages);
        log.info("✅ 消息已保存到记忆存储");
    }

    @Override
    public List<Message> get(String conversationId) {
        log.info("📖 读取记忆存储 - 对话ID: {}", conversationId);
        
        List<Message> allMessages = getOrCreateConversation(conversationId);
        List<Message> result = allMessages.stream()
                .skip(0)
                .toList();
        
        log.info("📚 读取结果 - 对话ID: {}, 消息数量: {}", conversationId, result.size());
        if (!result.isEmpty()) {
            log.info("📝 读取到的消息内容:");
            for (int i = 0; i < result.size(); i++) {
                Message msg = result.get(i);
                log.info("  [{}] {}: {}", i + 1, msg.getClass().getSimpleName(), 
                    msg.getText().length() > 100 ? msg.getText().substring(0, 100) + "..." : msg.getText());
            }
        } else {
            log.info("📝 无历史消息记录");
        }
        
        return result;
    }

    @Override
    public void clear(String conversationId) {
        log.info("🗑️ 清除记忆存储 - 对话ID: {}", conversationId);
        
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            boolean deleted = file.delete();
            log.info("🗑️ 删除记忆文件 - 对话ID: {}, 文件路径: {}, 删除结果: {}", 
                    conversationId, file.getAbsolutePath(), deleted ? "成功" : "失败");
        } else {
            log.info("📝 记忆文件不存在，无需删除 - 对话ID: {}, 文件路径: {}", 
                    conversationId, file.getAbsolutePath());
        }
    }

    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        
        if (file.exists()) {
            log.info("📁 读取记忆文件 - 对话ID: {}, 文件路径: {}, 文件大小: {} bytes", 
                    conversationId, file.getAbsolutePath(), file.length());
            try (Input input = new Input(new FileInputStream(file))) {
                messages = kryo.readObject(input, ArrayList.class);
                log.info("✅ 记忆文件读取成功 - 对话ID: {}, 消息数量: {}", conversationId, messages.size());
            } catch (IOException e) {
                log.error("❌ 记忆文件读取失败 - 对话ID: {}, 错误: {}", conversationId, e.getMessage(), e);
                e.printStackTrace();
            }
        } else {
            log.info("📝 记忆文件不存在，创建新对话 - 对话ID: {}, 文件路径: {}", 
                    conversationId, file.getAbsolutePath());
        }
        return messages;
    }

    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        log.info("💾 保存记忆文件 - 对话ID: {}, 文件路径: {}, 消息数量: {}", 
                conversationId, file.getAbsolutePath(), messages.size());
        
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, messages);
            log.info("✅ 记忆文件保存成功 - 对话ID: {}, 文件大小: {} bytes", 
                    conversationId, file.length());
        } catch (IOException e) {
            log.error("❌ 记忆文件保存失败 - 对话ID: {}, 错误: {}", conversationId, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private File getConversationFile(String conversationId) {
        File file = new File(BASE_DIR, conversationId + ".kryo");
        log.debug("📁 获取对话文件路径 - 对话ID: {}, 文件路径: {}", conversationId, file.getAbsolutePath());
        return file;
    }
}
