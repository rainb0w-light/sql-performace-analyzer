package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.ColumnStatisticValue;
import com.biz.sccba.sqlanalyzer.model.StatisticType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ColumnStatisticValueRepository extends JpaRepository<ColumnStatisticValue, Long> {

    /**
     * 根据数据源、表名、列名和统计类型查找
     */
    Optional<ColumnStatisticValue> findByDatasourceNameAndTableNameAndColumnNameAndStatisticType(
        String datasourceName, String tableName, String columnName, StatisticType statisticType);

    /**
     * 根据数据源、表名和列名查找所有统计值
     */
    List<ColumnStatisticValue> findByDatasourceNameAndTableNameAndColumnName(
        String datasourceName, String tableName, String columnName);

    /**
     * 根据数据源和表名查找所有列的统计值
     */
    List<ColumnStatisticValue> findByDatasourceNameAndTableName(
        String datasourceName, String tableName);

    /**
     * 根据数据源查找所有统计值
     */
    List<ColumnStatisticValue> findByDatasourceName(String datasourceName);

    /**
     * 根据统计类型查找
     */
    List<ColumnStatisticValue> findByStatisticType(StatisticType statisticType);

    /**
     * 删除指定数据源、表名和列名的统计值
     */
    void deleteByDatasourceNameAndTableNameAndColumnName(
        String datasourceName, String tableName, String columnName);

    /**
     * 删除指定数据源和表的统计值
     */
    void deleteByDatasourceNameAndTableName(String datasourceName, String tableName);
}

