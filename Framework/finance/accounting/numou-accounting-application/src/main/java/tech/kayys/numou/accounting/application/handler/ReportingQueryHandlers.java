package tech.kayys.syirkah.accounting.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.query.*;
import tech.kayys.syirkah.accounting.application.cqrs.QueryHandler;
import tech.kayys.syirkah.accounting.application.service.FinancialReportingService;
import tech.kayys.syirkah.accounting.domain.report.*;

import java.util.Objects;

public class ReportingQueryHandlers {

    public static class TrialBalanceHandler implements QueryHandler<GetTrialBalanceQuery, TrialBalance> {
        private final FinancialReportingService reportingService;
        public TrialBalanceHandler(FinancialReportingService reportingService) {
            this.reportingService = Objects.requireNonNull(reportingService);
        }
        @Override
        public Uni<TrialBalance> handle(GetTrialBalanceQuery query) {
            return reportingService.generateTrialBalance(query.asOfDate());
        }
    }

    public static class BalanceSheetHandler implements QueryHandler<GetBalanceSheetQuery, BalanceSheet> {
        private final FinancialReportingService reportingService;
        public BalanceSheetHandler(FinancialReportingService reportingService) {
            this.reportingService = Objects.requireNonNull(reportingService);
        }
        @Override
        public Uni<BalanceSheet> handle(GetBalanceSheetQuery query) {
            return reportingService.generateBalanceSheet(query.asOfDate());
        }
    }

    public static class IncomeStatementHandler implements QueryHandler<GetIncomeStatementQuery, IncomeStatement> {
        private final FinancialReportingService reportingService;
        public IncomeStatementHandler(FinancialReportingService reportingService) {
            this.reportingService = Objects.requireNonNull(reportingService);
        }
        @Override
        public Uni<IncomeStatement> handle(GetIncomeStatementQuery query) {
            return reportingService.generateIncomeStatement(query.period());
        }
    }

    public static class CashFlowHandler implements QueryHandler<GetCashFlowQuery, CashFlowStatement> {
        private final FinancialReportingService reportingService;
        public CashFlowHandler(FinancialReportingService reportingService) {
            this.reportingService = Objects.requireNonNull(reportingService);
        }
        @Override
        public Uni<CashFlowStatement> handle(GetCashFlowQuery query) {
            return reportingService.generateCashFlowStatement(query.period());
        }
    }
}
