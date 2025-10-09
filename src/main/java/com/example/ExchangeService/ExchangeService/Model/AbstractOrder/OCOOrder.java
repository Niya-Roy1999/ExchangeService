package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import com.example.ExchangeService.ExchangeService.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class OCOOrder extends BaseOrder{

    private String ocoGroupId;
    private BaseOrder primaryOrder;
    private BaseOrder secondaryOrder;
    private boolean isPrimaryTriggered = false;
    private boolean isSecondaryTriggered = false;
    private String cancelledOrderId;

    public OCOOrder(String ocoGroupId, BaseOrder primaryOrder, BaseOrder secondaryOrder) {
        super(OrderType.ONE_CANCELS_OTHER);
        this.ocoGroupId = ocoGroupId;
        this.primaryOrder = primaryOrder;
        this.secondaryOrder = secondaryOrder;

        this.setOrderId(ocoGroupId);
        this.setUserId(primaryOrder.getUserId());
        this.setInstrumentId(primaryOrder.getInstrumentId());
        this.setOrderSide(primaryOrder.getOrderSide());
        this.setQuantity(primaryOrder.getQuantity());
        this.setTimeInForce(primaryOrder.getTimeInForce());
        this.setTimestamp(primaryOrder.getTimestamp());
        this.setGoodTillDate(primaryOrder.getGoodTillDate());
        this.setExpiryTime(primaryOrder.getExpiryTime());
    }

    public boolean isAnyOrderFilled() {
        return isPrimaryTriggered || isSecondaryTriggered;
    }

    public BaseOrder getActiveOrder() {
        if(isPrimaryTriggered) return primaryOrder;
        if(isSecondaryTriggered) return secondaryOrder;
        return null;
    }

    @Override
    public boolean isFullyFilled() {
        BaseOrder active = getActiveOrder();
        return active != null && active.isFullyFilled();
    }

    @Override
    public int getRemainingQuantity() {
        BaseOrder active = getActiveOrder();
        return active != null ? active.getRemainingQuantity() : getQuantity();
    }

    public int getFilledQuantity() {
        BaseOrder active = getActiveOrder();
        return active != null ? active.getFilledQuantity() : 0;
    }
}
