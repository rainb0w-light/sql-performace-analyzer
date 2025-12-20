package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.ColumnStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ColumnStatisticsRepository extends JpaRepository<ColumnStatistics, Long> {

    /**
     * 根据数据源、表名和列名查找
     */
    Optional<ColumnStatistics> findByDatasourceNameAndTableNameAndColumnName(
        String datasourceName, String tableName, String columnName);

    /**
     * 根据数据源和表名查找所有列的统计信息
     */
    List<ColumnStatistics> findByDatasourceNameAndTableName(
        String datasourceName, String tableName);

    /**
     * 根据数据源查找所有统计信息
     */
    List<ColumnStatistics> findByDatasourceName(String datasourceName);

    /**
     * 删除指定数据源和表的统计信息
     */
    void deleteByDatasourceNameAndTableName(String datasourceName, String tableName);

    /**
     * 删除指定数据源的统计信息
     */
    void deleteByDatasourceName(String datasourceName);
}
