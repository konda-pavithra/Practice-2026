package com.practice.demo.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata and global security-scheme definition.
 *
 * <h3>Swagger UI</h3>
 * Available at <a href="http://localhost:8080/swagger-ui.html">
 * http://localhost:8080/swagger-ui.html</a> once the application is running.
 *
 * <h3>How to authenticate in the Swagger UI</h3>
 * <ol>
 *   <li>Call <b>POST /api/users/login</b> with your credentials.</li>
 *   <li>Copy the {@code token} value from the response.</li>
 *   <li>Click the <b>Authorize 🔒</b> button at the top of the page.</li>
 *   <li>In the "bearerAuth" field, paste the token — <em>without</em> the
 *       {@code Bearer } prefix (Swagger adds it automatically).</li>
 *   <li>Click <b>Authorize</b>, then <b>Close</b>.</li>
 *   <li>All subsequent "Try it out" calls will include the JWT in the
 *       {@code Authorization: Bearer …} header.</li>
 * </ol>
 *
 * <h3>Security scheme</h3>
 * The {@link SecurityScheme} annotation defines a reusable HTTP Bearer scheme
 * named {@code "bearerAuth"}.  Individual controllers reference it via
 * {@code @SecurityRequirement(name = "bearerAuth")}.  Public endpoints
 * (register, login, Swagger UI itself) carry no {@code @SecurityRequirement}
 * and therefore show no padlock in the UI.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "Stock Portfolio API",
        version     = "1.0.0",
        description = """
                REST API for a Nifty 50 stock portfolio management system.

                **Features**
                - User registration and JWT-based authentication
                - Live Nifty 50 stock price ticker (Yahoo Finance + Kafka pipeline)
                - Portfolio management: manual entry and bulk Excel upload
                - Real-time portfolio P&L via Server-Sent Events (SSE)
                - Per-stock upper/lower price threshold alerts (RabbitMQ + email)

                **Authentication**
                All endpoints except `/api/users/register` and `/api/users/login` require
                a valid JWT in the `Authorization: Bearer <token>` header.
                Use the **Authorize** button above to set your token for the Swagger UI.
                """,
        contact = @Contact(
            name  = "Demo Project",
            email = "admin@demo.com"
        ),
        license = @License(name = "MIT")
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local development server")
    }
)
@SecurityScheme(
    name        = "bearerAuth",
    type        = SecuritySchemeType.HTTP,
    scheme      = "bearer",
    bearerFormat = "JWT",
    description  = """
            JWT obtained from POST /api/users/login.
            Paste the token value **without** the 'Bearer ' prefix — Swagger adds it automatically.
            """
)
public class OpenApiConfig {
    // All configuration is expressed via annotations above.
    // No bean definitions needed — SpringDoc picks up the annotations at startup.
}
