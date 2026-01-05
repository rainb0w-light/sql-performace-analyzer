package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.domain.stats.ColumnHistogram;
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
     * @return 解析后的 ColumnHistogram，如果不存在则返回null
     */
    public ColumnHistogram getStatisticsFromMysql(String datasourceName, String databaseName,
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
    public List<ColumnHistogram> getStatisticsFromMysql(String datasourceName, String databaseName,
                                                        String tableName) {
        List<ColumnHistogram> result = new ArrayList<>();
        
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
                    ColumnHistogram histogram = parseHistogramJson(histogramJson, datasourceName, databaseName, tableName, columnName);
                    if (histogram != null) {
                        result.add(histogram);
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
     * 解析直方图JSON数据为 Domain Stats Object（ColumnHistogram）
     * 
     * @param histogramJson 直方图JSON字符串
     * @param datasourceName 数据源名称
     * @param databaseName 数据库名
     * @param tableName 表名
     * @param columnName 列名
     * @return 解析后的 ColumnHistogram
     */
    public ColumnHistogram parseHistogramJson(String histogramJson, String datasourceName,
                                              String databaseName, String tableName, String columnName) {
        try {
            JsonNode rootNode = objectMapper.readTree(histogramJson);

            String histogramType = rootNode.has("histogram-type") ? rootNode.get("histogram-type").asText() : null;
            Integer bucketCountSpecified = rootNode.has("number-of-buckets-specified")
                    ? rootNode.get("number-of-buckets-specified").asInt()
                    : null;
            Integer bucketCount = rootNode.has("number-of-buckets") ? rootNode.get("number-of-buckets").asInt() : bucketCountSpecified;
            String lastUpdated = rootNode.has("last-updated") ? rootNode.get("last-updated").asText() : null;
            Double samplingRate = rootNode.has("sampling-rate") ? rootNode.get("sampling-rate").asDouble() : null;
            String dataType = rootNode.has("data-type") ? rootNode.get("data-type").asText() : null;

            JsonNode bucketsNode = null;
            if (rootNode.has("buckets")) {
                bucketsNode = rootNode.get("buckets");
            } else if (rootNode.has("histogram")) {
                // 某些 MySQL 版本/文档使用 histogram 字段承载 bucket 数组
                bucketsNode = rootNode.get("histogram");
            }

            ParsedBuckets parsed = parseBuckets(bucketsNode, dataType);

            logger.debug("解析列统计信息成功: table={}, column={}, type={}, buckets={}",
                    tableName, columnName, histogramType, bucketCount);

            return new ColumnHistogram(
                    datasourceName,
                    databaseName,
                    tableName,
                    columnName,
                    dataType,
                    histogramType,
                    bucketCountSpecified,
                    bucketCount,
                    lastUpdated,
                    samplingRate,
                    parsed.minValue,
                    parsed.maxValue,
                    parsed.buckets,
                    parsed.sampleValues
            );

        } catch (Exception e) {
            logger.error("解析直方图JSON失败: table={}, column={}, error={}", 
                        tableName, columnName, e.getMessage(), e);
            return null;
        }
    }

    private static final class ParsedBuckets {
        private final String minValue;
        private final String maxValue;
        private final List<ColumnHistogram.Bucket> buckets;
        private final List<String> sampleValues;

        private ParsedBuckets(String minValue, String maxValue, List<ColumnHistogram.Bucket> buckets, List<String> sampleValues) {
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.buckets = buckets;
            this.sampleValues = sampleValues;
        }
    }

    /**
     * Histogram Extractor + Normalizer（解析+归一化）
     *
     * <p>把 MySQL buckets/histogram 统一成内部 bucket 结构，并对 CDF 做差分得到 rowFraction。</p>
     */
    private ParsedBuckets parseBuckets(JsonNode bucketsNode, String dataType) {
        if (bucketsNode == null || !bucketsNode.isArray() || bucketsNode.isEmpty()) {
            return new ParsedBuckets(null, null, List.of(), List.of());
        }

        List<ColumnHistogram.Bucket> buckets = new ArrayList<>();
        List<String> sampleValues = new ArrayList<>();

        Double prevCdf = 0.0d;

        for (JsonNode bucket : bucketsNode) {
            if (bucket == null || bucket.isNull()) {
                continue;
            }

            String lower = null;
            String upper = null;
            Double cdf = null;

            if (bucket.isArray()) {
                // 常见格式：
                // - [value, cdf]
                // - [lower, upper, cdf]
                if (bucket.size() >= 2) {
                    if (bucket.size() == 2) {
                        String v = extractValueFromNode(bucket.get(0), dataType);
                        lower = v;
                        upper = v;
                        cdf = bucket.get(1).isNumber() ? bucket.get(1).asDouble() : null;
                    } else {
                        lower = extractValueFromNode(bucket.get(0), dataType);
                        upper = extractValueFromNode(bucket.get(1), dataType);
                        cdf = bucket.get(2).isNumber() ? bucket.get(2).asDouble() : null;
                    }
                }
            } else if (bucket.isObject()) {
                // 兼容对象格式：
                // {"lower-bound": "...", "upper-bound": "...", "value": "...", "cumulative-frequency": 0.2}
                if (bucket.has("value")) {
                    String v = bucket.get("value").asText();
                    lower = v;
                    upper = v;
                } else {
                    if (bucket.has("lower-bound")) lower = bucket.get("lower-bound").asText();
                    if (bucket.has("upper-bound")) upper = bucket.get("upper-bound").asText();
                }
                if (bucket.has("cumulative-frequency") && bucket.get("cumulative-frequency").isNumber()) {
                    cdf = bucket.get("cumulative-frequency").asDouble();
                } else if (bucket.has("cumulative_frequency") && bucket.get("cumulative_frequency").isNumber()) {
                    cdf = bucket.get("cumulative_frequency").asDouble();
                } else if (bucket.has("cumulative-fraction") && bucket.get("cumulative-fraction").isNumber()) {
                    cdf = bucket.get("cumulative-fraction").asDouble();
                }
            }

            if (lower != null && lower.startsWith("base64:")) {
                lower = decodeBase64Value(lower);
            }
            if (upper != null && upper.startsWith("base64:")) {
                upper = decodeBase64Value(upper);
            }

            Double rowFraction = null;
            if (cdf != null) {
                rowFraction = cdf - (prevCdf == null ? 0.0d : prevCdf);
                prevCdf = cdf;
            }

            ColumnHistogram.BucketKind kind = classifyBucket(lower, upper);
            buckets.add(new ColumnHistogram.Bucket(lower, upper, cdf, rowFraction, kind));

            // sample values：从 bucket 抽代表值
            if (sampleValues.size() < 50) {
                String sample = (kind == ColumnHistogram.BucketKind.SINGLETON) ? lower : (lower != null ? lower : upper);
                if (sample != null) {
                    sampleValues.add(sample);
                }
            }
        }

        String minValue = null;
        String maxValue = null;
        if (!buckets.isEmpty()) {
            ColumnHistogram.Bucket first = buckets.get(0);
            ColumnHistogram.Bucket last = buckets.get(buckets.size() - 1);
            minValue = first != null ? coalesce(first.lower(), first.upper()) : null;
            maxValue = last != null ? coalesce(last.upper(), last.lower()) : null;
        }

        return new ParsedBuckets(minValue, maxValue, buckets, sampleValues);
    }

    private ColumnHistogram.BucketKind classifyBucket(String lower, String upper) {
        if (lower == null || upper == null) {
            return ColumnHistogram.BucketKind.UNKNOWN;
        }
        if (lower.equals(upper)) {
            return ColumnHistogram.BucketKind.SINGLETON;
        }
        // SMALL_RANGE/RANGE 需要知道数值跨度；此处先用 UNKNOWN/RANGE 兜底，后续可以在 Normalizer 中按 dataType 做细分
        return ColumnHistogram.BucketKind.RANGE;
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
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
}
