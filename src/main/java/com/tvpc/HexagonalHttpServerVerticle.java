package com.tvpc;

import com.tvpc.infrastructure.config.HexagonalApplicationConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.jdbcclient.JDBCPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Server Verticle using Hexagonal Architecture
 * Wires the hexagonal application configuration
 */
public class HexagonalHttpServerVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(HexagonalHttpServerVerticle.class);

    private static final int DEFAULT_PORT = 8080;

    private JDBCPool jdbcPool;
    private HexagonalApplicationConfig appConfig;

    @Override
    public void start(Promise<Void> startPromise) {
        log.info("Starting Hexagonal HTTP Server Verticle...");

        // Initialize database connection
        initializeDatabase()
                .compose(v -> {
                    log.info("Database initialized successfully");

                    // Initialize hexagonal application configuration
                    appConfig = new HexagonalApplicationConfig(vertx, config(), jdbcPool);
                    appConfig.initialize();

                    // Start HTTP server
                    return startHttpServer();
                })
                .onSuccess(v -> {
                    log.info("Hexagonal HTTP Server Verticle started successfully on port {}", getPort());
                    startPromise.complete();
                })
                .onFailure(error -> {
                    log.error("Failed to start Hexagonal HTTP Server Verticle", error);
                    startPromise.fail(error);
                });
    }

    @Override
    public void stop() {
        if (jdbcPool != null) {
            jdbcPool.close();
        }
        log.info("Hexagonal HTTP Server Verticle stopped");
    }

    private Future<Void> initializeDatabase() {
        try {
            JsonObject dbConfig = config().getJsonObject("database");
            if (dbConfig == null) {
                return Future.failedFuture("Database configuration not found");
            }

            log.info("Connecting to database: {}", dbConfig.getString("url"));

            JsonObject poolConfig = new JsonObject()
                    .put("url", dbConfig.getString("url"))
                    .put("user", dbConfig.getString("user"))
                    .put("password", dbConfig.getString("password"))
                    .put("driver_class", dbConfig.getString("driver_class"))
                    .put("max_pool_size", dbConfig.getInteger("max_pool_size", 10));

            jdbcPool = JDBCPool.pool(vertx, poolConfig);

            return jdbcPool.query("SELECT 1 FROM DUAL").execute()
                    .onSuccess(result -> log.info("Database connection test successful"))
                    .onFailure(error -> log.error("Database connection failed", error))
                    .mapEmpty();

        } catch (Exception e) {
            log.error("Error initializing database", e);
            return Future.failedFuture(e);
        }
    }

    private Future<Void> startHttpServer() {
        // Get router from hexagonal configuration
        // The ApiRouter already has CORS handling and all routes configured
        Router router = appConfig.getRouter();

        if (router == null) {
            return Future.failedFuture("Router not initialized from HexagonalApplicationConfig");
        }

        int port = getPort();

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> log.info("HTTP server listening on port {}", port))
                .mapEmpty();
    }

    private int getPort() {
        return config().getInteger("http.port", DEFAULT_PORT);
    }
}
