package com.practice.demo.client;

import com.practice.demo.client.dto.YahooFinanceQuoteResponse;
import com.practice.demo.client.dto.YahooFinanceQuoteResponse.YahooQuote;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Low-level HTTP client for the Yahoo Finance v7 quote API.
 *
 * Session management (2025+ Yahoo Finance requirement):
 *   Step 1 — GET consent URL  → obtains session cookie
 *   Step 2 — GET crumb URL    → obtains crumb token (requires cookie)
 *   Step 3 — GET quote URL    → append crumb as query param + send cookie
 *
 * If Yahoo Finance responds with 401/403 the client refreshes its session
 * automatically and retries the request exactly once.
 */
@Component
public class YahooFinanceClient {

    private static final Logger logger = LoggerFactory.getLogger(YahooFinanceClient.class);

    // Common browser headers that Yahoo Finance requires to serve API responses
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36";

    private static final String QUOTE_FIELDS =
            "symbol,shortName,longName," +
            "regularMarketPrice,regularMarketChange,regularMarketChangePercent," +
            "regularMarketOpen,regularMarketDayHigh,regularMarketDayLow," +
            "regularMarketPreviousClose,regularMarketVolume,currency,marketState";

    @Value("${yahoo.finance.quote-url}")
    private String quoteUrl;

    @Value("${yahoo.finance.crumb-url}")
    private String crumbUrl;

    @Value("${yahoo.finance.consent-url}")
    private String consentUrl;

    private final RestTemplate restTemplate;

    // Volatile — written by session-init thread, read by fetch threads
    private volatile String sessionCookie = null;
    private volatile String crumb         = null;

    public YahooFinanceClient(@Qualifier("stockRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @PostConstruct
    public void init() {
        try {
            initSession();
        } catch (Exception ex) {
            logger.warn("Yahoo Finance session initialisation failed at startup — " +
                        "will retry on first quote fetch. Reason: {}", ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Fetches live quotes for all supplied symbols in a single HTTP call.
     * Returns an empty list (never null) when the API is unreachable.
     */
    public List<YahooQuote> fetchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyList();
        }

        // Lazy session init
        if (sessionCookie == null || crumb == null) {
            logger.info("Session not initialised — attempting init before fetch");
            initSession();
        }

        try {
            return doFetch(symbols);

        } catch (HttpClientErrorException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                logger.warn("Yahoo Finance returned {} — refreshing session and retrying", status);
                initSession();
                return doFetch(symbols);
            }
            logger.error("Yahoo Finance API error {}: {}", status, ex.getMessage());
            return Collections.emptyList();

        } catch (RestClientException ex) {
            logger.error("Network error fetching stock quotes: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    // -----------------------------------------------------------------------
    // Session management
    // -----------------------------------------------------------------------

    /**
     * Acquires a fresh session cookie + crumb from Yahoo Finance.
     * Synchronized so concurrent callers don't trigger duplicate inits.
     */
    private synchronized void initSession() {
        logger.info("Initialising Yahoo Finance session (cookie → crumb)");

        // ── Step 1: consent endpoint ────────────────────────────────────────
        try {
            ResponseEntity<String> consentResp = restTemplate.exchange(
                    consentUrl, HttpMethod.GET,
                    new HttpEntity<>(baseHeaders()), String.class);

            String cookie = extractCookie(consentResp.getHeaders());
            if (cookie == null || cookie.isBlank()) {
                logger.warn("No Set-Cookie received from Yahoo Finance consent endpoint");
                // Some environments skip the consent step — proceed without cookie
            } else {
                this.sessionCookie = cookie;
                logger.debug("Session cookie acquired ({} chars)", cookie.length());
            }
        } catch (Exception ex) {
            logger.warn("Consent step failed ({}). Proceeding without cookie.", ex.getMessage());
        }

        // ── Step 2: crumb endpoint ───────────────────────────────────────────
        try {
            HttpHeaders crumbHeaders = baseHeaders();
            if (sessionCookie != null) {
                crumbHeaders.set(HttpHeaders.COOKIE, sessionCookie);
            }

            ResponseEntity<String> crumbResp = restTemplate.exchange(
                    crumbUrl, HttpMethod.GET,
                    new HttpEntity<>(crumbHeaders), String.class);

            String crumbValue = crumbResp.getBody();
            if (crumbValue != null && !crumbValue.isBlank()) {
                this.crumb = crumbValue.trim();
                logger.info("Yahoo Finance session ready — crumb acquired");
            } else {
                logger.warn("Crumb endpoint returned an empty body");
            }
        } catch (Exception ex) {
            logger.warn("Crumb step failed: {}. Quotes will be attempted without crumb.", ex.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Internal fetch
    // -----------------------------------------------------------------------

    private List<YahooQuote> doFetch(List<String> symbols) {
        String symbolParam = symbols.stream()
                .map(s -> s.replace("&", "%26"))   // M&M.NS → M%26M.NS
                .collect(Collectors.joining(","));

        StringBuilder urlBuilder = new StringBuilder(quoteUrl)
                .append("?symbols=").append(symbolParam)
                .append("&fields=").append(QUOTE_FIELDS);

        if (crumb != null && !crumb.isBlank()) {
            urlBuilder.append("&crumb=")
                      .append(URLEncoder.encode(crumb, StandardCharsets.UTF_8));
        }

        String url = urlBuilder.toString();
        logger.debug("Calling Yahoo Finance: {}", quoteUrl + "?symbols=<{} stocks>", symbols.size());

        HttpHeaders headers = baseHeaders();
        if (sessionCookie != null) {
            headers.set(HttpHeaders.COOKIE, sessionCookie);
        }

        ResponseEntity<YahooFinanceQuoteResponse> response = restTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(headers),
                YahooFinanceQuoteResponse.class);

        YahooFinanceQuoteResponse body = response.getBody();
        if (body == null
                || body.getQuoteResponse() == null
                || body.getQuoteResponse().getResult() == null) {
            logger.warn("Yahoo Finance returned an empty/null result payload");
            return Collections.emptyList();
        }

        List<YahooQuote> quotes = body.getQuoteResponse().getResult();
        logger.info("Yahoo Finance returned {} quote(s) for {} requested symbol(s)",
                quotes.size(), symbols.size());
        return quotes;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private HttpHeaders baseHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.USER_AGENT,      USER_AGENT);
        h.set(HttpHeaders.ACCEPT,          "application/json, text/plain, */*");
        h.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        h.set("Referer",                   "https://finance.yahoo.com");
        return h;
    }

    private String extractCookie(HttpHeaders headers) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null || setCookies.isEmpty()) return null;
        // Join multiple Set-Cookie values into a single Cookie header value
        return setCookies.stream()
                .map(c -> c.split(";")[0])   // take only name=value, drop path/expires
                .collect(Collectors.joining("; "));
    }
}
