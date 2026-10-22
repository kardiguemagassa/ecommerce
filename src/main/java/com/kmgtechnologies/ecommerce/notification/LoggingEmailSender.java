package com.kmgtechnologies.ecommerce.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final Duration simulatedDelay;

    public LoggingEmailSender(@Value("${app.notification.simulated-delay:PT2S}") Duration simulatedDelay) {
        this.simulatedDelay = simulatedDelay;
    }

    @Override
    public void sendOrderConfirmation(String to, Long orderId, BigDecimal totalAmount) {
        log.info("Sending confirmation of order {} ({}) to {}", orderId, totalAmount, mask(to));
        try {
            Thread.sleep(simulatedDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending confirmation of order " + orderId, e);
        }
        log.info("Confirmation of order {} sent", orderId);
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}