package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.BaseOrder;
import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.OCOOrder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Manages One-Cancels-Other (OCO) orders.
 * Single Responsibility Principle: Handles only OCO order operations.
 */
@Component
@Slf4j
@Getter
public class OCOOrderManager {

    private final Map<String, OCOOrder> ocoOrders = new HashMap<>();

    /**
     * Adds an OCO order group
     */
    public void addOCOOrder(OCOOrder ocoOrder) {
        ocoOrders.put(ocoOrder.getOcoGroupId(), ocoOrder);
        log.info("Added OCO order group: {}", ocoOrder.getOcoGroupId());
    }

    /**
     * Removes an OCO order group
     */
    public OCOOrder removeOCOOrder(String ocoGroupId) {
        OCOOrder removed = ocoOrders.remove(ocoGroupId);
        if (removed != null) {
            log.info("Removed OCO order group: {}", ocoGroupId);
        }
        return removed;
    }

    /**
     * Finds the OCO order group containing a specific order
     */
    public OCOOrder findOCOOrderContaining(BaseOrder order) {
        for (OCOOrder oco : ocoOrders.values()) {
            if (order.getOrderId().equals(oco.getPrimaryOrder().getOrderId()) ||
                    order.getOrderId().equals(oco.getSecondaryOrder().getOrderId())) {
                return oco;
            }
        }
        return null;
    }

    /**
     * Gets the counterpart order in an OCO group
     */
    public BaseOrder getCounterpartOrder(OCOOrder ocoOrder, BaseOrder executedOrder) {
        if (executedOrder.getOrderId().equals(ocoOrder.getPrimaryOrder().getOrderId())) {
            return ocoOrder.getSecondaryOrder();
        } else if (executedOrder.getOrderId().equals(ocoOrder.getSecondaryOrder().getOrderId())) {
            return ocoOrder.getPrimaryOrder();
        }
        return null;
    }

    /**
     * Marks which leg of the OCO was triggered
     */
    public void markLegAsTriggered(OCOOrder ocoOrder, BaseOrder triggeredOrder) {
        if (triggeredOrder.getOrderId().equals(ocoOrder.getPrimaryOrder().getOrderId())) {
            ocoOrder.setPrimaryTriggered(true);
            log.info("OCO primary order {} triggered in group {}",
                    triggeredOrder.getOrderId(), ocoOrder.getOcoGroupId());
        } else if (triggeredOrder.getOrderId().equals(ocoOrder.getSecondaryOrder().getOrderId())) {
            ocoOrder.setSecondaryTriggered(true);
            log.info("OCO secondary order {} triggered in group {}",
                    triggeredOrder.getOrderId(), ocoOrder.getOcoGroupId());
        }
    }

    /**
     * Checks if an order is part of an OCO group
     */
    public boolean isPartOfOCO(BaseOrder order) {
        return findOCOOrderContaining(order) != null;
    }

    /**
     * Gets all OCO order groups
     */
    public Collection<OCOOrder> getAllOCOOrders() {
        return new ArrayList<>(ocoOrders.values());
    }

    /**
     * Gets an OCO order by group ID
     */
    public OCOOrder getOCOOrder(String groupId) {
        return ocoOrders.get(groupId);
    }

    /**
     * Checks if OCO orders collection is empty
     */
    public boolean isEmpty() {
        return ocoOrders.isEmpty();
    }

    /**
     * Prints the current state of OCO orders
     */
    public void printState() {
        if (!ocoOrders.isEmpty()) {
            log.info("OCO Order Groups:");
            ocoOrders.forEach((groupId, oco) -> {
                log.info("OCO Group {}: Primary={} ({}), Secondary={} ({}), PrimaryTriggered={}, SecondaryTriggered={}",
                        groupId,
                        oco.getPrimaryOrder().getOrderId(),
                        oco.getPrimaryOrder().getClass().getSimpleName(),
                        oco.getSecondaryOrder().getOrderId(),
                        oco.getSecondaryOrder().getClass().getSimpleName(),
                        oco.isPrimaryTriggered(),
                        oco.isSecondaryTriggered());
            });
        }
    }
}
