package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.SqlAnalysisCache;
import com.biz.sccba.sqlanalyzer.model.response.SqlAnalysisResponse;
import com.biz.sccba.sqlanalyzer.repository.SqlAnalysisCacheRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * SQL分析结果缓存服务
 */
@Service
public class SqlAnalysisCacheService {

    private static final Logger logger = LoggerFactory.getLogger(SqlAnalysisCacheService.class);

    @Autowired
    private SqlAnalysisCacheRepository cacheRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 计算SQL语句的哈希值
     */
    private String calculateSqlHash(String sql) {
        try {
            // 标准化SQL（去除多余空格，统一大小写）
            String normalizedSql = sql.trim().replaceAll("\\s+", " ").toLowerCase();
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedSql.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("计算SQL哈希值失败", e);
            // 如果SHA-256不可用，使用简单的哈希
            String normalizedSql = sql.trim().replaceAll("\\s+", " ").toLowerCase();
            return String.valueOf(normalizedSql.hashCode());
        }
    }

    /**
     * 从缓存中获取分析结果
     * @param sql SQL语句
     * @param dataSourceName 数据源名称（如果为null，使用"default"）
     * @param llmName 大模型名称（如果为null，使用"default"）
     */
    @Transactional(readOnly = true)
    public Optional<SqlAnalysisResponse> getCachedResult(String sql, String dataSourceName, String llmName) {
        try {
            String sqlHash = calculateSqlHash(sql);
            // 处理默认值
            String dsName = dataSourceName != null && !dataSourceName.trim().isEmpty() ? dataSourceName : "default";
            String modelName = llmName != null && !llmName.trim().isEmpty() ? llmName : "default";
            
            Optional<SqlAnalysisCache> cacheOpt = cacheRepository.findBySqlHashAndDataSourceNameAndLlmName(
                sqlHash, dsName, modelName);
            
            if (cacheOpt.isPresent()) {
                SqlAnalysisCache cache = cacheOpt.get();
                logger.info("从缓存中获取SQL分析结果，SQL哈希: {}, 数据源: {}, 大模型: {}", 
                    sqlHash, dsName, modelName);
                
                // 更新访问统计
                cacheRepository.incrementAccessCount(cache.getId());
                
                // 反序列化分析结果
                SqlAnalysisResponse response = objectMapper.readValue(
                    cache.getAnalysisResult(), 
                    SqlAnalysisResponse.class
                );
                
                // 确保SQL和报告是最新的
                response.setSql(sql);
                response.setReport(cache.getReport());
                
                return Optional.of(response);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            logger.error("从缓存获取分析结果失败", e);
            return Optional.empty();
        }
    }

    /**
     * 保存分析结果到缓存
     * @param sql SQL语句
     * @param response 分析结果
     * @param dataSourceName 数据源名称（如果为null，使用"default"）
     * @param llmName 大模型名称（如果为null，使用"default"）
     */
    @Transactional
    public void saveResult(String sql, SqlAnalysisResponse response, String dataSourceName, String llmName) {
        try {
            String sqlHash = calculateSqlHash(sql);
            // 处理默认值
            String dsName = dataSourceName != null && !dataSourceName.trim().isEmpty() ? dataSourceName : "default";
            String modelName = llmName != null && !llmName.trim().isEmpty() ? llmName : "default";
            
            // 检查是否已存在
            Optional<SqlAnalysisCache> existingOpt = cacheRepository.findBySqlHashAndDataSourceNameAndLlmName(
                sqlHash, dsName, modelName);
            
            SqlAnalysisCache cache;
            if (existingOpt.isPresent()) {
                // 更新现有缓存
                cache = existingOpt.get();
                logger.info("更新SQL分析结果缓存，SQL哈希: {}, 数据源: {}, 大模型: {}", 
                    sqlHash, dsName, modelName);
            } else {
                // 创建新缓存
                cache = new SqlAnalysisCache();
                cache.setSqlHash(sqlHash);
                cache.setDataSourceName(dsName);
                cache.setLlmName(modelName);
                logger.info("保存SQL分析结果到缓存，SQL哈希: {}, 数据源: {}, 大模型: {}", 
                    sqlHash, dsName, modelName);
            }
            
            // 设置缓存数据
            cache.setSql(sql);
            cache.setReport(response.getReport());
            cache.setAnalysisResult(objectMapper.writeValueAsString(response));
            
            // 保存到数据库
            cacheRepository.save(cache);
            logger.debug("SQL分析结果已保存到缓存");
            
        } catch (Exception e) {
            logger.error("保存分析结果到缓存失败", e);
            // 不抛出异常，避免影响主流程
        }
    }
}

