package com.liveklass.assignment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockPaymentGatewayTest {

    @Test
    @DisplayName("기본 상태에서 charge는 Success를 반환한다")
    void charge_returns_success_by_default() {
        MockPaymentGateway gateway = new MockPaymentGateway();

        GatewayResult result = gateway.charge(1L, 10_000);

        assertThat(result).isInstanceOf(GatewayResult.Success.class);
    }

    @Test
    @DisplayName("Success의 externalPaymentKey는 비어 있지 않다")
    void success_external_key_is_not_blank() {
        MockPaymentGateway gateway = new MockPaymentGateway();

        GatewayResult result = gateway.charge(1L, 10_000);

        assertThat(result).isInstanceOf(GatewayResult.Success.class);
        GatewayResult.Success success = (GatewayResult.Success) result;
        assertThat(success.externalPaymentKey()).isNotBlank();
    }

    @Test
    @DisplayName("setNextResult로 Failure를 주입하면 charge는 Failure를 반환한다")
    void charge_returns_injected_failure() {
        MockPaymentGateway gateway = new MockPaymentGateway();
        gateway.setNextResult(new GatewayResult.Failure("decline"));

        GatewayResult result = gateway.charge(1L, 10_000);

        assertThat(result).isInstanceOf(GatewayResult.Failure.class);
        assertThat(((GatewayResult.Failure) result).reason()).isEqualTo("decline");
    }

    @Test
    @DisplayName("reset 호출 시 기본 Success 동작으로 복귀한다")
    void reset_restores_default_success() {
        MockPaymentGateway gateway = new MockPaymentGateway();
        gateway.setNextResult(new GatewayResult.Failure("decline"));

        gateway.reset();
        GatewayResult result = gateway.charge(1L, 10_000);

        assertThat(result).isInstanceOf(GatewayResult.Success.class);
    }
}
