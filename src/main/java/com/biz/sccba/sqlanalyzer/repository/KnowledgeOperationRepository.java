package com.biz.sccba.sqlanalyzer.repository;

import com.biz.sccba.sqlanalyzer.domain.knowledge.KnowledgeOperation;

import java.time.Instant;
import java.util.List;

public interface KnowledgeOperationRepository {

    KnowledgeOperation append(KnowledgeOperation operation);

    Page find(String clientId, Filter filter, int page, int size);

    List<KnowledgeOperation> findForExport(String clientId, Filter filter, int limit);

    record Filter(Instant from, Instant to, String actorId, String operationType,
                  String status, String sourceId, String traceId) {}

    record Page(List<KnowledgeOperation> items, int page, int size, long total) {}
}
