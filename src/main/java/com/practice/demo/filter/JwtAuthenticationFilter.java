package com.practice.demo.filter;

import com.practice.demo.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Intercepts every request exactly once.
 * If a valid JWT is found the filter authenticates the request inside the
 * SecurityContext so that downstream handlers see an authenticated principal —
 * without touching the database.
 *
 * <h3>Token sources (checked in order)</h3>
 * <ol>
 *   <li><b>Authorization header</b> — {@code Authorization: Bearer <token>}
 *       Standard path used by all REST clients and mobile apps.</li>
 *   <li><b>{@code token} query parameter</b> — {@code ?token=<jwt>}
 *       Fallback for browser {@code EventSource} (SSE) connections, which
 *       cannot set custom headers.  Used exclusively by
 *       {@code GET /api/portfolio/stream}.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);
                logger.debug("JWT filter — authenticated request for username '{}' on [{}] {}",
                        username, request.getMethod(), request.getRequestURI());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                Collections.emptyList()   // authorities — extend when roles are needed
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                logger.warn("JWT filter — invalid or expired token on [{}] {}",
                        request.getMethod(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Extracts the raw JWT from the request.
     * Checks the {@code Authorization: Bearer} header first; if absent, falls
     * back to the {@code token} query parameter (needed for browser SSE clients).
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Standard Authorization header
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        // 2. Query-parameter fallback for EventSource (SSE) connections
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            logger.debug("JWT filter — using query-param token for [{}] {}",
                    request.getMethod(), request.getRequestURI());
            return queryToken;
        }
        return null;
    }
}
