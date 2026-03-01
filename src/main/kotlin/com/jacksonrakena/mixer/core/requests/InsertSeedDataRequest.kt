package com.jacksonrakena.mixer.core.requests

import com.jacksonrakena.mixer.data.AssetTransactionType
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.data.tables.concrete.UserRole
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

private data class SeedAsset(val ref: String, val name: String, val currency: String)
private data class SeedTransaction(val assetRef: String, val daysAgo: Int, val type: AssetTransactionType, val amount: Double, val value: Double)

@Serializable
class InsertSeedDataRequest: JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return InsertSeedDataRequestHandler::class.java
    }

    @Component
    class InsertSeedDataRequestHandler(
        val database: Database,
        val jobRequestScheduler: JobRequestScheduler,
        val passwordEncoder: PasswordEncoder,
    ) : JobRequestHandler<InsertSeedDataRequest> {
        private val logger = KotlinLogging.logger {}
        private fun loadCsv(path: String): List<List<String>> {
            val stream = this::class.java.getResourceAsStream(path)
                ?: throw IllegalStateException("Seed data file not found: $path")
            return stream.bufferedReader().useLines { lines ->
                lines.drop(1) // skip header
                    .filter { it.isNotBlank() }
                    .map { line -> line.split(",").map { it.trim() } }
                    .toList()
            }
        }

        private fun loadAssets(): List<SeedAsset> =
            loadCsv("/seed/assets.csv").map { cols ->
                SeedAsset(ref = cols[0], name = cols[1], currency = cols[2])
            }

        private fun loadTransactions(): List<SeedTransaction> =
            loadCsv("/seed/transactions.csv").map { cols ->
                SeedTransaction(
                    assetRef = cols[0],
                    daysAgo = cols[1].toInt(),
                    type = AssetTransactionType.valueOf(cols[2]),
                    amount = cols[3].toDouble(),
                    value = cols[4].toDouble(),
                )
            }

        override fun run(request: InsertSeedDataRequest?) {
            if (request == null) {
                logger.warn { "Received null request for InsertSeedDataRequestHandler" }
                return
            }

            val time = Instant.parse("2026-02-16T23:05:37.337365Z")
            val userId = Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")

            val existingUser = transaction {
                User.selectAll().where { User.id eq userId }.firstOrNull()
            }
            if (existingUser != null) {
                logger.info { "Seed user already exists, skipping seed data insertion" }
                return
            }

            val seedAssets = loadAssets()
            val seedTransactions = loadTransactions()
            val assetIdsByRef = seedAssets.associate { it.ref to Uuid.random() }

            transaction {
                User.insert {
                    it[User.id] = userId
                    it[User.email] = "admin@mixer.local"
                    it[User.passwordHash] = passwordEncoder.encode("admin123")!!
                    it[User.displayName] = "Administrator"
                    it[User.emailVerified] = true
                    it[User.timezone] = "Australia/Sydney"
                }
                UserRole.insert {
                    it[UserRole.userId] = userId
                    it[UserRole.role] = "GLOBAL_ADMIN"
                }

                for (asset in seedAssets) {
                    Asset.insert {
                        it[Asset.id] = assetIdsByRef[asset.ref]!!
                        it[Asset.name] = asset.name
                        it[Asset.currency] = asset.currency
                        it[Asset.ownerId] = userId
                        it[Asset.provider] = "USER"
                    }
                }

                val rng = java.util.Random(42) // fixed seed for reproducible data
                Transaction.batchInsert(seedTransactions) {
                    val assetId = assetIdsByRef[it.assetRef]
                        ?: throw IllegalStateException("Unknown asset ref in transactions.csv: ${it.assetRef}")
                    val randomSecondsInDay = rng.nextInt(86400).toLong()
                    this[Transaction.assetId] = assetId
                    this[Transaction.timestamp] = (time - it.daysAgo.days).toEpochMilliseconds() + (randomSecondsInDay * 1000)
                    this[Transaction.type] = it.type
                    this[Transaction.amount] = it.amount
                    this[Transaction.value] = it.value
                }
            }

            logger.info { "Finished seed data: ${seedAssets.size} assets, ${seedTransactions.size} transactions" }
//            jobRequestScheduler.enqueue(RecomputeUserAggregationRequest(userId))
        }
    }
}
