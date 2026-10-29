package tech.kayys.numou.numouadapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.StaffRecordPort;
import tech.kayys.numou.numoudomain.StaffContract;
import tech.kayys.numou.numoudomain.SyirkahId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InMemoryStaffRecordPort implements StaffRecordPort {

    private final List<Row> rows = new ArrayList<>();

    private record Row(TenantId tenant, SyirkahId syirkah, Recorded recorded) {
    }

    @Override
    public synchronized int add(TenantId tenant, SyirkahId syirkah, int contractVersion, StaffContract staff,
                                Instant at) {
        int seq = 1 + (int) rows.stream().filter(r -> r.tenant().equals(tenant) && r.syirkah().equals(syirkah))
                .count();
        rows.add(new Row(tenant, syirkah, new Recorded(seq, contractVersion, staff, at)));
        return seq;
    }

    @Override
    public synchronized List<Recorded> list(TenantId tenant, SyirkahId syirkah) {
        return rows.stream().filter(r -> r.tenant().equals(tenant) && r.syirkah().equals(syirkah))
                .map(Row::recorded).toList();
    }
}
