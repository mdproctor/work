package io.casehub.work.runtime.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

/**
 * @deprecated Qualifier for {@link io.casehub.work.api.EscalationPolicy}, which is itself
 *             deprecated. No longer used — {@link io.casehub.work.api.SlaBreachPolicy}
 *             requires no qualifier.
 */
@Deprecated
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE })
public @interface ClaimEscalation {
}
