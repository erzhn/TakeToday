package com.example.taketoday.refund;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Доводит до конца возвраты, которые остались PENDING: сайт упал между отменой и обращением к платёжной службе
 * или служба была недоступна (п. 5.3.1). После {@link RefundService#MAX_ATTEMPTS} попыток — FAILED, нужен администратор.
 */
@Component
@ConditionalOnProperty(name = "app.refunds.job-enabled", havingValue = "true", matchIfMissing = true)
public class RefundJob {

    private final RefundService refunds;

    public RefundJob(RefundService refunds) {
        this.refunds = refunds;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void retryPending() {
        refunds.processAll(refunds.pending(20));
    }
}
