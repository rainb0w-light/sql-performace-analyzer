package com.biz.sccba.sqlanalyzer.response;

import com.biz.sccba.sqlanalyzer.model.SqlFillingRecord;
import lombok.Data;
import java.util.List;

/**
 * 批量查询填充记录响应
 */
@Data
public class FillingRecordsResponse {

    List<SqlFillingRecord> sqlFillingRecords;

}

