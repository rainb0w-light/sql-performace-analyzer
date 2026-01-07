package com.biz.sccba.sqlanalyzer.data;

import lombok.Data;
import java.util.List;

@Data
public class TableStructure {
    /**
     * 表名
     */
    private String tableName;

    /**
     * 列信息列表
     */
    private List<ColumnInfo> columns;

    /**
     * 索引信息列表
     */
    private List<IndexInfo> indexes;

    /**
     * 表统计信息
     */
    private TableStatistics statistics;

    @Data
    public static class ColumnInfo {
        private String columnName;
        private String dataType;
        private String isNullable;
        private String columnKey;
        private String columnDefault;
        private String extra;
    }

    @Data
    public static class IndexInfo {
        private String indexName;
        private String columnName;
        private Integer nonUnique;
        private Integer seqInIndex;
        private String indexType;
    }

    @Data
    public static class TableStatistics {
        /**
         * 表的行数
         */
        private Long rows;

        /**
         * 数据大小（字节）
         */
        private Long dataLength;

        /**
         * 索引大小（字节）
         */
        private Long indexLength;

        /**
         * 表引擎
         */
        private String engine;

        /**
         * 字符集
         */
        private String charset;
    }
}
