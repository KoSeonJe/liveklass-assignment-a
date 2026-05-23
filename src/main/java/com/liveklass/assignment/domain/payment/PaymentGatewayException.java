package com.liveklass.assignment.domain.payment;

public class PaymentGatewayException extends RuntimeException {

    private final String reason;

    public PaymentGatewayException(String reason) {
        super("결제 게이트웨이 호출이 실패했습니다. reason=" + reason);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
