package com.tvpc.adapter.out.persistence;

import com.tvpc.application.port.out.SettlementRepository;
import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.Settlement;
import com.tvpc.domain.model.SettlementDirection;
import com.tvpc.domain.model.SettlementType;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of SettlementRepository
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcSettlementPersistenceAdapter implements SettlementRepository {

    private final SqlClient sqlClient;

    @Override
    public Future<Long> save(Settlement settlement, SqlConnection connection) {
        String insertSql = "INSERT INTO SETTLEMENT (" +
                "SETTLEMENT_ID, SETTLEMENT_VERSION, PTS, PROCESSING_ENTITY, " +
                "COUNTERPARTY_ID, VALUE_DATE, CURRENCY, AMOUNT, " +
                "BUSINESS_STATUS, DIRECTION, SETTLEMENT_TYPE, IS_OLD, " +
                "CREATE_TIME, UPDATE_TIME" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        LocalDateTime now = LocalDateTime.now();
        Tuple insertParams = Tuple.of(
                settlement.getSettlementId(),
                settlement.getSettlementVersion(),
                settlement.getPts(),
                settlement.getProcessingEntity(),
                settlement.getCounterpartyId(),
                settlement.getValueDate(),
                settlement.getCurrency(),
                settlement.getAmount(),
                settlement.getBusinessStatus().getValue(),
                settlement.getDirection().getValue(),
                settlement.getSettlementType().getValue(),
                settlement.getIsOld() ? 1 : 0,
                now,
                now
        );

        return connection.preparedQuery(insertSql)
                .execute(insertParams)
                .compose(result -> findExistingSettlementId(
                        settlement.getSettlementId(),
                        settlement.getPts(),
                        settlement.getProcessingEntity(),
                        settlement.getSettlementVersion(),
                        connection
                ))
                .map(id -> {
                    settlement.setId(id);
                    log.debug("save: Settlement saved with ID: {}", id);
                    return id;
                })
                .recover(throwable -> {
                    String errorMsg = throwable.getMessage();
                    boolean isDuplicate = errorMsg != null &&
                            (errorMsg.contains("ORA-00001") ||
                                    errorMsg.contains("23505") ||
                                    errorMsg.contains("unique constraint") ||
                                    errorMsg.contains("UniqueConstraintViolation"));

                    if (isDuplicate) {
                        log.debug("save: Duplicate detected, querying for existing ID");
                        return findExistingSettlementId(
                                settlement.getSettlementId(),
                                settlement.getPts(),
                                settlement.getProcessingEntity(),
                                settlement.getSettlementVersion(),
                                connection
                        ).map(existingId -> {
                            settlement.setId(existingId);
                            log.debug("save: Using existing settlement ID: {}", existingId);
                            return existingId;
                        });
                    }
                    return Future.failedFuture(throwable);
                });
    }

    private Future<Long> findExistingSettlementId(String settlementId, String pts, String processingEntity, Long settlementVersion, SqlConnection connection) {
        String sql = "SELECT ID FROM SETTLEMENT " +
                "WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ? AND SETTLEMENT_VERSION = ?";

        Tuple params = Tuple.of(settlementId, pts, processingEntity, settlementVersion);

        return connection.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    if (result.size() > 0) {
                        return result.iterator().next().getLong("ID");
                    }
                    throw new RuntimeException("Duplicate detected but no existing record found");
                });
    }

    @Override
    public Future<Void> markOldVersions(String settlementId, String pts, String processingEntity, SqlConnection connection) {
        String sql = "UPDATE SETTLEMENT SET IS_OLD = 1, UPDATE_TIME = ? " +
                "WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ? " +
                "AND SETTLEMENT_VERSION < (SELECT MAX(SETTLEMENT_VERSION) " +
                "FROM SETTLEMENT WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ?) " +
                "AND (IS_OLD IS NULL OR IS_OLD = 0)";

        Tuple params = Tuple.of(
                LocalDateTime.now(),
                settlementId,
                pts,
                processingEntity,
                settlementId,
                pts,
                processingEntity
        );

        return connection.preparedQuery(sql)
                .execute(params)
                .mapEmpty();
    }

    @Override
    public Future<Optional<String>> findPreviousCounterparty(String settlementId, String pts, String processingEntity, Long currentId, SqlConnection connection) {
        String sql = "SELECT COUNTERPARTY_ID FROM SETTLEMENT " +
                "WHERE ID = (SELECT MAX(ID) FROM SETTLEMENT " +
                "WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ? AND ID < ?) " +
                "AND COUNTERPARTY_ID IS NOT NULL";

        Tuple params = Tuple.of(settlementId, pts, processingEntity, currentId);

        return connection.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    if (result.size() > 0) {
                        String counterpartyId = result.iterator().next().getString("COUNTERPARTY_ID");
                        return Optional.of(counterpartyId);
                    }
                    return Optional.empty();
                });
    }

    @Override
    public Future<Optional<Settlement>> findLatestVersion(String settlementId, String pts, String processingEntity) {
        String sql = "SELECT * FROM SETTLEMENT " +
                "WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ? " +
                "ORDER BY SETTLEMENT_VERSION DESC " +
                "FETCH FIRST 1 ROW ONLY";

        Tuple params = Tuple.of(settlementId, pts, processingEntity);

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    if (result.size() > 0) {
                        return Optional.of(mapToSettlement(result.iterator().next()));
                    }
                    return Optional.empty();
                });
    }

    @Override
    public Future<List<Settlement>> findByGroup(String pts, String processingEntity, String counterpartyId, String valueDate, Long maxSeqId, SqlConnection connection) {
        String sql = "SELECT * FROM SETTLEMENT " +
                "WHERE PTS = ? AND PROCESSING_ENTITY = ? AND COUNTERPARTY_ID = ? AND VALUE_DATE = ? " +
                "AND ID <= ? " +
                "ORDER BY SETTLEMENT_ID, SETTLEMENT_VERSION";

        Tuple params = Tuple.of(pts, processingEntity, counterpartyId, LocalDate.parse(valueDate), maxSeqId);

        return connection.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    List<Settlement> settlements = new ArrayList<>();
                    for (var row : result) {
                        settlements.add(mapToSettlement(row));
                    }
                    return settlements;
                });
    }

    @Override
    public Future<List<Settlement>> findByGroupWithFilters(String pts, String processingEntity, String counterpartyId, String valueDate, Long maxSeqId, SqlConnection connection) {
        String sql = "SELECT s.* FROM SETTLEMENT s " +
                "WHERE s.PTS = ? AND s.PROCESSING_ENTITY = ? AND s.COUNTERPARTY_ID = ? AND s.VALUE_DATE = ? " +
                "  AND s.ID <= ? " +
                "  AND s.DIRECTION = 'PAY' AND s.BUSINESS_STATUS != 'CANCELLED' " +
                "  AND s.SETTLEMENT_VERSION = (" +
                "    SELECT MAX(SETTLEMENT_VERSION) " +
                "    FROM SETTLEMENT " +
                "    WHERE SETTLEMENT_ID = s.SETTLEMENT_ID " +
                "      AND PTS = s.PTS " +
                "      AND PROCESSING_ENTITY = s.PROCESSING_ENTITY " +
                "  ) " +
                "ORDER BY SETTLEMENT_ID";

        Tuple params = Tuple.of(pts, processingEntity, counterpartyId, LocalDate.parse(valueDate), maxSeqId);

        return connection.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    List<Settlement> settlements = new ArrayList<>();
                    for (var row : result) {
                        settlements.add(mapToSettlement(row));
                    }
                    return settlements;
                });
    }

    private Settlement mapToSettlement(io.vertx.sqlclient.Row row) {
        Settlement settlement = new Settlement();
        settlement.setId(row.getLong("ID"));
        settlement.setSettlementId(row.getString("SETTLEMENT_ID"));
        settlement.setSettlementVersion(row.getLong("SETTLEMENT_VERSION"));
        settlement.setPts(row.getString("PTS"));
        settlement.setProcessingEntity(row.getString("PROCESSING_ENTITY"));
        settlement.setCounterpartyId(row.getString("COUNTERPARTY_ID"));
        settlement.setValueDate(row.getLocalDate("VALUE_DATE"));
        settlement.setCurrency(row.getString("CURRENCY"));
        settlement.setAmount(row.getBigDecimal("AMOUNT"));
        settlement.setBusinessStatus(BusinessStatus.fromValue(row.getString("BUSINESS_STATUS")));
        settlement.setDirection(SettlementDirection.fromValue(row.getString("DIRECTION")));
        settlement.setSettlementType(SettlementType.fromValue(row.getString("SETTLEMENT_TYPE")));

        Object isOldValue = row.getValue("IS_OLD");
        boolean isOld;
        if (isOldValue instanceof Boolean) {
            isOld = (Boolean) isOldValue;
        } else if (isOldValue instanceof Number) {
            isOld = ((Number) isOldValue).intValue() == 1;
        } else {
            isOld = false;
        }
        settlement.setIsOld(isOld);

        settlement.setCreateTime(row.getLocalDateTime("CREATE_TIME"));
        settlement.setUpdateTime(row.getLocalDateTime("UPDATE_TIME"));
        return settlement;
    }

    @Override
    public Future<List<Settlement>> search(
            String pts,
            String processingEntity,
            String counterpartyId,
            String valueDateFrom,
            String valueDateTo,
            String direction,
            String businessStatus,
            int limit,
            int offset
    ) {
        // Build dynamic SQL
        StringBuilder sql = new StringBuilder("SELECT * FROM SETTLEMENT WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (pts != null) {
            sql.append(" AND PTS = ?");
            params.add(pts);
        }
        if (processingEntity != null) {
            sql.append(" AND PROCESSING_ENTITY = ?");
            params.add(processingEntity);
        }
        if (counterpartyId != null) {
            sql.append(" AND COUNTERPARTY_ID = ?");
            params.add(counterpartyId);
        }
        if (valueDateFrom != null) {
            sql.append(" AND VALUE_DATE >= ?");
            params.add(LocalDate.parse(valueDateFrom));
        }
        if (valueDateTo != null) {
            sql.append(" AND VALUE_DATE <= ?");
            params.add(LocalDate.parse(valueDateTo));
        }
        if (direction != null) {
            sql.append(" AND DIRECTION = ?");
            params.add(direction);
        }
        if (businessStatus != null) {
            sql.append(" AND BUSINESS_STATUS = ?");
            params.add(businessStatus);
        }

        sql.append(" ORDER BY ID DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return sqlClient.preparedQuery(sql.toString())
                .execute(Tuple.from(params))
                .map(result -> {
                    List<Settlement> settlements = new ArrayList<>();
                    for (var row : result) {
                        settlements.add(mapToSettlement(row));
                    }
                    return settlements;
                });
    }


    @Override
    public Future<List<String>> getDistinctGroups(
            String pts,
            String processingEntity,
            String valueDateFrom,
            String valueDateTo
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT DISTINCT PTS, PROCESSING_ENTITY, COUNTERPARTY_ID, VALUE_DATE " +
                        "FROM SETTLEMENT WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (pts != null) {
            sql.append(" AND PTS = ?");
            params.add(pts);
        }
        if (processingEntity != null) {
            sql.append(" AND PROCESSING_ENTITY = ?");
            params.add(processingEntity);
        }
        if (valueDateFrom != null) {
            sql.append(" AND VALUE_DATE >= ?");
            params.add(LocalDate.parse(valueDateFrom));
        }
        if (valueDateTo != null) {
            sql.append(" AND VALUE_DATE <= ?");
            params.add(LocalDate.parse(valueDateTo));
        }

        return sqlClient.preparedQuery(sql.toString())
                .execute(Tuple.from(params))
                .map(result -> {
                    List<String> groups = new ArrayList<>();
                    for (var row : result) {
                        String group = String.format("%s|%s|%s|%s",
                                row.getString("PTS"),
                                row.getString("PROCESSING_ENTITY"),
                                row.getString("COUNTERPARTY_ID"),
                                row.getLocalDate("VALUE_DATE"));
                        groups.add(group);
                    }
                    return groups;
                });
    }

}
