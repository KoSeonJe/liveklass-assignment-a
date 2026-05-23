package com.liveklass.assignment.domain.payment;

public sealed interface GatewayResult {
    record Success(String externalPaymentKey) implements GatewayResult {}

    record Failure(String reason) implements GatewayResult {}
}
