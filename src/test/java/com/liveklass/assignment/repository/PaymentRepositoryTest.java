package com.liveklass.assignment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liveklass.assignment.domain.payment.Payment;
import com.liveklass.assignment.support.AbstractRepositoryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class PaymentRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    @DisplayName("동일 idempotency_key로 두 번 저장 시 DataIntegrityViolationException이 발생한다")
    void duplicate_idempotency_key_throws() {
        paymentRepository.saveAndFlush(Payment.create(1L, "dup-key"));

        assertThatThrownBy(() ->
                paymentRepository.saveAndFlush(Payment.create(2L, "dup-key"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("서로 다른 idempotency_key는 정상 저장된다")
    void distinct_idempotency_keys_persist() {
        Payment a = paymentRepository.saveAndFlush(Payment.create(1L, "key-a"));
        Payment b = paymentRepository.saveAndFlush(Payment.create(2L, "key-b"));

        assertThat(a.getId()).isNotNull();
        assertThat(b.getId()).isNotNull();
        assertThat(a.getId()).isNotEqualTo(b.getId());
    }
}
