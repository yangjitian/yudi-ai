package com.yudi.ai.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 基于 内存 的向量存储
 *
 * @author yudi
 */
@Slf4j
//@Configuration
public class CookVectorStoreConfig {

    @Resource
    private CookDocumentLoader cookDocumentLoader;

    @Resource
    private MyKeyWordEnricher myKeyWordEnricher;

    /**
     * 配置烹饪知识库向量存储
     * 使用SimpleVectorStore作为内存向量数据库
     *
     * @return 配置好的VectorStore实例
     */
    @Bean
    public VectorStore cookMemoryVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        // 使用builder模式创建SimpleVectorStore实例
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        // 加载烹饪文档
        List<Document> documents = cookDocumentLoader.loadMarkdowns();
        log.info("加载了 {} 个文档", documents.size());
        
        // 应用关键词增强
        documents = myKeyWordEnricher.enrichDocuments(documents);
        log.info("应用关键词增强完成");
        
        // 将文档添加到向量存储
        vectorStore.add(documents);
        log.info("文档已添加到内存向量存储");
        return vectorStore;
    }
}