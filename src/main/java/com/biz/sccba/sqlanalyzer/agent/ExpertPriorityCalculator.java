package com.biz.sccba.sqlanalyzer.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Calculates dynamic priorities for expert tools based on SQL query characteristics
 * and database environment.
 */
@Component
public class ExpertPriorityCalculator {

    /**
     * Calculates dynamic priorities for expert tools based on the analysis context.
     * 
     * @param datasourceName The datasource name
     * @param sql The SQL query (if available)
     * @param tables The list of tables involved (if available)
     * @return Map of tool names to their calculated priorities (lower number = higher priority)
     */
    public Map<String, Integer> calculatePriorities(String datasourceName, String sql, List<String> tables) {
        // Default priorities (same as original)
        int distributedDbPriority = 1;
        int innodbPriority = 2; 
        int sqlOptimizerPriority = 3;
        
        // Adjust priorities based on context
        
        // If distributed database is detected, keep highest priority for distributed DB expert
        if (isDistributedDatabase(datasourceName)) {
            distributedDbPriority = 1;
        } else {
            // For non-distributed databases, InnoDB expert might be more relevant
            distributedDbPriority = 3;
            innodbPriority = 1;
        }
        
        // If complex SQL query is detected, give higher priority to SQL optimizer
        if (hasComplexQuery(sql)) {
            sqlOptimizerPriority = Math.min(sqlOptimizerPriority, 2);
        }
        
        // If many tables are involved, prioritize InnoDB expert for join analysis
        if (tables != null && tables.size() > 3) {
            innodbPriority = Math.min(innodbPriority, 2);
        }
        
        return Map.of(
            "distributed_db_expert_analyze", distributedDbPriority,
            "innodb_expert_analyze", innodbPriority,
            "sql_optimizer_analyze", sqlOptimizerPriority
        );
    }
    
    /**
     * Determines if the datasource represents a distributed database.
     */
    private boolean isDistributedDatabase(String datasourceName) {
        if (datasourceName == null) {
            return false;
        }
        String lowerName = datasourceName.toLowerCase();
        return lowerName.contains("golden") || 
               lowerName.contains("shard") || 
               lowerName.contains("distributed") ||
               lowerName.contains("cluster");
    }
    
    /**
     * Determines if the SQL query is complex enough to warrant higher SQL optimizer priority.
     */
    private boolean hasComplexQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        String lowerSql = sql.toLowerCase();
        // Complex queries typically have joins, subqueries, or complex conditions
        return lowerSql.contains("join") || 
               lowerSql.contains("select.*from.*select") ||
               lowerSql.contains("union") ||
               (countOccurrences(lowerSql, "and") + countOccurrences(lowerSql, "or")) > 3 ||
               lowerSql.contains("group by") ||
               lowerSql.contains("having");
    }
    
    /**
     * Counts occurrences of a substring in a string.
     */
    private int countOccurrences(String str, String substr) {
        if (str == null || substr == null || substr.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substr, index)) != -1) {
            count++;
            index += substr.length();
        }
        return count;
    }
}