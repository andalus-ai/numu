package tech.kayys.syirkah.accounting.application.maintenance;

import tech.kayys.syirkah.accounting.domain.maintenance.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MaintenanceService {

    private final Map<String, MaintenanceWorkOrder> workOrders = new ConcurrentHashMap<>();
    private final Map<String, MaintenanceSchedule> schedules = new ConcurrentHashMap<>();
    private final Map<String, MaintenancePlan> plans = new ConcurrentHashMap<>();
    private final Map<String, List<MeterReading>> readingsByAsset = new ConcurrentHashMap<>();

    // ── Work Orders ───────────────────────────────────────────────────────────

    public MaintenanceWorkOrder createWorkOrder(String assetId, String description,
                                                MaintenanceWorkOrder.Priority priority) {
        var wo = new MaintenanceWorkOrder(UUID.randomUUID().toString(), assetId, description, priority);
        workOrders.put(wo.id(), wo);
        return wo;
    }

    public MaintenanceWorkOrder createWorkOrder(String assetId, String description, LocalDate scheduledDate) {
        return createWorkOrder(assetId, description, MaintenanceWorkOrder.Priority.MEDIUM);
    }

    public MaintenanceWorkOrder getWorkOrder(String id) {
        var wo = workOrders.get(id);
        if (wo == null) throw new IllegalArgumentException("WorkOrder not found: " + id);
        return wo;
    }

    public List<MaintenanceWorkOrder> listWorkOrders() {
        return List.copyOf(workOrders.values());
    }

    public void addPartToWorkOrder(String woId, String partNumber, String desc, int qty, BigDecimal unitCost) {
        var wo = getWorkOrder(woId);
        wo.addPart(new WorkOrderPart(partNumber, desc, qty, unitCost));
    }

    public void addLaborToWorkOrder(String woId, String technician, double hours, BigDecimal rate) {
        var wo = getWorkOrder(woId);
        wo.addLabor(new WorkOrderLabor(technician, hours, rate));
    }

    // ── Schedules & Plans ─────────────────────────────────────────────────────

    public MaintenanceSchedule registerSchedule(String assetId, String maintenanceType, int intervalDays) {
        var schedule = new MaintenanceSchedule(UUID.randomUUID().toString(), assetId,
                maintenanceType, intervalDays, true);
        schedules.put(schedule.scheduleId(), schedule);
        return schedule;
    }

    public MaintenanceSchedule registerSchedule(String assetId, int intervalDays, String taskDescription) {
        return registerSchedule(assetId, taskDescription, intervalDays);
    }

    public List<MaintenanceSchedule> listSchedules() {
        return List.copyOf(schedules.values());
    }

    public MaintenancePlan createPlan(String planId, String assetId, String title, int frequencyDays,
                                      MaintenanceType type, double stdHours, BigDecimal estCost) {
        var plan = new MaintenancePlan(MaintenancePlanId.of(planId), assetId, title, frequencyDays, type, stdHours, estCost);
        plans.put(planId, plan);
        return plan;
    }

    // ── Meter Readings ────────────────────────────────────────────────────────

    public MeterReading recordMeterReading(String assetId, MeterUnit unit, double value, String recordedBy) {
        var reading = new MeterReading(MeterReadingId.newId(), assetId, unit, value, recordedBy);
        readingsByAsset.computeIfAbsent(assetId, k -> new ArrayList<>()).add(reading);
        return reading;
    }

    public List<MeterReading> getMeterReadings(String assetId) {
        return List.copyOf(readingsByAsset.getOrDefault(assetId, Collections.emptyList()));
    }
}
