package com.jacksonrakena.mixer.performance

import com.jacksonrakena.mixer.data.aggregation.AggregationService
import com.jacksonrakena.mixer.data.aggregation.AssetTransaction
import com.jacksonrakena.mixer.data.aggregation.AssetTransactionSource
import com.jacksonrakena.mixer.data.aggregation.AssetTransactionType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * In-memory transaction source for benchmarks. Pre-sorts and indexes
 * transactions at setup time so the benchmark measures aggregation,
 * not source overhead.
 */
class BenchmarkTransactionSource(
    transactions: List<AssetTransaction>,
) : AssetTransactionSource {
    private val byAsset = transactions.groupBy { it.assetId }

    override suspend fun getLatestReconciliation(asset: Uuid, before: Instant): AssetTransaction? {
        return byAsset[asset]
            ?.filter { it.timestamp <= before && it.type == AssetTransactionType.Reconciliation }
            ?.maxByOrNull { it.timestamp }
    }

    override suspend fun getTransactions(asset: Uuid, after: Instant?): Iterable<AssetTransaction> {
        val list = byAsset[asset] ?: return emptyList()
        return if (after == null) list else list.filter { it.timestamp >= after }
    }

    override suspend fun getEarliestTransaction(asset: Uuid, after: Instant?): AssetTransaction? {
        val list = byAsset[asset] ?: return null
        return if (after == null) list.minByOrNull { it.timestamp }
        else list.filter { it.timestamp >= after }.minByOrNull { it.timestamp }
    }
}

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class AggregationBenchmark {

    @Param("1000", "10000", "100000", "1000000")
    open var transactionCount: Int = 0

    private lateinit var service: AggregationService
    private lateinit var assetId: Uuid
    private lateinit var source: BenchmarkTransactionSource
    private lateinit var endDate: LocalDate
    private lateinit var staleDate: LocalDate
    private var initialHolding: Double = 0.0
    private var initialPrice: Double = 0.0
    private lateinit var initialPriceDate: LocalDate
    private lateinit var marketPrices: Map<LocalDate, Double>

    private val timezone = TimeZone.UTC
    private val baseTime = Instant.parse("2020-01-01T00:00:00Z")

    @Setup(Level.Trial)
    fun setup() {
        service = AggregationService()
        assetId = Uuid.random()

        val rng = Random(42)
        val transactions = buildList(transactionCount) {
            for (i in 0..transactionCount) {
                // Spread transactions across a date range proportional to count
                // ~10 transactions per day on average
                val dayOffset = i / 10
                val hourOffset = rng.nextInt(0, 24)
                val minuteOffset = rng.nextInt(0, 60)
                val ts = baseTime + dayOffset.days + hourOffset.hours + minuteOffset.minutes

                val isTrade = rng.nextDouble() < 0.85
                val amount = if (isTrade) {
                    // Mix of buys and sells, mostly buys to keep holding positive
                    if (rng.nextDouble() < 0.7) rng.nextDouble(0.1, 100.0)
                    else -rng.nextDouble(0.1, 20.0)
                } else {
                    rng.nextDouble(10.0, 500.0)
                }
                val price = rng.nextDouble(5.0, 200.0)

                add(
                    AssetTransaction(
                        assetId = assetId,
                        timestamp = ts,
                        type = if (isTrade) AssetTransactionType.Trade else AssetTransactionType.Reconciliation,
                        amount = amount,
                        value = kotlin.math.abs(amount) * price,
                    )
                )
            }
        }

        source = BenchmarkTransactionSource(transactions)

        val totalDays = transactionCount / 10
        endDate = (baseTime + totalDays.days).toLocalDateTime(timezone).date

        // For partial reaggregation: stale point at 75% through the timeline
        val staleDayOffset = (totalDays * 3) / 4
        staleDate = (baseTime + staleDayOffset.days).toLocalDateTime(timezone).date

        // Simulate initial state at the stale point (as if we read it from the last valid aggregate)
        initialHolding = 1500.0
        initialPrice = 50.0
        initialPriceDate = staleDate

        // Build market prices for market-data benchmarks (one price per day)
        val priceMap = mutableMapOf<LocalDate, Double>()
        var price = 100.0
        for (d in 0..totalDays) {
            val date = (baseTime + d.days).toLocalDateTime(timezone).date
            price += rng.nextDouble(-3.0, 3.5) // slight upward drift
            priceMap[date] = price
        }
        marketPrices = priceMap
    }

    @Benchmark
    fun fullAggregation(bh: Blackhole) {
        runBlocking {
            val result = service.forwardAggregate(
                asset = assetId,
                timezone = timezone,
                ats = source,
                end = endDate,
            )
            bh.consume(result)
        }
    }

    @Benchmark
    fun partialAggregation(bh: Blackhole) {
        runBlocking {
            val result = service.forwardAggregate(
                asset = assetId,
                timezone = timezone,
                ats = source,
                end = endDate,
                startOverride = staleDate,
                initialHolding = initialHolding,
                initialPrice = initialPrice,
                initialPriceDate = initialPriceDate,
            )
            bh.consume(result)
        }
    }

    @Benchmark
    fun fullAggregationWithMarketPrices(bh: Blackhole) {
        runBlocking {
            val result = service.forwardAggregate(
                asset = assetId,
                timezone = timezone,
                ats = source,
                end = endDate,
                marketPrices = marketPrices,
            )
            bh.consume(result)
        }
    }

    @Benchmark
    fun partialAggregationWithMarketPrices(bh: Blackhole) {
        runBlocking {
            val result = service.forwardAggregate(
                asset = assetId,
                timezone = timezone,
                ats = source,
                end = endDate,
                marketPrices = marketPrices,
                startOverride = staleDate,
                initialHolding = initialHolding,
                initialPrice = initialPrice,
                initialPriceDate = initialPriceDate,
            )
            bh.consume(result)
        }
    }
}
