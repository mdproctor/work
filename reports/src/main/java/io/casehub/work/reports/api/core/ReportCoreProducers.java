package io.casehub.work.reports.api.core;

import io.casehub.work.reports.service.ReportService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ReportCoreProducers {

    @Produces
    @ApplicationScoped
    public ReportCore reportCore(ReportService reportService) {
        return new ReportCore(reportService);
    }
}
