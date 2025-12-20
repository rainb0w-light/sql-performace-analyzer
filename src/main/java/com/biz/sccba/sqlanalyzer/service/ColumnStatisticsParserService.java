package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ColumnStatistics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 列统计信息解析服务
 * 解析从information_schema.column_statistics获取的JSON格式直方图数据
 */
@Service
public class ColumnStatisticsParserService {

    private static final Logger logger = LoggerFactory.getLogger(ColumnStatisticsParserService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析直方图JSON数据
     * 
     * @param histogramJson 直方图JSON字符串
     * @param datasourceName 数据源名称
     * @param databaseName 数据库名
     * @param tableName 表名
     * @param columnName 列名
     * @return 解析后的ColumnStatistics对象
     */
    public ColumnStatistics parseHistogramJson(String histogramJson, String datasourceName,
                                                String databaseName, String tableName, String columnName) {
        try {
            JsonNode rootNode = objectMapper.readTree(histogramJson);
            
            ColumnStatistics statistics = new ColumnStatistics();
            statistics.setDatasourceName(datasourceName);
            statistics.setDatabaseName(databaseName);
            statistics.setTableName(tableName);
            statistics.setColumnName(columnName);
            statistics.setRawJsonData(histogramJson);

            // 解析直方图类型
            if (rootNode.has("histogram-type")) {
                statistics.setHistogramType(rootNode.get("histogram-type").asText());
            }

            // 解析桶数量
            if (rootNode.has("number-of-buckets-specified")) {
                statistics.setBucketCount(rootNode.get("number-of-buckets-specified").asInt());
            }

            // 解析数据统计信息
            if (rootNode.has("data-type")) {
                String dataType = rootNode.get("data-type").asText();
                // 可以存储数据类型信息
            }

            // 解析直方图桶数据
            if (rootNode.has("buckets")) {
                JsonNode bucketsNode = rootNode.get("buckets");
                statistics.setHistogramData(bucketsNode.toString());
                
                // 从桶中提取最小值和最大值
                parseBucketData(bucketsNode, statistics);
            }

            // 解析采样值
            List<Object> sampleValues = extractSampleValues(rootNode);
            if (!sampleValues.isEmpty()) {
                statistics.setSampleValues(objectMapper.writeValueAsString(sampleValues));
            }

            logger.debug("解析列统计信息成功: table={}, column={}, type={}, buckets={}", 
                        tableName, columnName, statistics.getHistogramType(), statistics.getBucketCount());

            return statistics;

        } catch (Exception e) {
            logger.error("解析直方图JSON失败: table={}, column={}, error={}", 
                        tableName, columnName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析桶数据，提取最小值和最大值
     */
    private void parseBucketData(JsonNode bucketsNode, ColumnStatistics statistics) {
        if (!bucketsNode.isArray() || bucketsNode.size() == 0) {
            return;
        }

        try {
            // 第一个桶的最小值
            JsonNode firstBucket = bucketsNode.get(0);
            if (firstBucket.has("lower-bound")) {
                String lowerBound = firstBucket.get("lower-bound").asText();
                statistics.setMinValue(lowerBound);
            } else if (firstBucket.has("value")) {
                // 对于singleton类型，使用value
                String value = firstBucket.get("value").asText();
                statistics.setMinValue(value);
            }

            // 最后一个桶的最大值
            JsonNode lastBucket = bucketsNode.get(bucketsNode.size() - 1);
            if (lastBucket.has("upper-bound")) {
                String upperBound = lastBucket.get("upper-bound").asText();
                statistics.setMaxValue(upperBound);
            } else if (lastBucket.has("value")) {
                String value = lastBucket.get("value").asText();
                statistics.setMaxValue(value);
            }

            // 计算不同值数量（从桶的数量估算）
            statistics.setDistinctCount((long) bucketsNode.size());

        } catch (Exception e) {
            logger.warn("解析桶数据失败: {}", e.getMessage());
        }
    }

    /**
     * 从直方图数据中提取采样值
     */
    private List<Object> extractSampleValues(JsonNode rootNode) {
        List<Object> sampleValues = new ArrayList<>();

        try {
            if (rootNode.has("buckets")) {
                JsonNode bucketsNode = rootNode.get("buckets");
                
                if (bucketsNode.isArray()) {
                    // 从每个桶中提取代表性值
                    for (JsonNode bucket : bucketsNode) {
                        if (bucket.has("value")) {
                            // Singleton类型：直接使用value
                            sampleValues.add(bucket.get("value").asText());
                        } else if (bucket.has("lower-bound")) {
                            // Equi-height类型：使用lower-bound
                            sampleValues.add(bucket.get("lower-bound").asText());
                        } else if (bucket.has("upper-bound")) {
                            // Equi-height类型：使用upper-bound
                            sampleValues.add(bucket.get("upper-bound").asText());
                        }
                        
                        // 限制采样值数量，避免过多
                        if (sampleValues.size() >= 50) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("提取采样值失败: {}", e.getMessage());
        }

        return sampleValues;
    }

    /**
     * 从ColumnStatistics对象中获取采样值列表
     */
    public List<Object> getSampleValues(ColumnStatistics statistics) {
        if (statistics.getSampleValues() == null || statistics.getSampleValues().trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(statistics.getSampleValues());
            List<Object> values = new ArrayList<>();
            
            if (jsonNode.isArray()) {
                for (JsonNode node : jsonNode) {
                    if (node.isTextual()) {
                        values.add(node.asText());
                    } else if (node.isNumber()) {
                        values.add(node.asDouble());
                    } else if (node.isBoolean()) {
                        values.add(node.asBoolean());
                    } else {
                        values.add(node.toString());
                    }
                }
            }
            
            return values;
        } catch (Exception e) {
            logger.warn("解析采样值JSON失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从ColumnStatistics对象中获取直方图桶数据
     */
    public JsonNode getHistogramBuckets(ColumnStatistics statistics) {
        if (statistics.getHistogramData() == null || statistics.getHistogramData().trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readTree(statistics.getHistogramData());
        } catch (Exception e) {
            logger.warn("解析直方图数据失败: {}", e.getMessage());
            return null;
        }
    }
}
