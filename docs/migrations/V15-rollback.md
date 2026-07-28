# V15 rollback

Stop knowledge-admin traffic, export any required audit rows, then drop
`sql_analyzer.knowledge_operation_log`, its indexes, the two new
`knowledge_version` indexes, and the seven added version columns. Rollback loses processing
metadata and operation audit history, so it is intentionally a manual maintenance action.
