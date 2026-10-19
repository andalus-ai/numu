package tech.kayys.numou.asset.application.fixedasset;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.asset.domain.fixedasset.*;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.spi.port.FixedAssetStorePort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class FixedAssetServiceTest {

    @Test
    void acquire_activate_depreciate_service_flow() {
        var service = new FixedAssetService(new TestFixedAssetStore());
        var id = AssetId.generate();

        service.acquire(id, "AST-10", "Office Server", AssetCategory.IT_EQUIPMENT,
                new BigDecimal("12000"), BigDecimal.ZERO, 12,
                DepreciationMethod.STRAIGHT_LINE, LocalDate.of(2026, 1, 1));

        service.activate(id);
        BigDecimal depr = service.runMonthlyDepreciation(id);

        assertEquals(new BigDecimal("1000.0000"), depr);
        assertEquals(new BigDecimal("11000.0000"), service.get(id).bookValue());
    }

    private static final class TestFixedAssetStore implements FixedAssetStorePort {
        private final Map<AssetId, FixedAssetAggregate> assets = new ConcurrentHashMap<>();
        private final Map<LeaseId, LeaseContract> leases = new ConcurrentHashMap<>();
        private final Map<CipId, ConstructionProject> projects = new ConcurrentHashMap<>();

        @Override
        public Optional<FixedAssetAggregate> findAsset(AssetId id) {
            return Optional.ofNullable(assets.get(id));
        }

        @Override
        public void saveAsset(FixedAssetAggregate asset) {
            assets.put(asset.id(), asset);
        }

        @Override
        public Optional<LeaseContract> findLease(LeaseId id) {
            return Optional.ofNullable(leases.get(id));
        }

        @Override
        public void saveLease(LeaseContract lease) {
            leases.put(lease.id(), lease);
        }

        @Override
        public Optional<ConstructionProject> findConstructionProject(CipId id) {
            return Optional.ofNullable(projects.get(id));
        }

        @Override
        public void saveConstructionProject(ConstructionProject project) {
            projects.put(project.id(), project);
        }
    }
}
