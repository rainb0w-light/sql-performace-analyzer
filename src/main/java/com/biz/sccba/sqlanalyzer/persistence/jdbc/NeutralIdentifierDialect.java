package com.biz.sccba.sqlanalyzer.persistence.jdbc;

import org.springframework.data.jdbc.core.dialect.JdbcArrayColumns;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.relational.core.dialect.Dialect;
import org.springframework.data.relational.core.dialect.Escaper;
import org.springframework.data.relational.core.dialect.IdGeneration;
import org.springframework.data.relational.core.dialect.InsertRenderContext;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.dialect.LockClause;
import org.springframework.data.relational.core.dialect.OrderByNullPrecedence;
import org.springframework.data.relational.core.sql.IdentifierProcessing;
import org.springframework.data.relational.core.sql.SimpleFunction;
import org.springframework.data.relational.core.sql.render.SelectRenderContext;

import java.util.Collection;
import java.util.Set;

/**
 * Database-neutral identifier rendering (docs/cloud-code-next-goal.md §3.2).
 *
 * <p>Wraps the vendor dialect detected at startup and switches identifier rendering to
 * UNQUOTED + AS-IS. Unquoted identifiers are folded by each engine to its storage case —
 * PostgreSQL folds to lower case (matching the deployed history), H2 folds to upper case
 * (matching the H2 baseline DDL) — so the very same Spring Data JDBC entities map correctly on
 * both management databases without compatibility modes. Every other dialect behavior (type
 * converters, id generation, limit/lock clauses) is delegated unchanged to the vendor dialect.
 */
public class NeutralIdentifierDialect implements JdbcDialect {

    private static final IdentifierProcessing NEUTRAL =
            IdentifierProcessing.create(IdentifierProcessing.Quoting.NONE, IdentifierProcessing.LetterCasing.AS_IS);

    private final Dialect delegate;

    public NeutralIdentifierDialect(Dialect delegate) {
        this.delegate = delegate;
    }

    @Override
    public IdentifierProcessing getIdentifierProcessing() {
        return NEUTRAL;
    }

    @Override
    public LimitClause limit() {
        return delegate.limit();
    }

    @Override
    public LockClause lock() {
        return delegate.lock();
    }

    @Override
    public SelectRenderContext getSelectContext() {
        return delegate.getSelectContext();
    }

    @Override
    public JdbcArrayColumns getArraySupport() {
        if (delegate instanceof JdbcDialect jdbcDialect) {
            return jdbcDialect.getArraySupport();
        }
        return JdbcArrayColumns.DefaultSupport.INSTANCE;
    }

    @Override
    public Escaper getLikeEscaper() {
        return delegate.getLikeEscaper();
    }

    @Override
    public IdGeneration getIdGeneration() {
        return delegate.getIdGeneration();
    }

    @Override
    public Collection<Object> getConverters() {
        return delegate.getConverters();
    }

    @Override
    public Set<Class<?>> simpleTypes() {
        return delegate.simpleTypes();
    }

    @Override
    public InsertRenderContext getInsertRenderContext() {
        return delegate.getInsertRenderContext();
    }

    @Override
    public OrderByNullPrecedence orderByNullHandling() {
        return delegate.orderByNullHandling();
    }

    @Override
    public SimpleFunction getExistsFunction() {
        return delegate.getExistsFunction();
    }

    @Override
    public boolean supportsSingleQueryLoading() {
        return delegate.supportsSingleQueryLoading();
    }
}
