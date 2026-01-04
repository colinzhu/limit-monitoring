package com.tvpc.infrastructure.persistence;

import com.tvpc.application.port.outbound.ActivityRepository;
import com.tvpc.domain.model.Activity;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of ActivityRepository
 * Infrastructure layer adapter
 */
@Slf4j
public class JdbcActivityRepository implements ActivityRepository {

    private final SqlClient sqlClient;

    public JdbcActivityRepository(SqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    @Override
    public Future<Void> save(Activity activity) {
        String sql = "INSERT INTO ACTIVITIES " +
                "(PTS, PROCESSING_ENTITY, SETTLEMENT_ID, SETTLEMENT_VERSION, USER_ID, USER_NAME, ACTION_TYPE, ACTION_COMMENT, CREATE_TIME) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Tuple params = Tuple.of(
                activity.getPts(),
                activity.getProcessingEntity(),
                activity.getSettlementId(),
                activity.getSettlementVersion(),
                activity.getUserId(),
                activity.getUserName(),
                activity.getActionType(),
                activity.getActionComment(),
                activity.getCreateTime()
        );

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .mapEmpty();
    }

    @Override
    public Future<List<Activity>> findBySettlement(String settlementId, Long settlementVersion) {
        String sql = "SELECT * FROM ACTIVITIES " +
                "WHERE SETTLEMENT_ID = ? AND SETTLEMENT_VERSION = ? " +
                "ORDER BY CREATE_TIME ASC";

        Tuple params = Tuple.of(settlementId, settlementVersion);

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    List<Activity> activities = new ArrayList<>();
                    for (var row : result) {
                        activities.add(mapToActivity(row));
                    }
                    return activities;
                });
    }

    @Override
    public Future<List<Activity>> findBySettlementLatest(String settlementId, String pts, String processingEntity) {
        // Find activities for the latest version of a settlement
        String sql = "SELECT a.* FROM ACTIVITIES a " +
                "INNER JOIN SETTLEMENT s ON a.SETTLEMENT_ID = s.SETTLEMENT_ID " +
                "  AND a.SETTLEMENT_VERSION = s.SETTLEMENT_VERSION " +
                "WHERE a.SETTLEMENT_ID = ? AND s.PTS = ? AND s.PROCESSING_ENTITY = ? " +
                "  AND s.SETTLEMENT_VERSION = (" +
                "    SELECT MAX(SETTLEMENT_VERSION) " +
                "    FROM SETTLEMENT " +
                "    WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ?" +
                "  ) " +
                "ORDER BY a.CREATE_TIME ASC";

        Tuple params = Tuple.of(settlementId, pts, processingEntity, settlementId, pts, processingEntity);

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    List<Activity> activities = new ArrayList<>();
                    for (var row : result) {
                        activities.add(mapToActivity(row));
                    }
                    return activities;
                });
    }

    @Override
    public Future<Boolean> hasUserRequestedRelease(String settlementId, Long settlementVersion, String userId) {
        String sql = "SELECT COUNT(*) as count FROM ACTIVITIES " +
                "WHERE SETTLEMENT_ID = ? AND SETTLEMENT_VERSION = ? " +
                "  AND USER_ID = ? AND ACTION_TYPE = 'REQUEST_RELEASE'";

        Tuple params = Tuple.of(settlementId, settlementVersion, userId);

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    if (result.size() > 0) {
                        long count = result.iterator().next().getLong("count");
                        return count > 0;
                    }
                    return false;
                });
    }

    private Activity mapToActivity(io.vertx.sqlclient.Row row) {
        return new Activity(
                row.getLong("ID"),
                row.getString("PTS"),
                row.getString("PROCESSING_ENTITY"),
                row.getString("SETTLEMENT_ID"),
                row.getLong("SETTLEMENT_VERSION"),
                row.getString("USER_ID"),
                row.getString("USER_NAME"),
                row.getString("ACTION_TYPE"),
                row.getString("ACTION_COMMENT"),
                row.getLocalDateTime("CREATE_TIME")
        );
    }
}
