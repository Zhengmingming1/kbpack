package com.kbpack.common.id;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

/**
 * Hibernate identifier generator that produces time-ordered UUID v7 values.
 */
public class UuidV7JpaGenerator implements IdentifierGenerator {

    public static final String NAME = "uuid_v7";
    public static final String STRATEGY = "com.kbpack.common.id.UuidV7JpaGenerator";

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        return UuidV7Generator.generate();
    }
}
