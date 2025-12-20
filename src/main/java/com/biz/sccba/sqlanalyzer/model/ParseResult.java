package com.biz.sccba.sqlanalyzer.model;

import java.util.List;

public  class ParseResult {
        private String mapperNamespace;
        private int queryCount;
        private List<ParsedSqlQuery> queries;

        public String getMapperNamespace() {
            return mapperNamespace;
        }

        public void setMapperNamespace(String mapperNamespace) {
            this.mapperNamespace = mapperNamespace;
        }

        public int getQueryCount() {
            return queryCount;
        }

        public void setQueryCount(int queryCount) {
            this.queryCount = queryCount;
        }

        public List<ParsedSqlQuery> getQueries() {
            return queries;
        }

        public void setQueries(List<ParsedSqlQuery> queries) {
            this.queries = queries;
        }
    }