package com.practice.demo.controller;

import com.practice.demo.dto.PortfolioRealtimeResponse;
import com.practice.demo.service.PortfolioRealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Real-time portfolio monitoring endpoints — require a valid JWT.
 *
 * <pre>
 * ┌─────────────────────────────────────┬────────────────────────────────────────┐
 * │ Endpoint                            │ Purpose                                │
 * ├─────────────────────────────────────┼────────────────────────────────────────┤
 * │ GET /api/portfolio/stream           │ SSE stream — live portfolio P&L        │
 * │ GET /api/portfolio/stream/snapshot  │ One-shot REST snapshot (no streaming)  │
 * └─────────────────────────────────────┴────────────────────────────────────────┘
 * </pre>
 *
 * <h3>SSE authentication</h3>
 * Browser {@code EventSource} does not support custom request headers.
 * Pass the JWT as a query parameter instead:
 * <pre>
 *   const es = new EventSource('/api/portfolio/stream?token=' + jwt);
 * </pre>
 * The {@link com.practice.demo.filter.JwtAuthenticationFilter} checks both the
 * {@code Authorization} header and the {@code token} query parameter, so both
 * REST clients and browser SSE clients are authenticated via the same filter.
 *
 * <h3>SSE event format</h3>
 * <pre>
 *   event: portfolio-update
 *   data: { &lt;PortfolioRealtimeResponse JSON&gt; }
 * </pre>
 * Events are sent:
 * <ul>
 *   <li>Immediately on connect (initial snapshot)</li>
 *   <li>After every Kafka price batch (every ~30 s during market hours)</li>
 * </ul>
 * An SSE comment ({@code :heartbeat}) is also sent every 30 s to prevent
 * proxy timeouts on idle connections.
 */
@RestController
@RequestMapping("/api/portfolio/stream")
public class PortfolioStreamController {

    private static final Logger logger = LoggerFactory.getLogger(PortfolioStreamController.class);

    private final PortfolioRealtimeService realtimeService;

    @Value("${portfolio.stream.emitter-timeout-ms:300000}")
    private long emitterTimeoutMs;

    public PortfolioStreamController(PortfolioRealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    // =========================================================================
    // SSE stream
    // =========================================================================

    /**
     * Opens a Server-Sent Events stream for the authenticated user.
     *
     * <p>The connection is kept open and a {@code portfolio-update} event is sent:
     * <ul>
     *   <li>Immediately, with the current portfolio valuation (so the UI is not
     *       blank while waiting for the next Kafka cycle)</li>
     *   <li>After each Kafka price batch (~every 30 s)</li>
     * </ul>
     *
     * <p>The {@link SseEmitter} times out after
     * {@code portfolio.stream.emitter-timeout-ms} (default 5 minutes).
     * The browser's native {@code EventSource} reconnects automatically.
     *
     * <p><b>Browser usage:</b>
     * <pre>
     *   const es = new EventSource('/api/portfolio/stream?token=' + jwt);
     *   es.addEventListener('portfolio-update', e =&gt; {
     *       const data = JSON.parse(e.data);
     *       renderPortfolio(data);
     *   });
     * </pre>
     *
     * @return an {@link SseEmitter}; Spring MVC keeps the response open and flushes events
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPortfolio(Authentication authentication) {
        String username = authentication.getName();
        logger.info("SSE connect — user '{}' (active connections: {})",
                username, realtimeService.activeConnectionCount() + 1);
        return realtimeService.register(username, emitterTimeoutMs);
    }

    // =========================================================================
    // One-shot REST snapshot (no streaming — useful for initial page load / mobile)
    // =========================================================================

    /**
     * Returns the current portfolio valuation as a regular JSON response.
     *
     * <p>Computes the same data as one SSE event but does not open a persistent
     * connection.  Useful for:
     * <ul>
     *   <li>Mobile / non-browser clients that prefer polling over SSE</li>
     *   <li>Initial page load before the SSE stream is established</li>
     *   <li>Testing the valuation logic without an SSE client</li>
     * </ul>
     *
     * <p>Authentication: standard {@code Authorization: Bearer <token>} header.
     */
    @GetMapping(value = "/snapshot", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PortfolioRealtimeResponse> getSnapshot(Authentication authentication) {
        String username = authentication.getName();
        logger.info("GET /api/portfolio/stream/snapshot — user '{}'", username);

        PortfolioRealtimeResponse snapshot = realtimeService.computeValuation(username);

        logger.info("Snapshot — user '{}': {} holdings, P&L=₹{} ({}%), dataStatus={}",
                username,
                snapshot.getTotalHoldings(),
                snapshot.getTotalProfitLoss(),
                snapshot.getTotalPLPercent(),
                snapshot.getDataStatus());

        return ResponseEntity.ok(snapshot);
    }
}
