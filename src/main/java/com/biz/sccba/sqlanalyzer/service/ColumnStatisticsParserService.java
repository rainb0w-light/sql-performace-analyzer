package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.dto.ColumnStatisticsDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列统计信息解析服务
 * 直接从MySQL的information_schema.COLUMN_STATISTICS读取并解析JSON格式直方图数据
 */
@Service
public class ColumnStatisticsParserService {

    private static final Logger logger = LoggerFactory.getLogger(ColumnStatisticsParserService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    /**
     * 从MySQL的information_schema.COLUMN_STATISTICS读取并解析统计信息
     * 
     * @param datasourceName 数据源名称
     * @param databaseName 数据库名
     * @param tableName 表名
     * @param columnName 列名
     * @return 解析后的ColumnStatisticsDTO对象，如果不存在则返回null
     */
    public ColumnStatisticsDTO getStatisticsFromMysql(String datasourceName, String databaseName,
                                                       String tableName, String columnName) {
        try {
            JdbcTemplate jdbcTemplate = dataSourceManagerService.getJdbcTemplate(datasourceName);
            
            String sql = """
                SELECT HISTOGRAM
                FROM information_schema.COLUMN_STATISTICS
                WHERE SCHEMA_NAME = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, databaseName, tableName, columnName);
            
            if (results.isEmpty()) {
                logger.debug("未找到列统计信息: schema={}, table={}, column={}", databaseName, tableName, columnName);
                return null;
            }
            
            String histogramJson = (String) results.get(0).get("HISTOGRAM");
            if (histogramJson == null || histogramJson.trim().isEmpty()) {
                logger.debug("列 {} 没有直方图数据", columnName);
                return null;
            }
            
            return parseHistogramJson(histogramJson, datasourceName, databaseName, tableName, columnName);
            
        } catch (DataAccessException e) {
            logger.warn("查询information_schema.COLUMN_STATISTICS失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.error("从MySQL读取统计信息失败: table={}, column={}, error={}", 
                        tableName, columnName, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 从MySQL获取指定表的所有列的统计信息
     */
    public List<ColumnStatisticsDTO> getStatisticsFromMysql(String datasourceName, String databaseName,
                                                              String tableName) {
        List<ColumnStatisticsDTO> result = new ArrayList<>();
        
        try {
            JdbcTemplate jdbcTemplate = dataSourceManagerService.getJdbcTemplate(datasourceName);
            
            String sql = """
                SELECT COLUMN_NAME, HISTOGRAM
                FROM information_schema.COLUMN_STATISTICS
                WHERE SCHEMA_NAME = ? AND TABLE_NAME = ?
                """;
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, databaseName, tableName);
            
            for (Map<String, Object> row : results) {
                String columnName = (String) row.get("COLUMN_NAME");
                String histogramJson = (String) row.get("HISTOGRAM");
                
                if (histogramJson != null && !histogramJson.trim().isEmpty()) {
                    ColumnStatisticsDTO dto = parseHistogramJson(histogramJson, datasourceName, databaseName, tableName, columnName);
                    if (dto != null) {
                        result.add(dto);
                    }
                }
            }
            
        } catch (DataAccessException e) {
            logger.warn("查询information_schema.COLUMN_STATISTICS失败: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("从MySQL读取统计信息失败: table={}, error={}", tableName, e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 解析直方图JSON数据为DTO
     * 
     * @param histogramJson 直方图JSON字符串
     * @param datasourceName 数据源名称
     * @param databaseName 数据库名
     * @param tableName 表名
     * @param columnName 列名
     * @return 解析后的ColumnStatisticsDTO对象
     */
    public ColumnStatisticsDTO parseHistogramJson(String histogramJson, String datasourceName,
                                                  String databaseName, String tableName, String columnName) {
        try {
            JsonNode rootNode = objectMapper.readTree(histogramJson);
            
            ColumnStatisticsDTO dto = new ColumnStatisticsDTO(datasourceName, databaseName, tableName, columnName);

            // 解析直方图类型
            if (rootNode.has("histogram-type")) {
                dto.setHistogramType(rootNode.get("histogram-type").asText());
            }

            // 解析桶数量
            if (rootNode.has("number-of-buckets-specified")) {
                dto.setBucketCount(rootNode.get("number-of-buckets-specified").asInt());
            }

            // 解析数据统计信息
            String dataType = null;
            if (rootNode.has("data-type")) {
                dataType = rootNode.get("data-type").asText();
            }

            // 解析直方图桶数据
            if (rootNode.has("buckets")) {
                JsonNode bucketsNode = rootNode.get("buckets");
                dto.setHistogramData(bucketsNode.toString());
                
                // 从桶中提取最小值和最大值
                parseBucketData(bucketsNode, dto, dataType);
            }

            // 解析采样值
            List<Object> sampleValues = extractSampleValues(rootNode);
            dto.setSampleValues(sampleValues);

            logger.debug("解析列统计信息成功: table={}, column={}, type={}, buckets={}", 
                        tableName, columnName, dto.getHistogramType(), dto.getBucketCount());

            return dto;

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
    private void parseBucketData(JsonNode bucketsNode, ColumnStatisticsDTO dto, String dataType) {
        if (!bucketsNode.isArray() || bucketsNode.size() == 0) {
            return;
        }

        try {
            JsonNode firstBucket = bucketsNode.get(0);

            // 判断桶的格式：数组格式还是对象格式
            if (firstBucket.isArray()) {
                // 数组格式：[value, cumulative_frequency]
                parseArrayFormatBuckets(bucketsNode, dto, dataType);
            } else if (firstBucket.isObject()) {
                // 对象格式：{"lower-bound": "...", "upper-bound": "...", "value": "..."}
                parseObjectFormatBuckets(bucketsNode, dto);
            }

        } catch (Exception e) {
            logger.warn("解析桶数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 解析数组格式的桶数据
     * 格式：[value, cumulative_frequency]
     * 例如：["base64:type254:QWxpY2UgU21pdGg=", 0.2] 或 [25, 0.2]
     */
    private void parseArrayFormatBuckets(JsonNode bucketsNode, ColumnStatisticsDTO dto, String dataType) {
        try {
            // 第一个桶的最小值
            JsonNode firstBucket = bucketsNode.get(0);
            if (firstBucket.isArray() && firstBucket.size() >= 1) {
                JsonNode valueNode = firstBucket.get(0);
                String minValue = extractValueFromNode(valueNode, dataType);
                if (minValue != null) {
                    dto.setMinValue(minValue);
                }
            }

            // 最后一个桶的最大值
            JsonNode lastBucket = bucketsNode.get(bucketsNode.size() - 1);
            if (lastBucket.isArray() && lastBucket.size() >= 1) {
                JsonNode valueNode = lastBucket.get(0);
                String maxValue = extractValueFromNode(valueNode, dataType);
                if (maxValue != null) {
                    dto.setMaxValue(maxValue);
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
    private void parseObjectFormatBuckets(JsonNode bucketsNode, ColumnStatisticsDTO dto) {
        try {
            // 第一个桶的最小值
            JsonNode firstBucket = bucketsNode.get(0);
            if (firstBucket.has("lower-bound")) {
                String lowerBound = firstBucket.get("lower-bound").asText();
                dto.setMinValue(lowerBound);
            } else if (firstBucket.has("value")) {
                // 对于singleton类型，使用value
                String value = firstBucket.get("value").asText();
                dto.setMinValue(value);
            }

            // 最后一个桶的最大值
            JsonNode lastBucket = bucketsNode.get(bucketsNode.size() - 1);
            if (lastBucket.has("upper-bound")) {
                String upperBound = lastBucket.get("upper-bound").asText();
                dto.setMaxValue(upperBound);
            } else if (lastBucket.has("value")) {
                String value = lastBucket.get("value").asText();
                dto.setMaxValue(value);
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
     * 从ColumnStatisticsDTO对象中获取采样值列表
     */
    public List<Object> getSampleValues(ColumnStatisticsDTO dto) {
        if (dto == null || dto.getSampleValues() == null) {
            return new ArrayList<>();
        }
        return dto.getSampleValues();
    }

    /**
     * 从ColumnStatisticsDTO对象中获取直方图桶数据
     */
    public JsonNode getHistogramBuckets(ColumnStatisticsDTO dto) {
        if (dto == null || dto.getHistogramData() == null || dto.getHistogramData().trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readTree(dto.getHistogramData());
        } catch (Exception e) {
            logger.warn("解析直方图数据失败: {}", e.getMessage());
            return null;
        }
    }
}
