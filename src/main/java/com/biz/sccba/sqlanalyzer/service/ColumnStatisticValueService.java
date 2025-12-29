package com.biz.sccba.sqlanalyzer.service;

import com.biz.sccba.sqlanalyzer.model.ColumnStatisticValue;
import com.biz.sccba.sqlanalyzer.model.dto.ColumnStatisticsDTO;
import com.biz.sccba.sqlanalyzer.model.StatisticType;
import com.biz.sccba.sqlanalyzer.repository.ColumnStatisticValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 列统计值服务
 * 提供统计值的计算、保存、查询等功能
 * 从MySQL的information_schema.COLUMN_STATISTICS直接读取统计信息
 */
@Service
public class ColumnStatisticValueService {

    private static final Logger logger = LoggerFactory.getLogger(ColumnStatisticValueService.class);

    @Autowired
    private ColumnStatisticValueRepository statisticValueRepository;

    @Autowired
    private ColumnStatisticsParserService parserService;
    
    @Autowired
    private DataSourceManagerService dataSourceManagerService;

    /**
     * 从MySQL的information_schema.COLUMN_STATISTICS计算统计值并保存
     * 
     * @param datasourceName 数据源名称
     * @param tableName 表名
     * @param columnName 列名（如果为null，则计算该表所有列的统计值）
     * @return 计算并保存的统计值列表
     */
    @Transactional
    public List<ColumnStatisticValue> calculateFromStatistics(String datasourceName, String tableName, String columnName) {
        List<ColumnStatisticValue> result = new ArrayList<>();
        
        try {
            String databaseName = extractDatabaseName(datasourceName);
            List<ColumnStatisticsDTO> statisticsList;
            
            if (columnName != null && !columnName.trim().isEmpty()) {
                // 计算指定列的统计值
                ColumnStatisticsDTO dto = parserService.getStatisticsFromMysql(
                    datasourceName, databaseName, tableName, columnName);
                if (dto != null) {
                    statisticsList = List.of(dto);
                } else {
                    logger.warn("未找到列统计信息: datasource={}, table={}, column={}", 
                               datasourceName, tableName, columnName);
                    return result;
                }
            } else {
                // 计算表所有列的统计值
                statisticsList = parserService.getStatisticsFromMysql(datasourceName, databaseName, tableName);
            }
            
            for (ColumnStatisticsDTO dto : statisticsList) {
                List<ColumnStatisticValue> values = calculateForColumn(dto);
                result.addAll(values);
            }
            
            logger.info("计算统计值完成: datasource={}, table={}, column={}, count={}", 
                       datasourceName, tableName, columnName, result.size());
            
        } catch (Exception e) {
            logger.error("计算统计值失败", e);
            throw new RuntimeException("计算统计值失败: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 从数据源配置中提取数据库名称
     */
    private String extractDatabaseName(String datasourceName) {
        try {
            DataSourceManagerService.DataSourceInfo info =
                dataSourceManagerService.getAllDataSources().stream()
                    .filter(ds -> ds.getName().equals(datasourceName) || 
                            (datasourceName == null && ds.getName() != null))
                    .findFirst()
                    .orElse(null);
            
            if (info != null && info.getUrl() != null) {
                String url = info.getUrl();
                // jdbc:mysql://localhost:3306/test_db?...
                if (url.contains("/")) {
                    String[] parts = url.split("/");
                    if (parts.length > 1) {
                        String dbPart = parts[parts.length - 1];
                        // 移除查询参数
                        if (dbPart.contains("?")) {
                            dbPart = dbPart.substring(0, dbPart.indexOf("?"));
                        }
                        return dbPart;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("提取数据库名失败: {}", e.getMessage());
        }
        
        return "test_db"; // 默认值
    }

    /**
     * 为单个列计算所有统计值
     */
    private List<ColumnStatisticValue> calculateForColumn(ColumnStatisticsDTO dto) {
        List<ColumnStatisticValue> values = new ArrayList<>();
        
        // 获取采样值列表
        List<Object> sampleValues = parserService.getSampleValues(dto);
        
        if (sampleValues.isEmpty()) {
            logger.debug("列 {} 没有采样值，无法计算统计值", dto.getColumnName());
            return values;
        }
        
        // 排序采样值
        List<Object> sortedSamples = new ArrayList<>(sampleValues);
        sortedSamples.sort(this::compareValues);
        
        // 计算各种统计值
        // MIN
        if (dto.getMinValue() != null) {
            values.add(saveOrUpdateStatisticValue(
                dto.getDatasourceName(),
                dto.getTableName(),
                dto.getColumnName(),
                StatisticType.MIN,
                dto.getMinValue(),
                false
            ));
        } else if (!sortedSamples.isEmpty()) {
            values.add(saveOrUpdateStatisticValue(
                dto.getDatasourceName(),
                dto.getTableName(),
                dto.getColumnName(),
                StatisticType.MIN,
                sortedSamples.get(0).toString(),
                false
            ));
        }
        
        // MAX
        if (dto.getMaxValue() != null) {
            values.add(saveOrUpdateStatisticValue(
                dto.getDatasourceName(),
                dto.getTableName(),
                dto.getColumnName(),
                StatisticType.MAX,
                dto.getMaxValue(),
                false
            ));
        } else if (!sortedSamples.isEmpty()) {
            values.add(saveOrUpdateStatisticValue(
                dto.getDatasourceName(),
                dto.getTableName(),
                dto.getColumnName(),
                StatisticType.MAX,
                sortedSamples.get(sortedSamples.size() - 1).toString(),
                false
            ));
        }
        
        // MEDIAN (50%分位数)
        if (sortedSamples.size() >= 2) {
            int medianIndex = sortedSamples.size() / 2;
            Object medianValue = sortedSamples.get(medianIndex);
            values.add(saveOrUpdateStatisticValue(
                dto.getDatasourceName(),
                dto.getTableName(),
                dto.getColumnName(),
                StatisticType.MEDIAN,
                medianValue.toString(),
                false
            ));
        }
        
        // PERCENTILE_25 (25%分位数)
        if (sortedSamples.size() >= 4) {
            int p25Index = sortedSamples.size() / 4;
            Object p25Value = sortedSamples.get(p25Index);
            values.add(saveOrUpdateStatisticValue(
                dto.getDatasourceName(),
                dto.getTableName(),
                dto.getColumnName(),
                StatisticType.PERCENTILE_25,
                p25Value.toString(),
                false
            ));
        }
        
        // PERCENTILE_75 (75%分位数)
        if (sortedSamples.size() >= 4) {
            int p75Index = (sortedSamples.size() * 3) / 4;
            Object p75Value = sortedSamples.get(p75Index);
            values.add(saveOrUpdateStatisticValue(
                dto.getDatasourceName(),
                dto.getTableName(),
                dto.getColumnName(),
                StatisticType.PERCENTILE_75,
                p75Value.toString(),
                false
            ));
        }
        
        return values;
    }

    /**
     * 保存或更新统计值
     * 如果已存在相同的数据源、表、列和类型的统计值，则更新；否则创建新的
     */
    @Transactional
    public ColumnStatisticValue saveOrUpdateStatisticValue(String datasourceName, String tableName, 
                                                          String columnName, StatisticType statisticType,
                                                          String statisticValue, boolean isManual) {
        Optional<ColumnStatisticValue> existingOpt = statisticValueRepository
            .findByDatasourceNameAndTableNameAndColumnNameAndStatisticType(
                datasourceName, tableName, columnName, statisticType);
        
        ColumnStatisticValue value;
        if (existingOpt.isPresent()) {
            value = existingOpt.get();
            value.setStatisticValue(statisticValue);
            value.setIsManual(isManual);
        } else {
            value = new ColumnStatisticValue();
            value.setDatasourceName(datasourceName);
            value.setTableName(tableName);
            value.setColumnName(columnName);
            value.setStatisticType(statisticType);
            value.setStatisticValue(statisticValue);
            value.setIsManual(isManual);
        }
        
        return statisticValueRepository.save(value);
    }

    /**
     * 查询指定列的统计值
     */
    public List<ColumnStatisticValue> findByColumn(String datasourceName, String tableName, String columnName) {
        return statisticValueRepository.findByDatasourceNameAndTableNameAndColumnName(
            datasourceName, tableName, columnName);
    }

    /**
     * 查询指定表的所有列的统计值
     */
    public List<ColumnStatisticValue> findByTable(String datasourceName, String tableName) {
        return statisticValueRepository.findByDatasourceNameAndTableName(datasourceName, tableName);
    }

    /**
     * 根据ID查询统计值
     */
    public Optional<ColumnStatisticValue> findById(Long id) {
        return statisticValueRepository.findById(id);
    }

    /**
     * 删除统计值
     */
    @Transactional
    public void delete(Long id) {
        statisticValueRepository.deleteById(id);
    }

    /**
     * 删除指定列的统计值
     */
    @Transactional
    public void deleteByColumn(String datasourceName, String tableName, String columnName) {
        statisticValueRepository.deleteByDatasourceNameAndTableNameAndColumnName(
            datasourceName, tableName, columnName);
    }

    /**
     * 比较两个值的大小（用于排序）
     */
    private int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        
        if (a instanceof Number && b instanceof Number) {
            double da = ((Number) a).doubleValue();
            double db = ((Number) b).doubleValue();
            return Double.compare(da, db);
        }
        
        return a.toString().compareTo(b.toString());
    }
}

