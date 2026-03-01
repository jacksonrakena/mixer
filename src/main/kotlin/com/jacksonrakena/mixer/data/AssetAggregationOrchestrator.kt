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

            // Resolve asset state
            val resolveStart = System.nanoTime()
            val state = aggregateRepository.getAssetAggregationState(assetId)
            if (state == null) {
                logger.warn { "Asset $assetId not found, skipping reaggregation" }
                return
            }
            val resolveMs = (System.nanoTime() - resolveStart) / 1_000_000.0

            // Skip if already up-to-date
            if (state.staleAfter == 0L && state.aggregatedThrough != null && state.aggregatedThrough >= today) {
                val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0
                logger.info { "Asset $assetId already up-to-date (through=${state.aggregatedThrough}), skipping | total=${String.format("%.1f", totalMs)}ms" }
                return
            }

            // Determine if partial reaggregation is possible
            val partialStart = System.nanoTime()
            val partialState: PartialAggregateState? = if (state.staleAfter > 0L) {
                val staleDate = Instant.fromEpochMilliseconds(state.staleAfter).toLocalDateTime(userTimezone).date
                aggregateRepository.getLastAggregateBefore(assetId, staleDate)
            } else null
            val partialCheckMs = (System.nanoTime() - partialStart) / 1_000_000.0

            // Delete stale aggregates
            val deleteStart = System.nanoTime()
            if (partialState != null) {
                aggregateRepository.deleteAggregatesFrom(assetId, partialState.startDate)
                logger.info { "Partial reaggregation for asset $assetId from ${partialState.startDate}" }
            } else {
                aggregateRepository.deleteAggregatesForAsset(assetId)
            }
            val deleteMs = (System.nanoTime() - deleteStart) / 1_000_000.0

            // Determine the effective start date for market prices:
            // partial reaggregation starts from the stale date, full starts from the earliest transaction
            val effectiveStartDate = if (partialState != null) {
                partialState.startDate
            } else {
                val earliest = transactionSource.getEarliestTransaction(assetId)
                if (earliest != null) {
                    earliest.timestamp.toLocalDateTime(userTimezone).date
                } else {
                    // No transactions — aggregation will produce nothing, skip market fetch
                    null
                }
            }

            // Fetch market prices
            val marketStart = System.nanoTime()
            val marketPrices = if (effectiveStartDate != null) {
                marketPriceResolver.resolveMarketPrices(
                    state.provider, state.providerData, effectiveStartDate, today, assetId
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
                startOverride = partialState?.startDate,
                initialHolding = partialState?.initialHolding ?: 0.0,
                initialPrice = partialState?.initialPrice,
                initialPriceDate = partialState?.initialPriceDate,
            )
            val aggMs = (System.nanoTime() - aggStart) / 1_000_000.0

            // Persist results
            val insertStart = System.nanoTime()
            aggregateRepository.batchInsertAggregates(assetId, aggregates)
            aggregateRepository.markAssetAggregated(assetId, today, state.staleAfter)
            val insertMs = (System.nanoTime() - insertStart) / 1_000_000.0
            val totalMs = (System.nanoTime() - totalStart) / 1_000_000.0

            val mode = if (partialState != null) "partial(from=${partialState.startDate})" else "full"
            logger.info {
                "Reaggregation complete for asset $assetId: mode=$mode, entries=${aggregates.size}, through=$today | " +
                "total=${String.format("%.1f", totalMs)}ms " +
                "[resolve=${String.format("%.1f", resolveMs)}ms, " +
                "partialCheck=${String.format("%.1f", partialCheckMs)}ms, " +
                "delete=${String.format("%.1f", deleteMs)}ms, " +
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
