package com.biz.sccba.sqlanalyzer.data;

import com.biz.sccba.sqlanalyzer.model.ParsedSqlQuery;
import lombok.Data;

import java.util.List;

@Data
public  class ParseResult {
        private String mapperNamespace;
        private int queryCount;
        private List<ParsedSqlQuery> queries;
    }