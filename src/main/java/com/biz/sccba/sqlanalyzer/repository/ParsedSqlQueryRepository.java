package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.model.ParsedSqlQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParsedSqlQueryRepository extends JpaRepository<ParsedSqlQuery, Long> {
    
    /**
     * 根据表名查找相关SQL查询
     */
    @Query("SELECT p FROM ParsedSqlQuery p WHERE p.tableName LIKE %:tableName%")
    List<ParsedSqlQuery> findByTableName(@Param("tableName") String tableName);

    /**
     * 根据Mapper命名空间查找
     */
    List<ParsedSqlQuery> findByMapperNamespace(String mapperNamespace);

    /**
     * 根据查询类型查找
     */
    List<ParsedSqlQuery> findByQueryType(String queryType);

    /**
     * 根据Mapper命名空间和语句ID查找
     */
    ParsedSqlQuery findByMapperNamespaceAndStatementId(String mapperNamespace, String statementId);

    /**
     * 删除指定命名空间的所有查询
     */
    void deleteByMapperNamespace(String mapperNamespace);
}

