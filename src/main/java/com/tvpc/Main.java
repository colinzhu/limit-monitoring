package com.tvpc;

import com.tvpc.domain.SettlementEvent;
import com.tvpc.event.SettlementEventCodec;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Main application entry point.
 *
 * Uses Hexagonal Architecture with Composition Root pattern.
 * All component wiring is handled by CompositionRoot.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting Payment Limit Monitoring System (Hexagonal Architecture)...");

        // Create Vertx instance with options
        VertxOptions options = new VertxOptions()
                .setWorkerPoolSize(10)
                .setEventLoopPoolSize(5);

        Vertx vertx = Vertx.vertx(options);

        // Register message codec for SettlementEvent
        vertx.eventBus().registerDefaultCodec(SettlementEvent.class, new SettlementEventCodec());
        log.info("Registered SettlementEvent message codec");

        // Load configuration from application.yml
        JsonObject config = loadConfig();

        // Create Composition Root
        CompositionRoot compositionRoot = new CompositionRoot(vertx, config);

        // Deploy HTTP Server Verticle (primary adapter)
        deployHttpServerVerticle(vertx, compositionRoot)
                .compose(v -> {
                    // Deploy Event Consumer Verticle (secondary adapter)
                    return deployEventConsumerVerticle(vertx, compositionRoot);
                })
                .onSuccess(v -> {
                    // All verticles deployed successfully
                    log.info("Payment Limit Monitoring System is ready!");
                    log.info("API Endpoint: http://localhost:8081/api/settlements");
                    log.info("Health Check: http://localhost:8081/health");

                    // Add shutdown hook
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        log.info("Shutting down Payment Limit Monitoring System...");
                        compositionRoot.close();
                        vertx.close();
                    }));
                })
                .onFailure(error -> {
                    log.error("Failed to deploy verticles", error);
                    compositionRoot.close();
                    vertx.close();
                });
    }

    /**
     * Deploys the HTTP Server Verticle.
     * This is the primary entry point for all HTTP requests.
     */
    private static io.vertx.core.Future<Void> deployHttpServerVerticle(Vertx vertx, CompositionRoot compositionRoot) {
        HttpServerVerticle httpServerVerticle = compositionRoot.createHttpServerVerticle();

        return vertx.deployVerticle(httpServerVerticle, new io.vertx.core.DeploymentOptions()
                .setConfig(compositionRoot.getPool() != null ? vertx.getOrCreateContext().config() : null)
                .setInstances(1))
                .map(deploymentId -> {
                    log.info("HTTP Server Verticle deployed: {}", deploymentId);
                    return null;
                });
    }

    /**
     * Deploys the Event Consumer Verticle.
     * This processes settlement events asynchronously.
     */
    private static io.vertx.core.Future<Void> deployEventConsumerVerticle(Vertx vertx, CompositionRoot compositionRoot) {
        var eventConsumerVerticle = compositionRoot.createEventConsumerVerticle();

        return vertx.deployVerticle(eventConsumerVerticle, new io.vertx.core.DeploymentOptions()
                .setInstances(1))
                .map(deploymentId -> {
                    log.info("Event Consumer Verticle deployed: {}", deploymentId);
                    return null;
                });
    }

    /**
     * Load configuration from application.yml or use defaults.
     */
    private static JsonObject loadConfig() {
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (is == null) {
                log.warn("application.yml not found, using Oracle defaults");
                return getDefaultOracleConfig();
            }

            // Read the YAML file
            String yaml = new String(is.readAllBytes());

            // Simple YAML parsing for our config structure
            JsonObject config = new JsonObject();

            // Parse HTTP config
            if (yaml.contains("port: 8081")) {
                config.put("http.port", 8081);
            }

            // Parse Database config
            JsonObject dbConfig = new JsonObject();
            if (yaml.contains("jdbc:oracle")) {
                dbConfig.put("url", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
                dbConfig.put("driver_class", "oracle.jdbc.OracleDriver");
                dbConfig.put("user", "tvpc");
                dbConfig.put("password", "tvpc123");
                dbConfig.put("max_pool_size", 20);
            } else {
                // Fallback to H2
                dbConfig.put("url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
                dbConfig.put("driver_class", "org.h2.Driver");
                dbConfig.put("user", "sa");
                dbConfig.put("password", "");
                dbConfig.put("max_pool_size", 10);
            }

            config.put("database", dbConfig);
            log.info("Loaded configuration from application.yml");
            return config;

        } catch (Exception e) {
            log.warn("Failed to load application.yml, using Oracle defaults: {}", e.getMessage());
            return getDefaultOracleConfig();
        }
    }

    /**
     * Default Oracle configuration (used when application.yml is not found).
     */
    private static JsonObject getDefaultOracleConfig() {
        return new JsonObject()
                .put("http.port", 8081)
                .put("database", new JsonObject()
                        .put("url", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1")
                        .put("driver_class", "oracle.jdbc.OracleDriver")
                        .put("user", "tvpc")
                        .put("password", "tvpc123")
                        .put("max_pool_size", 20)
                );
    }
}
