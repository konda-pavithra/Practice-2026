package com.practice.demo.consumer;

import com.practice.demo.config.RabbitMQConfig;
import com.practice.demo.dto.StockAlertMessage;
import com.practice.demo.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Alert Consumer (consumer side of the RabbitMQ pipeline).
 *
 * <h3>Responsibility</h3>
 * Listens to {@code stock.alerts.queue}, deserializes each {@link StockAlertMessage}
 * from JSON, and delegates to {@link EmailService} to send the HTML alert email.
 *
 * <h3>Error handling</h3>
 * If {@link EmailService#sendThresholdAlert} throws (e.g. SMTP failure), the
 * exception propagates and the RabbitMQ listener container applies the retry
 * policy configured in {@link RabbitMQConfig}:
 * <ol>
 *   <li>Attempt 1 — immediate</li>
 *   <li>Attempt 2 — after 1 s</li>
 *   <li>Attempt 3 — after 2 s</li>
 *   <li>All attempts exhausted → message rejected, routed to {@code stock.alerts.dlq}</li>
 * </ol>
 *
 * <h3>Idempotency note</h3>
 * The alert generator suppresses duplicate alerts via a cooldown window, so
 * re-delivery of a message from the DLQ is safe to process.
 */
@Component
public class StockAlertConsumer {

    private static final Logger logger = LoggerFactory.getLogger(StockAlertConsumer.class);

    private final EmailService emailService;

    public StockAlertConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Receives a deserialized {@link StockAlertMessage} from RabbitMQ and sends
     * the corresponding HTML alert email to the user.
     *
     * <p>Jackson2JsonMessageConverter (configured in {@link RabbitMQConfig}) handles
     * JSON deserialization automatically based on the {@code __TypeId__} AMQP header.
     *
     * @param message the alert payload published by {@link com.practice.demo.scheduler.AlertGeneratorScheduler}
     * @throws Exception re-thrown on email failure so the retry + DLQ policy applies
     */
    @RabbitListener(
        queues             = RabbitMQConfig.ALERT_QUEUE,
        containerFactory   = "rabbitListenerContainerFactory"
    )
    public void handleAlert(StockAlertMessage message) throws Exception {

        logger.info("Received alert message — user='{}', symbol='{}', type={}, currentPrice=₹{}",
                message.getUsername(),
                message.getDisplaySymbol(),
                message.getAlertType(),
                message.getCurrentPrice());

        try {
            emailService.sendThresholdAlert(message);

            logger.info("Alert email sent successfully → '{}' for '{}' ({} breach, P&L: {}₹{})",
                    message.getUserEmail(),
                    message.getDisplaySymbol(),
                    message.getAlertType(),
                    message.isGain() ? "+" : "-",
                    message.getProfitLoss().abs());

        } catch (Exception ex) {
            logger.error("Failed to send alert email to '{}' for '{}' (type={}): {}",
                    message.getUserEmail(),
                    message.getDisplaySymbol(),
                    message.getAlertType(),
                    ex.getMessage(), ex);

            // Re-throw so the container's retry interceptor handles retries → DLQ
            throw ex;
        }
    }
}
