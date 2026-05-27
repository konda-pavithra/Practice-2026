package com.practice.demo.service;

import com.practice.demo.dto.StockAlertMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Composes and sends HTML alert emails using Spring's {@link JavaMailSender}.
 *
 * <p>Called exclusively by {@link com.practice.demo.consumer.StockAlertConsumer}
 * after a {@link StockAlertMessage} is received from RabbitMQ.
 *
 * <h3>Design</h3>
 * All dynamic values are pre-formatted into plain strings before being inserted
 * into the HTML template via a simple {@link String#formatted} call.  This avoids
 * ambiguity around {@code %} characters inside BigDecimal / percentage values.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${alert.mail.from-name:Stock Price Alerts}")
    private String fromName;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Sends an HTML price-alert email to the user identified in {@code message}.
     *
     * @throws MessagingException if the SMTP transport fails (the caller — the
     *         RabbitMQ consumer — re-throws so the retry/DLQ policy applies)
     */
    public void sendThresholdAlert(StockAlertMessage message) throws MessagingException {
        String subject = buildSubject(message);
        String body    = buildHtmlBody(message);

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

        helper.setFrom(fromAddress, fromName);
        helper.setTo(message.getUserEmail());
        helper.setSubject(subject);
        helper.setText(body, true); // true → send as HTML

        mailSender.send(mime);

        logger.info("Alert email sent to '{}' for '{}' ({} breach)",
                message.getUserEmail(), message.getDisplaySymbol(), message.getAlertType());
    }

    // =========================================================================
    // Subject line
    // =========================================================================

    private String buildSubject(StockAlertMessage m) {
        if ("UPPER".equals(m.getAlertType())) {
            return "📈 Price Alert: %s crossed your upper threshold (+%s%%)".formatted(
                    m.getDisplaySymbol(), m.getThresholdPercent());
        } else {
            return "📉 Price Alert: %s crossed your lower threshold (-%s%%)".formatted(
                    m.getDisplaySymbol(), m.getThresholdPercent());
        }
    }

    // =========================================================================
    // HTML body
    // =========================================================================

    /**
     * Builds a fully inline-styled HTML email.
     *
     * <p>Strategy: pre-compute <em>all</em> dynamic display strings first, then
     * substitute them into the template with {@code %s} only.  This keeps the
     * template readable and eliminates any risk of {@code BigDecimal.toString()}
     * returning characters (like 'E') that could confuse a {@code %.2f} specifier.
     */
    private String buildHtmlBody(StockAlertMessage m) {

        // ── Boolean flags ──────────────────────────────────────────────────────
        boolean isUpper = "UPPER".equals(m.getAlertType());
        boolean isGain  = m.isGain();

        // ── Header styling ─────────────────────────────────────────────────────
        String headerBg    = isUpper ? "#e65c00" : "#b71c1c";
        String headerTitle = isUpper ? "📈 UPPER THRESHOLD CROSSED" : "📉 LOWER THRESHOLD CROSSED";
        String headerSub   = m.getCompanyName() + " has "
                           + (isUpper ? "risen above your upper threshold"
                                      : "fallen below your lower threshold");

        // ── P&L colour / symbol ────────────────────────────────────────────────
        String plColor = isGain ? "#1b5e20" : "#b71c1c";
        String plEmoji = isGain ? "✅" : "❌";

        // ── Pre-formatted display strings ──────────────────────────────────────
        String alertTypeLabel = (isUpper ? "Upper" : "Lower") + " threshold ("
                + (isUpper ? "▲ +" : "▼ -") + m.getThresholdPercent() + "%)";

        String refPriceStr   = "₹" + m.getReferencePrice();
        String alertPriceStr = "₹" + m.getAlertPrice();

        // Current price with % deviation from reference
        String currentPriceStr = buildCurrentPriceStr(m);

        String sharesStr     = m.getQuantity() + " shares";
        String buyPriceStr   = "₹" + m.getBuyingPrice() + " / share";
        String investStr     = "₹" + m.getInvestmentValue();
        String curValueStr   = "₹" + m.getCurrentValue();

        String sign          = isGain ? "+" : "-";
        BigDecimal absLoss   = m.getProfitLoss().abs().setScale(2, RoundingMode.HALF_UP);
        double     absPct    = Math.abs(m.getPlPercent());
        String plStr         = sign + "₹" + absLoss
                             + " &nbsp;(" + sign + String.format("%.2f", absPct) + "%) " + plEmoji;

        String alertedAt     = m.getAlertGeneratedAt() != null
                ? m.getAlertGeneratedAt().format(DISPLAY_FORMAT) + " IST"
                : "—";

        // ── Template ───────────────────────────────────────────────────────────
        // Only %s placeholders used — no risk of format-specifier collision.
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1.0"/>
                  <title>Stock Price Alert</title>
                </head>
                <body style="margin:0;padding:0;font-family:Arial,Helvetica,sans-serif;background:#f4f4f4;">
                  <table width="100%" cellpadding="0" cellspacing="0"
                         style="background:#f4f4f4;padding:20px 0;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:8px;overflow:hidden;
                                    box-shadow:0 2px 8px rgba(0,0,0,.1);">

                        <!-- Header -->
                        <tr>
                          <td style="background:%s;padding:24px 32px;text-align:center;">
                            <h1 style="margin:0;color:#fff;font-size:20px;letter-spacing:1px;">%s</h1>
                            <p style="margin:8px 0 0;color:#ffe0b2;font-size:14px;">%s</p>
                          </td>
                        </tr>

                        <!-- Stock identity -->
                        <tr>
                          <td style="padding:24px 32px 0;">
                            <h2 style="margin:0;color:#212121;font-size:22px;">
                              %s &nbsp;<span style="color:#757575;font-size:16px;font-weight:normal;">%s</span>
                            </h2>
                          </td>
                        </tr>

                        <!-- Threshold details -->
                        <tr>
                          <td style="padding:16px 32px;">
                            <table width="100%" cellpadding="0" cellspacing="0"
                                   style="background:#fafafa;border:1px solid #e0e0e0;border-radius:6px;">
                              %s
                              %s
                              %s
                              %s
                            </table>
                          </td>
                        </tr>

                        <!-- Portfolio impact -->
                        <tr>
                          <td style="padding:0 32px 16px;">
                            <table width="100%" cellpadding="0" cellspacing="0"
                                   style="background:#fafafa;border:1px solid #e0e0e0;border-radius:6px;">
                              %s
                              %s
                              %s
                              %s
                              <!-- P&L row -->
                              <tr>
                                <td style="padding:6px 20px 14px;">
                                  <table width="100%" cellpadding="0" cellspacing="0">
                                    <tr>
                                      <td style="color:#424242;font-size:14px;">Profit / Loss</td>
                                      <td align="right"
                                          style="font-size:15px;font-weight:bold;color:%s;">%s</td>
                                    </tr>
                                  </table>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f5f5f5;padding:16px 32px;
                                     border-top:1px solid #e0e0e0;">
                            <p style="margin:0;color:#9e9e9e;font-size:12px;text-align:center;">
                              Alert generated at: <strong>%s</strong>
                            </p>
                            <p style="margin:8px 0 0;color:#bdbdbd;font-size:11px;text-align:center;">
                              This is an automated price alert. Threshold levels are computed from the
                              reference price captured when you last saved your settings.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                        // Header
                        headerBg, headerTitle, headerSub,
                        // Stock identity
                        m.getCompanyName(), m.getDisplaySymbol(),
                        // Threshold detail rows
                        row("Alert Type",      alertTypeLabel),
                        row("Reference Price", refPriceStr),
                        row("Alert Level",     alertPriceStr),
                        row("Current Price",   currentPriceStr),
                        // Portfolio rows
                        row("Shares Held",    sharesStr),
                        row("Buying Price",   buyPriceStr),
                        row("Investment",     investStr),
                        row("Current Value",  curValueStr),
                        // P&L
                        plColor, plStr,
                        // Footer
                        alertedAt
                );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds the current-price display string with its % deviation from the
     * reference price in parentheses, e.g. {@code ₹2600.00 (+6.10% from ref)}.
     */
    private static String buildCurrentPriceStr(StockAlertMessage m) {
        String base = "₹" + m.getCurrentPrice();
        if (m.getReferencePrice() == null || m.getReferencePrice().compareTo(BigDecimal.ZERO) == 0) {
            return base;
        }
        BigDecimal pct = m.getCurrentPrice()
                .subtract(m.getReferencePrice())
                .divide(m.getReferencePrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        String sign = pct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return base + " <span style=\"color:#757575;font-size:12px;\">(" + sign + pct + "% from ref)</span>";
    }

    /**
     * Renders a label / value table row with consistent padding and typography.
     * Only {@code %s} placeholders are used to avoid escaping issues.
     */
    private static String row(String label, String value) {
        return """
               <tr>
                 <td style="padding:6px 20px;">
                   <table width="100%" cellpadding="0" cellspacing="0">
                     <tr>
                       <td style="color:#424242;font-size:14px;">%s</td>
                       <td align="right"
                           style="color:#212121;font-size:14px;font-weight:bold;">%s</td>
                     </tr>
                   </table>
                 </td>
               </tr>
               """.formatted(label, value);
    }
}
