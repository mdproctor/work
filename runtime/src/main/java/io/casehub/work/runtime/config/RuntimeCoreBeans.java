package io.casehub.work.runtime.config;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.routing.StrategyResolver;
import io.casehub.work.api.spi.ExclusionPolicy;
import io.casehub.work.api.spi.HolidayCalendar;
import io.casehub.work.api.spi.SlaBreachPolicy;
import io.casehub.work.api.spi.WorkerRegistry;
import io.casehub.work.api.spi.WorkloadProvider;
import io.casehub.work.runtime.calendar.DefaultBusinessCalendar;
import io.casehub.work.runtime.service.CommaSeparatedExclusionPolicy;
import io.casehub.work.runtime.service.FormSchemaValidationService;
import io.casehub.work.runtime.service.NoOpSlaBreachPolicy;
import io.casehub.work.runtime.service.OutcomeValidator;
import io.casehub.work.runtime.service.WorkItemAssignmentService;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class RuntimeCoreBeans {

    @Produces
    @ApplicationScoped
    public WorkItemAssignmentService workItemAssignmentService(
            final StrategyResolver strategyResolver,
            final WorkItemsConfig config,
            final WorkerRegistry workerRegistry,
            final WorkloadProvider workloadProvider,
            final ExclusionPolicy exclusionPolicy) {
        return new WorkItemAssignmentService(
                strategyResolver,
                config.routing().strategy(),
                workerRegistry,
                workloadProvider,
                exclusionPolicy);
    }

    @Produces
    @ApplicationScoped
    public DefaultBusinessCalendar defaultBusinessCalendar(
            final WorkItemsConfig config,
            final HolidayCalendar holidayCalendar) {
        return new DefaultBusinessCalendar(
                config.businessHours().timezone(),
                config.businessHours().start(),
                config.businessHours().end(),
                config.businessHours().workDays(),
                holidayCalendar);
    }

    @Produces
    @Unremovable
    @ApplicationScoped
    public NoOpSlaBreachPolicy noOpSlaBreachPolicy() {
        return new NoOpSlaBreachPolicy();
    }

    @Produces
    @DefaultBean
    public CommaSeparatedExclusionPolicy commaSeparatedExclusionPolicy() {
        return new CommaSeparatedExclusionPolicy();
    }

    @Produces
    @ApplicationScoped
    public FormSchemaValidationService formSchemaValidationService() {
        return new FormSchemaValidationService();
    }

    @Produces
    @ApplicationScoped
    public OutcomeValidator outcomeValidator(
            final ExpressionEngineRegistry expressionRegistry) {
        return new OutcomeValidator(expressionRegistry);
    }
}
