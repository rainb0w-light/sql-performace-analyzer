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
            String dataType = null;
            if (rootNode.has("data-type")) {
                dataType = rootNode.get("data-type").asText();
                // 可以存储数据类型信息
            }

            // 解析直方图桶数据
            if (rootNode.has("buckets")) {
                JsonNode bucketsNode = rootNode.get("buckets");
                statistics.setHistogramData(bucketsNode.toString());
                
                // 从桶中提取最小值和最大值
                parseBucketData(bucketsNode, statistics, dataType);
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
     * 支持两种格式：
     * 1. 对象格式：{"lower-bound": "...", "upper-bound": "...", "value": "..."}
     * 2. 数组格式：["value", cumulative_frequency] 或 [value, cumulative_frequency]
     */
    private void parseBucketData(JsonNode bucketsNode, ColumnStatistics statistics, String dataType) {
        if (!bucketsNode.isArray() || bucketsNode.size() == 0) {
            return;
        }

        try {
            JsonNode firstBucket = bucketsNode.get(0);

            // 判断桶的格式：数组格式还是对象格式
            if (firstBucket.isArray()) {
                // 数组格式：[value, cumulative_frequency]
                parseArrayFormatBuckets(bucketsNode, statistics, dataType);
            } else if (firstBucket.isObject()) {
                // 对象格式：{"lower-bound": "...", "upper-bound": "...", "value": "..."}
                parseObjectFormatBuckets(bucketsNode, statistics);
            }

            // 计算不同值数量（从桶的数量估算）
            statistics.setDistinctCount((long) bucketsNode.size());

        } catch (Exception e) {
            logger.warn("解析桶数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析数组格式的桶数据
     * 格式：[value, cumulative_frequency]
     * 例如：["base64:type254:QWxpY2UgU21pdGg=", 0.2] 或 [25, 0.2]
     */
    private void parseArrayFormatBuckets(JsonNode bucketsNode, ColumnStatistics statistics, String dataType) {
        try {
            // 第一个桶的最小值
            JsonNode firstBucket = bucketsNode.get(0);
            if (firstBucket.isArray() && firstBucket.size() >= 1) {
                JsonNode valueNode = firstBucket.get(0);
                String minValue = extractValueFromNode(valueNode, dataType);
                if (minValue != null) {
                    statistics.setMinValue(minValue);
                }
            }

            // 最后一个桶的最大值
            JsonNode lastBucket = bucketsNode.get(bucketsNode.size() - 1);
            if (lastBucket.isArray() && lastBucket.size() >= 1) {
                JsonNode valueNode = lastBucket.get(0);
                String maxValue = extractValueFromNode(valueNode, dataType);
                if (maxValue != null) {
                    statistics.setMaxValue(maxValue);
                }
            }
        } catch (Exception e) {
            logger.warn("解析数组格式桶数据失败: {}", e.getMessage());
        }
    }

    /**
     * 解析对象格式的桶数据
     * 格式：{"lower-bound": "...", "upper-bound": "...", "value": "..."}
     */
    private void parseObjectFormatBuckets(JsonNode bucketsNode, ColumnStatistics statistics) {
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
        } catch (Exception e) {
            logger.warn("解析对象格式桶数据失败: {}", e.getMessage());
        }
    }

    /**
     * 从JsonNode中提取值，支持base64编码的字符串和数字类型
     */
    private String extractValueFromNode(JsonNode valueNode, String dataType) {
        if (valueNode == null) {
            return null;
        }

        try {
            if (valueNode.isTextual()) {
                String textValue = valueNode.asText();
                // 检查是否是base64编码的字符串（格式：base64:type254:...）
                if (textValue.startsWith("base64:")) {
                    // 解码base64值
                    return decodeBase64Value(textValue);
                }
                return textValue;
            } else if (valueNode.isNumber()) {
                // 数字类型直接转换为字符串
                return valueNode.asText();
            } else {
                return valueNode.toString();
            }
        } catch (Exception e) {
            logger.warn("提取值失败: {}", e.getMessage());
            return valueNode.asText();
        }
    }

    /**
     * 解码base64格式的值
     * 格式：base64:type254:encoded_value
     */
    private String decodeBase64Value(String base64Value) {
        try {
            // 格式：base64:type254:QWxpY2UgU21pdGg=
            if (base64Value.startsWith("base64:")) {
                String[] parts = base64Value.split(":", 3);
                if (parts.length >= 3) {
                    String encodedValue = parts[2];
                    // 解码base64
                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(encodedValue);
                    return new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            return base64Value;
        } catch (Exception e) {
            logger.warn("解码base64值失败: {}", e.getMessage());
            return base64Value;
        }
    }

    /**
     * 从直方图数据中提取采样值
     * 支持对象格式和数组格式的桶数据
     */
    private List<Object> extractSampleValues(JsonNode rootNode) {
        List<Object> sampleValues = new ArrayList<>();

        try {
            if (rootNode.has("buckets")) {
                JsonNode bucketsNode = rootNode.get("buckets");
                String dataType = rootNode.has("data-type") ? rootNode.get("data-type").asText() : null;
                
                if (bucketsNode.isArray()) {
                    // 从每个桶中提取代表性值
                    for (JsonNode bucket : bucketsNode) {
                        Object sampleValue = null;
                        
                        if (bucket.isArray() && bucket.size() >= 1) {
                            // 数组格式：[value, cumulative_frequency]
                            JsonNode valueNode = bucket.get(0);
                            sampleValue = extractSampleValueFromNode(valueNode, dataType);
                        } else if (bucket.isObject()) {
                            // 对象格式：{"lower-bound": "...", "upper-bound": "...", "value": "..."}
                            if (bucket.has("value")) {
                                sampleValue = bucket.get("value").asText();
                            } else if (bucket.has("lower-bound")) {
                                sampleValue = bucket.get("lower-bound").asText();
                            } else if (bucket.has("upper-bound")) {
                                sampleValue = bucket.get("upper-bound").asText();
                            }
                        }
                        
                        if (sampleValue != null) {
                            sampleValues.add(sampleValue);
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
     * 从JsonNode中提取采样值
     */
    private Object extractSampleValueFromNode(JsonNode valueNode, String dataType) {
        if (valueNode == null) {
            return null;
        }

        try {
            if (valueNode.isTextual()) {
                String textValue = valueNode.asText();
                // 检查是否是base64编码的字符串
                if (textValue.startsWith("base64:")) {
                    return decodeBase64Value(textValue);
                }
                return textValue;
            } else if (valueNode.isNumber()) {
                // 根据数据类型返回合适的数值类型
                if ("int".equals(dataType) || "integer".equals(dataType) || "bigint".equals(dataType)) {
                    return valueNode.asLong();
                } else if ("double".equals(dataType) || "float".equals(dataType) || "decimal".equals(dataType)) {
                    return valueNode.asDouble();
                }
                return valueNode.asText();
            } else if (valueNode.isBoolean()) {
                return valueNode.asBoolean();
            } else {
                return valueNode.toString();
            }
        } catch (Exception e) {
            logger.warn("提取采样值失败: {}", e.getMessage());
            return valueNode.asText();
        }
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
