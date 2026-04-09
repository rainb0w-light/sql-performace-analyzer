package com.biz.sccba.sqlanalyzer.data;

import java.util.List;

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

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    public List<IndexInfo> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexInfo> indexes) {
        this.indexes = indexes;
    }

    public TableStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(TableStatistics statistics) {
        this.statistics = statistics;
    }

    public static class ColumnInfo {
        private String columnName;
        private String dataType;
        private String isNullable;
        private String columnKey;
        private String columnDefault;
        private String extra;

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public String getIsNullable() {
            return isNullable;
        }

        public void setIsNullable(String isNullable) {
            this.isNullable = isNullable;
        }

        public String getColumnKey() {
            return columnKey;
        }

        public void setColumnKey(String columnKey) {
            this.columnKey = columnKey;
        }

        public String getColumnDefault() {
            return columnDefault;
        }

        public void setColumnDefault(String columnDefault) {
            this.columnDefault = columnDefault;
        }

        public String getExtra() {
            return extra;
        }

        public void setExtra(String extra) {
            this.extra = extra;
        }
    }

    public static class IndexInfo {
        private String indexName;
        private String columnName;
        private Integer nonUnique;
        private Integer seqInIndex;
        private String indexType;

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public Integer getNonUnique() {
            return nonUnique;
        }

        public void setNonUnique(Integer nonUnique) {
            this.nonUnique = nonUnique;
        }

        public Integer getSeqInIndex() {
            return seqInIndex;
        }

        public void setSeqInIndex(Integer seqInIndex) {
            this.seqInIndex = seqInIndex;
        }

        public String getIndexType() {
            return indexType;
        }

        public void setIndexType(String indexType) {
            this.indexType = indexType;
        }
    }

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

        public Long getRows() {
            return rows;
        }

        public void setRows(Long rows) {
            this.rows = rows;
        }

        public Long getDataLength() {
            return dataLength;
        }

        public void setDataLength(Long dataLength) {
            this.dataLength = dataLength;
        }

        public Long getIndexLength() {
            return indexLength;
        }

        public void setIndexLength(Long indexLength) {
            this.indexLength = indexLength;
        }

        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }
    }
}
