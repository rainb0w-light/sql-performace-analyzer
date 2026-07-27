package com.biz.sccba.sqlanalyzer.persistence.jdbc.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base for entities whose id is assigned by the application before insert (all product ids are
 * stable prefixed business ids such as {@code client_…}, {@code session_…}). Implements
 * {@link Persistable} so Spring Data JDBC issues INSERT (not UPDATE) for freshly built entities.
 */
public abstract class AssignedIdEntity implements Persistable<String> {

    @Id
    private String id;

    @Transient
    private boolean isNew;

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markNew() {
        this.isNew = true;
    }

    public void markPersisted() {
        this.isNew = false;
    }
}
