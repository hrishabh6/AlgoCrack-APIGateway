package com.hrishabh.algocrackapigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * JWT utility for RS256 token validation.
 * 
 * This class only holds the PUBLIC key — it can validate tokens
 * but CANNOT create them. Only the Auth Service holds the private key.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.public-key-path}")
    private Resource publicKeyResource;

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            String keyContent = new String(publicKeyResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Strip PEM headers/footers and whitespace
            String publicKeyPem = keyContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] decoded = Base64.getDecoder().decode(publicKeyPem);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            this.publicKey = keyFactory.generatePublic(keySpec);

            log.info("✅ RSA public key loaded successfully for JWT validation");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA public key", e);
        }
    }

    /**
     * Validate and parse a JWT token using the RSA public key.
     * 
     * @param token the JWT string
     * @return parsed Claims if valid
     * @throws JwtException if token is invalid, expired, or tampered
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Check if a token is valid (not expired, signature OK).
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract the subject (email) from a validated token.
     */
    public String getEmail(String token) {
        return validateToken(token).getSubject();
    }

    /**
     * Extract a custom claim from the token.
     */
    public Object getClaim(String token, String claimName) {
        return validateToken(token).get(claimName);
    }
}
