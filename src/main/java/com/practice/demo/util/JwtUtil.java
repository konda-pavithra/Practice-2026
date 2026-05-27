package com.practice.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    // -----------------------------------------------------------------------
    // Token generation
    // -----------------------------------------------------------------------

    /**
     * Generates a signed JWT for the given username.
     * Algorithm: HMAC-SHA256 (HS256).
     * Claims: subject=username, issuedAt=now, expiration=now+expirationMs.
     */
    public String generateToken(String username) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        logger.debug("Generating JWT for username '{}', expires at {}", username, expiry);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    // -----------------------------------------------------------------------
    // Token introspection
    // -----------------------------------------------------------------------

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    // -----------------------------------------------------------------------
    // Token validation
    // -----------------------------------------------------------------------

    /**
     * Returns true only when the token is structurally valid, correctly signed,
     * and not yet expired.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            logger.debug("JWT validated successfully");
            return true;
        } catch (ExpiredJwtException ex) {
            logger.warn("JWT has expired: {}", ex.getMessage());
        } catch (SignatureException ex) {
            logger.warn("JWT signature is invalid: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            logger.warn("JWT is malformed: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            logger.warn("JWT type is unsupported: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            logger.warn("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
