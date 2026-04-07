package com.ye.decision.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 模块配置属性，绑定 {@code decision.rag.*} 前缀。
 *
 * @author ye
 */
@Component
@ConfigurationProperties(prefix = "decision.rag")
public class RagProperties {

    /** 文件上传目录 */
    private String uploadDir = "./uploads/knowledge";

    /** 上传文件大小上限（字节），默认 50MB */
    private long maxFileSize = 52428800L;

    /** 每个 chunk 的最大 token 数 */
    private int chunkSize = 512;

    /** 相邻 chunk 重叠 token 数 */
    private int chunkOverlap = 100;

    /** 批量写入 Milvus 的 chunk 数 */
    private int batchSize = 100;

    /** 向量检索相似度下限 */
    private double similarityThreshold = 0.7;

    /** Milvus 连接与 Collection 配置 */
    private Milvus milvus = new Milvus();

    public static class Milvus {
        /** Milvus gRPC 地址 */
        private String uri = "http://192.168.83.128:19530";

        /** Collection 名称 */
        private String collectionName = "knowledge_vectors";

        /** 稠密向量维度，须与 EmbeddingModel 输出一致 */
        private int denseDimension = 1024;

        /** RRF 融合参数 k，值越大长尾排名权重越高 */
        private int rrfK = 60;

        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getCollectionName() { return collectionName; }
        public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
        public int getDenseDimension() { return denseDimension; }
        public void setDenseDimension(int denseDimension) { this.denseDimension = denseDimension; }
        public int getRrfK() { return rrfK; }
        public void setRrfK(int rrfK) { this.rrfK = rrfK; }
    }

    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }
    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }
    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public Milvus getMilvus() { return milvus; }
    public void setMilvus(Milvus milvus) { this.milvus = milvus; }
}
