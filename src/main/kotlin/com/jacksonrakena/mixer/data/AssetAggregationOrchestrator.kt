package com.jacksonrakena.mixer.data

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.MDC
import org.springframework.stereotype.Component
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Component
class AssetAggregationOrchestrator(
    private val aggregationService: AggregationService,
    private val aggregateRepository: AggregateRepository,
    private val marketPriceResolver: MarketPriceResolver,
    private val transactionSource: DatabaseAssetTransactionSource,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Resolved plan for an aggregation run, unifying partial and full reaggregation.
     */
    private data class AggregationPlan(
        val mode: String,
        val startDate: LocalDate?,
        val initialHolding: Double,
        val initialPrice: Double?,
        val initialPriceDate: LocalDate?,
    )

    suspend fun forceAggregateUserAssets(userId: Uuid) {
        val (assetIds, userTimezone) = aggregateRepository.getUserAssetsWithTimezone(userId) ?: return
        MDC.put("userId", userId.toString())
        try {
            logger.info { "Forcing aggregation for user $userId (tz=$userTimezone), total ${assetIds.size} assets" }
            for (assetId in assetIds) {
                regenerateAggregatesForAsset(assetId, userTimezone)
            }
        } finally {
            MDC.remove("userId")
        }
    }

    suspend fun clearAggregatesForAsset(assetId: Uuid) {
        aggregateRepository.deleteAggregatesForAsset(assetId)
    }

    /**
     * Resolves the aggregation plan: determines start date, initial state, and cleans up
     * stale aggregates. Returns null if there are no transactions (nothing to aggregate).
     */
    private suspend fun resolveAggregationPlan(
        assetId: Uuid,
        staleAfter: Long,
        userTimezone: TimeZone,
    ): AggregationPlan {
        // Try partial reaggregation if there's a staleness marker
        if (staleAfter > 0L) {
            val staleDate = Instant.fromEpochMilliseconds(staleAfter).toLocalDateTime(userTimezone).date
            val lastValid = aggregateRepository.getLastAggregateBefore(assetId, staleDate)
            if (lastValid != null) {
                aggregateRepository.deleteAggregatesFrom(assetId, lastValid.startDate)
                return AggregationPlan(
                    mode = "partial(from=${lastValid.startDate})",
                    startDate = lastValid.startDate,
                    initialHolding = lastValid.initialHolding,
                    initialPrice = lastValid.initialPrice,
                    initialPriceDate = lastValid.initialPriceDate,
                )
            }
        }

        // Full reaggregation
        aggregateRepository.deleteAggregatesForAsset(assetId)
        val earliest = transactionSource.getEarliestTransaction(assetId)
        val startDate = earliest?.timestamp?.toLocalDateTime(userTimezone)?.date
        return AggregationPlan(
            mode = "full",
            startDate = startDate,
            initialHolding = 0.0,
            initialPrice = null,
            initialPriceDate = null,
        )
    }

    suspend fun regenerateAggregatesForAsset(
        assetId: Uuid,
        userTimezone: TimeZone,
    ) {
        if (!aggregateRepository.tryAdvisoryLock(assetId)) {
            logger.info { "Asset $assetId is being aggregated by another instance, skipping" }
            return
        }

        val totalStart = System.nanoTime()
        MDC.put("assetId", assetId.toString())
        try {
            val today = Clock.System.now().toLocalDateTime(userTimezone).date

            val state = aggregateRepository.getAssetAggregationState(assetId)
            if (state == null) {
                logger.warn { "Asset $assetId not found, skipping reaggregation" }
                return
            }

            if (state.staleAfter == 0L && state.aggregatedThrough != null && state.aggregatedThrough >= today) {
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0
                logger.info { "Asset $assetId already up-to-date (through=${state.aggregatedThrough}), skipping | total=${String.format("%.1f", totalMs)}ms" }
                return
            }

            // Resolve plan (also deletes stale aggregates)
            val planStart = System.nanoTime()
            val plan = resolveAggregationPlan(assetId, state.staleAfter, userTimezone)
            val planMs = (System.nanoTime() - planStart) / 1_000_000.0

            if (plan.mode != "full") {
                logger.info { "Partial reaggregation for asset $assetId: ${plan.mode}" }
            }

            // Fetch market prices (skip if no start date, i.e. no transactions)
            val marketStart = System.nanoTime()
            val marketPrices = if (plan.startDate != null) {
                marketPriceResolver.resolveMarketPrices(
                    state.provider, state.providerData, plan.startDate, today, assetId
                )
            } else null
            val marketMs = (System.nanoTime() - marketStart) / 1_000_000.0

            // Run forward aggregation
            val aggStart = System.nanoTime()
            val aggregates = aggregationService.forwardAggregate(
                assetId,
                userTimezone,
                transactionSource,
                today,
                marketPrices,
                startOverride = plan.startDate,
                initialHolding = plan.initialHolding,
                initialPrice = plan.initialPrice,
                initialPriceDate = plan.initialPriceDate,
            )
            val aggMs = (System.nanoTime() - aggStart) / 1_000_000.0

            // Persist results
            val insertStart = System.nanoTime()
            aggregateRepository.batchInsertAggregates(assetId, aggregates)
            aggregateRepository.markAssetAggregated(assetId, today, state.staleAfter)
            val insertMs = (System.nanoTime() - insertStart) / 1_000_000.0
            val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0

            logger.info {
                "Reaggregation complete for asset $assetId: mode=${plan.mode}, entries=${aggregates.size}, through=$today | " +
                "total=${String.format("%.1f", totalMs)}ms " +
                "[plan=${String.format("%.1f", planMs)}ms, " +
                "market=${String.format("%.1f", marketMs)}ms, " +
                "aggregate=${String.format("%.1f", aggMs)}ms, " +
                "insert=${String.format("%.1f", insertMs)}ms]"
            }
        } finally {
            aggregateRepository.releaseAdvisoryLock(assetId)
            MDC.remove("assetId")
        }
    }

    /**
     * Checks all assets and regenerates aggregations for any that are behind today
     * or have never been computed.
     */
    suspend fun ensureAllAggregationsUpToDate() {
        val staleAssets = aggregateRepository.findStaleAssets()
        if (staleAssets.isEmpty()) {
            logger.debug { "All asset aggregations are up-to-date" }
            return
        }
        logger.info { "Found ${staleAssets.size} assets needing aggregation refresh" }
        for ((assetId, userTimezone) in staleAssets) {
            try {
                regenerateAggregatesForAsset(assetId, userTimezone)
            } catch (e: Exception) {
                logger.error(e) { "Failed to refresh aggregations for asset $assetId" }
            }
        }
    }
}
