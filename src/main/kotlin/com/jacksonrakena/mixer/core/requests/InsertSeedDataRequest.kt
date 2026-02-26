package com.jacksonrakena.mixer.core.requests

import com.jacksonrakena.mixer.data.AssetTransaction
import com.jacksonrakena.mixer.data.AssetTransactionType
import com.jacksonrakena.mixer.data.tables.concrete.Asset
import com.jacksonrakena.mixer.data.tables.concrete.Transaction
import com.jacksonrakena.mixer.data.tables.concrete.User
import com.jacksonrakena.mixer.upstream.CurrencyService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.jobs.lambdas.JobRequest
import org.jobrunr.jobs.lambdas.JobRequestHandler
import org.jobrunr.scheduling.JobRequestScheduler
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val logger = KotlinLogging.logger {}

@Serializable
class InsertSeedDataRequest: JobRequest {
    override fun getJobRequestHandler(): Class<out JobRequestHandler<*>?> {
        return InsertSeedDataRequestHandler::class.java
    }

    @Component
    class InsertSeedDataRequestHandler(
        val database: Database,
        val currencyService: CurrencyService,
        val jobRequestScheduler: JobRequestScheduler
    ) : JobRequestHandler<InsertSeedDataRequest> {
        override fun run(request: InsertSeedDataRequest?) {
            if (request == null) {
                logger.warn { "Received null request for InsertSeedDataRequestHandler" }
                return
            }

            val time = Instant.parse("2026-02-16T23:05:37.337365Z")
            val assetId = Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")
            transaction {
                User.insert {
                    it[User.id] = Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")
                    it[User.timezone] = "Australia/Sydney"
                }
                Asset.insert {
                    it[Asset.id] = assetId
                    it[Asset.name] = "Atlassian (TEAM)"
                    it[Asset.currency] = "USD"
                    it[Asset.ownerId] = Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")
                    it[Asset.provider] = "USER"
                }
                Transaction.batchInsert(
                    listOf(
                        // Buy 1 @ ~$309
                        AssetTransaction(assetId = assetId, timestamp = time - 365.days, type = AssetTransactionType.Trade, amount = 1.0, value = 308.52),
                        // Buy 1 @ ~$316
                        AssetTransaction(assetId = assetId, timestamp = time - 364.days, type = AssetTransactionType.Trade, amount = 1.0, value = 316.01),
                        // Buy 4 @ ~$312
                        AssetTransaction(assetId = assetId, timestamp = time - 362.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1249.84),
                        // Buy 2 @ ~$308
                        AssetTransaction(assetId = assetId, timestamp = time - 361.days, type = AssetTransactionType.Trade, amount = 2.0, value = 616.97),
                        // Buy 2 @ ~$307
                        AssetTransaction(assetId = assetId, timestamp = time - 358.days, type = AssetTransactionType.Trade, amount = 2.0, value = 613.97),
                        // Buy 4 @ ~$309
                        AssetTransaction(assetId = assetId, timestamp = time - 356.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1237.45),
                        // Buy 2 @ ~$305
                        AssetTransaction(assetId = assetId, timestamp = time - 356.days, type = AssetTransactionType.Trade, amount = 2.0, value = 609.86),
                        // Buy 4 @ ~$309
                        AssetTransaction(assetId = assetId, timestamp = time - 355.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1235.21),
                        // Buy 2 @ ~$304
                        AssetTransaction(assetId = assetId, timestamp = time - 355.days, type = AssetTransactionType.Trade, amount = 2.0, value = 607.47),
                        // Buy 2 @ ~$310
                        AssetTransaction(assetId = assetId, timestamp = time - 354.days, type = AssetTransactionType.Trade, amount = 2.0, value = 620.67),
                        // Buy 4 @ ~$304
                        AssetTransaction(assetId = assetId, timestamp = time - 354.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1215.41),
                        // Sell 1 @ ~$307
                        AssetTransaction(assetId = assetId, timestamp = time - 353.days, type = AssetTransactionType.Trade, amount = -1.0, value = 307.16),
                        // Buy 4 @ ~$309
                        AssetTransaction(assetId = assetId, timestamp = time - 351.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1235.42),
                        // Sell 1 @ ~$306
                        AssetTransaction(assetId = assetId, timestamp = time - 350.days, type = AssetTransactionType.Trade, amount = -1.0, value = 305.87),
                        // Sell 3 @ ~$302
                        AssetTransaction(assetId = assetId, timestamp = time - 349.days, type = AssetTransactionType.Trade, amount = -3.0, value = 904.56),
                        // Buy 2 @ ~$300
                        AssetTransaction(assetId = assetId, timestamp = time - 348.days, type = AssetTransactionType.Trade, amount = 2.0, value = 599.39),
                        // Sell 2 @ ~$298
                        AssetTransaction(assetId = assetId, timestamp = time - 348.days, type = AssetTransactionType.Trade, amount = -2.0, value = 596.28),
                        // Buy 1 @ ~$300
                        AssetTransaction(assetId = assetId, timestamp = time - 347.days, type = AssetTransactionType.Trade, amount = 1.0, value = 299.63),
                        // Buy 2 @ ~$297
                        AssetTransaction(assetId = assetId, timestamp = time - 342.days, type = AssetTransactionType.Trade, amount = 2.0, value = 593.86),
                        // Buy 2 @ ~$293
                        AssetTransaction(assetId = assetId, timestamp = time - 341.days, type = AssetTransactionType.Trade, amount = 2.0, value = 585.64),
                        // Buy 4 @ ~$288
                        AssetTransaction(assetId = assetId, timestamp = time - 340.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1153.84),
                        // Buy 4 @ ~$287
                        AssetTransaction(assetId = assetId, timestamp = time - 335.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1149.18),
                        // Buy 4 @ ~$287
                        AssetTransaction(assetId = assetId, timestamp = time - 334.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1149.16),
                        // Buy 1 @ ~$282
                        AssetTransaction(assetId = assetId, timestamp = time - 334.days, type = AssetTransactionType.Trade, amount = 1.0, value = 281.57),
                        // Sell 3 @ ~$283
                        AssetTransaction(assetId = assetId, timestamp = time - 333.days, type = AssetTransactionType.Trade, amount = -3.0, value = 848.28),
                        // Sell 1 @ ~$280
                        AssetTransaction(assetId = assetId, timestamp = time - 332.days, type = AssetTransactionType.Trade, amount = -1.0, value = 279.97),
                        // Buy 1 @ ~$278
                        AssetTransaction(assetId = assetId, timestamp = time - 332.days, type = AssetTransactionType.Trade, amount = 1.0, value = 278.09),
                        // Buy 5 @ ~$280
                        AssetTransaction(assetId = assetId, timestamp = time - 330.days, type = AssetTransactionType.Trade, amount = 5.0, value = 1397.54),
                        // Buy 4 @ ~$281
                        AssetTransaction(assetId = assetId, timestamp = time - 329.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1125.74),
                        // Buy 3 @ ~$276
                        AssetTransaction(assetId = assetId, timestamp = time - 329.days, type = AssetTransactionType.Trade, amount = 3.0, value = 828.87),
                        // Buy 1 @ ~$276
                        AssetTransaction(assetId = assetId, timestamp = time - 328.days, type = AssetTransactionType.Trade, amount = 1.0, value = 276.24),
                        // Buy 1 @ ~$277
                        AssetTransaction(assetId = assetId, timestamp = time - 328.days, type = AssetTransactionType.Trade, amount = 1.0, value = 277.37),
                        // Sell 1 @ ~$272
                        AssetTransaction(assetId = assetId, timestamp = time - 327.days, type = AssetTransactionType.Trade, amount = -1.0, value = 271.79),
                        // Buy 2 @ ~$271
                        AssetTransaction(assetId = assetId, timestamp = time - 326.days, type = AssetTransactionType.Trade, amount = 2.0, value = 541.42),
                        // Buy 6 @ ~$275
                        AssetTransaction(assetId = assetId, timestamp = time - 326.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1647.38),
                        // Sell 4 @ ~$269
                        AssetTransaction(assetId = assetId, timestamp = time - 324.days, type = AssetTransactionType.Trade, amount = -4.0, value = 1077.49),
                        // Buy 1 @ ~$270
                        AssetTransaction(assetId = assetId, timestamp = time - 323.days, type = AssetTransactionType.Trade, amount = 1.0, value = 270.02),
                        // Sell 2 @ ~$271
                        AssetTransaction(assetId = assetId, timestamp = time - 321.days, type = AssetTransactionType.Trade, amount = -2.0, value = 541.8),
                        // Buy 1 @ ~$266
                        AssetTransaction(assetId = assetId, timestamp = time - 319.days, type = AssetTransactionType.Trade, amount = 1.0, value = 265.63),
                        // Buy 2 @ ~$261
                        AssetTransaction(assetId = assetId, timestamp = time - 317.days, type = AssetTransactionType.Trade, amount = 2.0, value = 521.03),
                        // Sell 4 @ ~$266
                        AssetTransaction(assetId = assetId, timestamp = time - 316.days, type = AssetTransactionType.Trade, amount = -4.0, value = 1063.0),
                        // Buy 4 @ ~$263
                        AssetTransaction(assetId = assetId, timestamp = time - 316.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1051.4),
                        // Buy 3 @ ~$266
                        AssetTransaction(assetId = assetId, timestamp = time - 315.days, type = AssetTransactionType.Trade, amount = 3.0, value = 797.23),
                        // Buy 4 @ ~$262
                        AssetTransaction(assetId = assetId, timestamp = time - 314.days, type = AssetTransactionType.Trade, amount = 4.0, value = 1049.63),
                        // Buy 6 @ ~$257
                        AssetTransaction(assetId = assetId, timestamp = time - 313.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1542.76),
                        // Buy 2 @ ~$255
                        AssetTransaction(assetId = assetId, timestamp = time - 312.days, type = AssetTransactionType.Trade, amount = 2.0, value = 510.73),
                        // Sell 5 @ ~$252
                        AssetTransaction(assetId = assetId, timestamp = time - 309.days, type = AssetTransactionType.Trade, amount = -5.0, value = 1262.36),
                        // Buy 1 @ ~$252
                        AssetTransaction(assetId = assetId, timestamp = time - 309.days, type = AssetTransactionType.Trade, amount = 1.0, value = 252.38),
                        // Sell 6 @ ~$251
                        AssetTransaction(assetId = assetId, timestamp = time - 308.days, type = AssetTransactionType.Trade, amount = -6.0, value = 1507.01),
                        // Buy 2 @ ~$254
                        AssetTransaction(assetId = assetId, timestamp = time - 307.days, type = AssetTransactionType.Trade, amount = 2.0, value = 507.73),
                        // Buy 2 @ ~$252
                        AssetTransaction(assetId = assetId, timestamp = time - 306.days, type = AssetTransactionType.Trade, amount = 2.0, value = 503.83),
                        // Sell 5 @ ~$255
                        AssetTransaction(assetId = assetId, timestamp = time - 305.days, type = AssetTransactionType.Trade, amount = -5.0, value = 1275.14),
                        // Buy 1 @ ~$256
                        AssetTransaction(assetId = assetId, timestamp = time - 305.days, type = AssetTransactionType.Trade, amount = 1.0, value = 255.5),
                        // Buy 2 @ ~$246
                        AssetTransaction(assetId = assetId, timestamp = time - 302.days, type = AssetTransactionType.Trade, amount = 2.0, value = 491.42),
                        // Buy 6 @ ~$250
                        AssetTransaction(assetId = assetId, timestamp = time - 301.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1497.3),
                        // Buy 4 @ ~$248
                        AssetTransaction(assetId = assetId, timestamp = time - 301.days, type = AssetTransactionType.Trade, amount = 4.0, value = 993.0),
                        // Buy 2 @ ~$245
                        AssetTransaction(assetId = assetId, timestamp = time - 299.days, type = AssetTransactionType.Trade, amount = 2.0, value = 489.16),
                        // Buy 2 @ ~$245
                        AssetTransaction(assetId = assetId, timestamp = time - 299.days, type = AssetTransactionType.Trade, amount = 2.0, value = 489.27),
                        // Buy 1 @ ~$248
                        AssetTransaction(assetId = assetId, timestamp = time - 298.days, type = AssetTransactionType.Trade, amount = 1.0, value = 248.1),
                        // Buy 3 @ ~$240
                        AssetTransaction(assetId = assetId, timestamp = time - 295.days, type = AssetTransactionType.Trade, amount = 3.0, value = 718.83),
                        // Buy 1 @ ~$243
                        AssetTransaction(assetId = assetId, timestamp = time - 293.days, type = AssetTransactionType.Trade, amount = 1.0, value = 243.39),
                        // Buy 1 @ ~$244
                        AssetTransaction(assetId = assetId, timestamp = time - 292.days, type = AssetTransactionType.Trade, amount = 1.0, value = 243.71),
                        // Buy 4 @ ~$236
                        AssetTransaction(assetId = assetId, timestamp = time - 289.days, type = AssetTransactionType.Trade, amount = 4.0, value = 945.58),
                        // Buy 5 @ ~$237
                        AssetTransaction(assetId = assetId, timestamp = time - 289.days, type = AssetTransactionType.Trade, amount = 5.0, value = 1184.24),
                        // Buy 1 @ ~$241
                        AssetTransaction(assetId = assetId, timestamp = time - 288.days, type = AssetTransactionType.Trade, amount = 1.0, value = 241.05),
                        // Buy 4 @ ~$243
                        AssetTransaction(assetId = assetId, timestamp = time - 288.days, type = AssetTransactionType.Trade, amount = 4.0, value = 970.71),
                        // Buy 4 @ ~$241
                        AssetTransaction(assetId = assetId, timestamp = time - 286.days, type = AssetTransactionType.Trade, amount = 4.0, value = 962.08),
                        // Buy 2 @ ~$241
                        AssetTransaction(assetId = assetId, timestamp = time - 286.days, type = AssetTransactionType.Trade, amount = 2.0, value = 481.45),
                        // Buy 2 @ ~$235
                        AssetTransaction(assetId = assetId, timestamp = time - 285.days, type = AssetTransactionType.Trade, amount = 2.0, value = 469.77),
                        // Buy 1 @ ~$240
                        AssetTransaction(assetId = assetId, timestamp = time - 284.days, type = AssetTransactionType.Trade, amount = 1.0, value = 240.2),
                        // Buy 4 @ ~$236
                        AssetTransaction(assetId = assetId, timestamp = time - 280.days, type = AssetTransactionType.Trade, amount = 4.0, value = 944.53),
                        // Buy 2 @ ~$234
                        AssetTransaction(assetId = assetId, timestamp = time - 279.days, type = AssetTransactionType.Trade, amount = 2.0, value = 467.48),
                        // Reconciliation: 117 shares @ ~$237
                        AssetTransaction(assetId = assetId, timestamp = time - 277.days, type = AssetTransactionType.Reconciliation, amount = 117.0, value = 27729.59),
                        // Buy 1 @ ~$238
                        AssetTransaction(assetId = assetId, timestamp = time - 274.days, type = AssetTransactionType.Trade, amount = 1.0, value = 237.79),
                        // Sell 5 @ ~$239
                        AssetTransaction(assetId = assetId, timestamp = time - 273.days, type = AssetTransactionType.Trade, amount = -5.0, value = 1192.81),
                        // Buy 2 @ ~$240
                        AssetTransaction(assetId = assetId, timestamp = time - 272.days, type = AssetTransactionType.Trade, amount = 2.0, value = 480.52),
                        // Buy 4 @ ~$236
                        AssetTransaction(assetId = assetId, timestamp = time - 270.days, type = AssetTransactionType.Trade, amount = 4.0, value = 944.16),
                        // Buy 4 @ ~$238
                        AssetTransaction(assetId = assetId, timestamp = time - 269.days, type = AssetTransactionType.Trade, amount = 4.0, value = 953.97),
                        // Buy 6 @ ~$241
                        AssetTransaction(assetId = assetId, timestamp = time - 267.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1443.19),
                        // Buy 1 @ ~$237
                        AssetTransaction(assetId = assetId, timestamp = time - 266.days, type = AssetTransactionType.Trade, amount = 1.0, value = 236.75),
                        // Buy 2 @ ~$238
                        AssetTransaction(assetId = assetId, timestamp = time - 265.days, type = AssetTransactionType.Trade, amount = 2.0, value = 475.98),
                        // Buy 6 @ ~$237
                        AssetTransaction(assetId = assetId, timestamp = time - 264.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1424.88),
                        // Buy 4 @ ~$236
                        AssetTransaction(assetId = assetId, timestamp = time - 260.days, type = AssetTransactionType.Trade, amount = 4.0, value = 943.2),
                        // Sell 1 @ ~$239
                        AssetTransaction(assetId = assetId, timestamp = time - 259.days, type = AssetTransactionType.Trade, amount = -1.0, value = 238.94),
                        // Buy 2 @ ~$236
                        AssetTransaction(assetId = assetId, timestamp = time - 258.days, type = AssetTransactionType.Trade, amount = 2.0, value = 472.63),
                        // Buy 2 @ ~$237
                        AssetTransaction(assetId = assetId, timestamp = time - 257.days, type = AssetTransactionType.Trade, amount = 2.0, value = 473.42),
                        // Buy 1 @ ~$234
                        AssetTransaction(assetId = assetId, timestamp = time - 257.days, type = AssetTransactionType.Trade, amount = 1.0, value = 234.36),
                        // Buy 3 @ ~$234
                        AssetTransaction(assetId = assetId, timestamp = time - 256.days, type = AssetTransactionType.Trade, amount = 3.0, value = 701.41),
                        // Buy 4 @ ~$234
                        AssetTransaction(assetId = assetId, timestamp = time - 253.days, type = AssetTransactionType.Trade, amount = 4.0, value = 935.81),
                        // Buy 6 @ ~$235
                        AssetTransaction(assetId = assetId, timestamp = time - 253.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1409.58),
                        // Buy 5 @ ~$236
                        AssetTransaction(assetId = assetId, timestamp = time - 252.days, type = AssetTransactionType.Trade, amount = 5.0, value = 1181.77),
                        // Buy 1 @ ~$235
                        AssetTransaction(assetId = assetId, timestamp = time - 251.days, type = AssetTransactionType.Trade, amount = 1.0, value = 234.58),
                        // Buy 3 @ ~$235
                        AssetTransaction(assetId = assetId, timestamp = time - 251.days, type = AssetTransactionType.Trade, amount = 3.0, value = 705.76),
                        // Sell 3 @ ~$231
                        AssetTransaction(assetId = assetId, timestamp = time - 250.days, type = AssetTransactionType.Trade, amount = -3.0, value = 691.52),
                        // Buy 1 @ ~$230
                        AssetTransaction(assetId = assetId, timestamp = time - 249.days, type = AssetTransactionType.Trade, amount = 1.0, value = 230.06),
                        // Buy 2 @ ~$225
                        AssetTransaction(assetId = assetId, timestamp = time - 246.days, type = AssetTransactionType.Trade, amount = 2.0, value = 450.17),
                        // Buy 4 @ ~$225
                        AssetTransaction(assetId = assetId, timestamp = time - 244.days, type = AssetTransactionType.Trade, amount = 4.0, value = 899.33),
                        // Buy 1 @ ~$228
                        AssetTransaction(assetId = assetId, timestamp = time - 244.days, type = AssetTransactionType.Trade, amount = 1.0, value = 227.99),
                        // Buy 5 @ ~$223
                        AssetTransaction(assetId = assetId, timestamp = time - 243.days, type = AssetTransactionType.Trade, amount = 5.0, value = 1114.33),
                        // Buy 2 @ ~$224
                        AssetTransaction(assetId = assetId, timestamp = time - 243.days, type = AssetTransactionType.Trade, amount = 2.0, value = 448.95),
                        // Buy 2 @ ~$221
                        AssetTransaction(assetId = assetId, timestamp = time - 242.days, type = AssetTransactionType.Trade, amount = 2.0, value = 442.53),
                        // Buy 6 @ ~$223
                        AssetTransaction(assetId = assetId, timestamp = time - 240.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1337.18),
                        // Sell 6 @ ~$220
                        AssetTransaction(assetId = assetId, timestamp = time - 240.days, type = AssetTransactionType.Trade, amount = -6.0, value = 1320.72),
                        // Sell 3 @ ~$220
                        AssetTransaction(assetId = assetId, timestamp = time - 239.days, type = AssetTransactionType.Trade, amount = -3.0, value = 661.35),
                        // Buy 3 @ ~$214
                        AssetTransaction(assetId = assetId, timestamp = time - 238.days, type = AssetTransactionType.Trade, amount = 3.0, value = 643.42),
                        // Buy 3 @ ~$217
                        AssetTransaction(assetId = assetId, timestamp = time - 237.days, type = AssetTransactionType.Trade, amount = 3.0, value = 652.45),
                        // Buy 4 @ ~$214
                        AssetTransaction(assetId = assetId, timestamp = time - 235.days, type = AssetTransactionType.Trade, amount = 4.0, value = 856.28),
                        // Buy 2 @ ~$207
                        AssetTransaction(assetId = assetId, timestamp = time - 232.days, type = AssetTransactionType.Trade, amount = 2.0, value = 414.44),
                        // Sell 6 @ ~$204
                        AssetTransaction(assetId = assetId, timestamp = time - 231.days, type = AssetTransactionType.Trade, amount = -6.0, value = 1221.33),
                        // Buy 6 @ ~$200
                        AssetTransaction(assetId = assetId, timestamp = time - 230.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1202.39),
                        // Sell 1 @ ~$200
                        AssetTransaction(assetId = assetId, timestamp = time - 228.days, type = AssetTransactionType.Trade, amount = -1.0, value = 200.0),
                        // Buy 4 @ ~$198
                        AssetTransaction(assetId = assetId, timestamp = time - 227.days, type = AssetTransactionType.Trade, amount = 4.0, value = 790.76),
                        // Sell 3 @ ~$192
                        AssetTransaction(assetId = assetId, timestamp = time - 225.days, type = AssetTransactionType.Trade, amount = -3.0, value = 576.65),
                        // Sell 5 @ ~$190
                        AssetTransaction(assetId = assetId, timestamp = time - 224.days, type = AssetTransactionType.Trade, amount = -5.0, value = 951.06),
                        // Buy 2 @ ~$191
                        AssetTransaction(assetId = assetId, timestamp = time - 224.days, type = AssetTransactionType.Trade, amount = 2.0, value = 382.5),
                        // Buy 2 @ ~$183
                        AssetTransaction(assetId = assetId, timestamp = time - 222.days, type = AssetTransactionType.Trade, amount = 2.0, value = 366.77),
                        // Buy 3 @ ~$185
                        AssetTransaction(assetId = assetId, timestamp = time - 221.days, type = AssetTransactionType.Trade, amount = 3.0, value = 555.55),
                        // Buy 4 @ ~$183
                        AssetTransaction(assetId = assetId, timestamp = time - 221.days, type = AssetTransactionType.Trade, amount = 4.0, value = 730.56),
                        // Buy 3 @ ~$183
                        AssetTransaction(assetId = assetId, timestamp = time - 220.days, type = AssetTransactionType.Trade, amount = 3.0, value = 548.23),
                        // Buy 2 @ ~$182
                        AssetTransaction(assetId = assetId, timestamp = time - 218.days, type = AssetTransactionType.Trade, amount = 2.0, value = 364.45),
                        // Buy 2 @ ~$179
                        AssetTransaction(assetId = assetId, timestamp = time - 217.days, type = AssetTransactionType.Trade, amount = 2.0, value = 358.88),
                        // Buy 6 @ ~$178
                        AssetTransaction(assetId = assetId, timestamp = time - 215.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1066.31),
                        // Buy 1 @ ~$175
                        AssetTransaction(assetId = assetId, timestamp = time - 214.days, type = AssetTransactionType.Trade, amount = 1.0, value = 175.39),
                        // Buy 4 @ ~$176
                        AssetTransaction(assetId = assetId, timestamp = time - 214.days, type = AssetTransactionType.Trade, amount = 4.0, value = 705.03),
                        // Buy 1 @ ~$170
                        AssetTransaction(assetId = assetId, timestamp = time - 211.days, type = AssetTransactionType.Trade, amount = 1.0, value = 170.12),
                        // Buy 2 @ ~$172
                        AssetTransaction(assetId = assetId, timestamp = time - 210.days, type = AssetTransactionType.Trade, amount = 2.0, value = 344.87),
                        // Buy 2 @ ~$172
                        AssetTransaction(assetId = assetId, timestamp = time - 209.days, type = AssetTransactionType.Trade, amount = 2.0, value = 343.56),
                        // Sell 2 @ ~$170
                        AssetTransaction(assetId = assetId, timestamp = time - 209.days, type = AssetTransactionType.Trade, amount = -2.0, value = 340.48),
                        // Buy 2 @ ~$172
                        AssetTransaction(assetId = assetId, timestamp = time - 208.days, type = AssetTransactionType.Trade, amount = 2.0, value = 344.76),
                        // Buy 6 @ ~$171
                        AssetTransaction(assetId = assetId, timestamp = time - 207.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1024.63),
                        // Buy 1 @ ~$170
                        AssetTransaction(assetId = assetId, timestamp = time - 205.days, type = AssetTransactionType.Trade, amount = 1.0, value = 169.56),
                        // Buy 4 @ ~$169
                        AssetTransaction(assetId = assetId, timestamp = time - 205.days, type = AssetTransactionType.Trade, amount = 4.0, value = 677.8),
                        // Buy 1 @ ~$171
                        AssetTransaction(assetId = assetId, timestamp = time - 204.days, type = AssetTransactionType.Trade, amount = 1.0, value = 171.34),
                        // Buy 1 @ ~$171
                        AssetTransaction(assetId = assetId, timestamp = time - 204.days, type = AssetTransactionType.Trade, amount = 1.0, value = 171.01),
                        // Buy 1 @ ~$169
                        AssetTransaction(assetId = assetId, timestamp = time - 203.days, type = AssetTransactionType.Trade, amount = 1.0, value = 169.48),
                        // Sell 6 @ ~$170
                        AssetTransaction(assetId = assetId, timestamp = time - 203.days, type = AssetTransactionType.Trade, amount = -6.0, value = 1021.43),
                        // Buy 4 @ ~$169
                        AssetTransaction(assetId = assetId, timestamp = time - 202.days, type = AssetTransactionType.Trade, amount = 4.0, value = 676.66),
                        // Buy 4 @ ~$169
                        AssetTransaction(assetId = assetId, timestamp = time - 201.days, type = AssetTransactionType.Trade, amount = 4.0, value = 677.12),
                        // Buy 2 @ ~$170
                        AssetTransaction(assetId = assetId, timestamp = time - 201.days, type = AssetTransactionType.Trade, amount = 2.0, value = 339.66),
                        // Buy 2 @ ~$172
                        AssetTransaction(assetId = assetId, timestamp = time - 200.days, type = AssetTransactionType.Trade, amount = 2.0, value = 344.12),
                        // Sell 6 @ ~$172
                        AssetTransaction(assetId = assetId, timestamp = time - 197.days, type = AssetTransactionType.Trade, amount = -6.0, value = 1029.35),
                        // Buy 2 @ ~$171
                        AssetTransaction(assetId = assetId, timestamp = time - 197.days, type = AssetTransactionType.Trade, amount = 2.0, value = 341.8),
                        // Buy 2 @ ~$175
                        AssetTransaction(assetId = assetId, timestamp = time - 195.days, type = AssetTransactionType.Trade, amount = 2.0, value = 349.59),
                        // Buy 6 @ ~$175
                        AssetTransaction(assetId = assetId, timestamp = time - 194.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1048.37),
                        // Sell 1 @ ~$177
                        AssetTransaction(assetId = assetId, timestamp = time - 193.days, type = AssetTransactionType.Trade, amount = -1.0, value = 177.26),
                        // Sell 2 @ ~$180
                        AssetTransaction(assetId = assetId, timestamp = time - 190.days, type = AssetTransactionType.Trade, amount = -2.0, value = 359.05),
                        // Buy 3 @ ~$179
                        AssetTransaction(assetId = assetId, timestamp = time - 190.days, type = AssetTransactionType.Trade, amount = 3.0, value = 538.03),
                        // Buy 2 @ ~$178
                        AssetTransaction(assetId = assetId, timestamp = time - 189.days, type = AssetTransactionType.Trade, amount = 2.0, value = 356.63),
                        // Sell 1 @ ~$179
                        AssetTransaction(assetId = assetId, timestamp = time - 188.days, type = AssetTransactionType.Trade, amount = -1.0, value = 179.14),
                        // Reconciliation: 246 shares @ ~$180
                        AssetTransaction(assetId = assetId, timestamp = time - 187.days, type = AssetTransactionType.Reconciliation, amount = 246.0, value = 44239.09),
                        // Sell 2 @ ~$181
                        AssetTransaction(assetId = assetId, timestamp = time - 186.days, type = AssetTransactionType.Trade, amount = -2.0, value = 362.06),
                        // Buy 6 @ ~$181
                        AssetTransaction(assetId = assetId, timestamp = time - 185.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1088.87),
                        // Buy 2 @ ~$183
                        AssetTransaction(assetId = assetId, timestamp = time - 185.days, type = AssetTransactionType.Trade, amount = 2.0, value = 365.63),
                        // Sell 5 @ ~$184
                        AssetTransaction(assetId = assetId, timestamp = time - 183.days, type = AssetTransactionType.Trade, amount = -5.0, value = 922.08),
                        // Buy 2 @ ~$184
                        AssetTransaction(assetId = assetId, timestamp = time - 182.days, type = AssetTransactionType.Trade, amount = 2.0, value = 368.66),
                        // Buy 2 @ ~$184
                        AssetTransaction(assetId = assetId, timestamp = time - 181.days, type = AssetTransactionType.Trade, amount = 2.0, value = 367.9),
                        // Buy 3 @ ~$186
                        AssetTransaction(assetId = assetId, timestamp = time - 180.days, type = AssetTransactionType.Trade, amount = 3.0, value = 557.45),
                        // Buy 2 @ ~$186
                        AssetTransaction(assetId = assetId, timestamp = time - 176.days, type = AssetTransactionType.Trade, amount = 2.0, value = 372.32),
                        // Buy 4 @ ~$182
                        AssetTransaction(assetId = assetId, timestamp = time - 175.days, type = AssetTransactionType.Trade, amount = 4.0, value = 729.8),
                        // Buy 2 @ ~$182
                        AssetTransaction(assetId = assetId, timestamp = time - 174.days, type = AssetTransactionType.Trade, amount = 2.0, value = 363.33),
                        // Sell 6 @ ~$185
                        AssetTransaction(assetId = assetId, timestamp = time - 173.days, type = AssetTransactionType.Trade, amount = -6.0, value = 1110.8),
                        // Sell 5 @ ~$182
                        AssetTransaction(assetId = assetId, timestamp = time - 172.days, type = AssetTransactionType.Trade, amount = -5.0, value = 912.17),
                        // Sell 1 @ ~$185
                        AssetTransaction(assetId = assetId, timestamp = time - 169.days, type = AssetTransactionType.Trade, amount = -1.0, value = 184.88),
                        // Buy 2 @ ~$181
                        AssetTransaction(assetId = assetId, timestamp = time - 168.days, type = AssetTransactionType.Trade, amount = 2.0, value = 361.4),
                        // Buy 1 @ ~$182
                        AssetTransaction(assetId = assetId, timestamp = time - 167.days, type = AssetTransactionType.Trade, amount = 1.0, value = 182.04),
                        // Buy 4 @ ~$184
                        AssetTransaction(assetId = assetId, timestamp = time - 166.days, type = AssetTransactionType.Trade, amount = 4.0, value = 735.72),
                        // Buy 6 @ ~$183
                        AssetTransaction(assetId = assetId, timestamp = time - 165.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1095.92),
                        // Buy 2 @ ~$181
                        AssetTransaction(assetId = assetId, timestamp = time - 162.days, type = AssetTransactionType.Trade, amount = 2.0, value = 361.99),
                        // Buy 3 @ ~$178
                        AssetTransaction(assetId = assetId, timestamp = time - 161.days, type = AssetTransactionType.Trade, amount = 3.0, value = 535.19),
                        // Sell 6 @ ~$177
                        AssetTransaction(assetId = assetId, timestamp = time - 160.days, type = AssetTransactionType.Trade, amount = -6.0, value = 1060.82),
                        // Buy 2 @ ~$176
                        AssetTransaction(assetId = assetId, timestamp = time - 159.days, type = AssetTransactionType.Trade, amount = 2.0, value = 352.77),
                        // Buy 1 @ ~$177
                        AssetTransaction(assetId = assetId, timestamp = time - 158.days, type = AssetTransactionType.Trade, amount = 1.0, value = 177.32),
                        // Buy 5 @ ~$174
                        AssetTransaction(assetId = assetId, timestamp = time - 155.days, type = AssetTransactionType.Trade, amount = 5.0, value = 871.65),
                        // Sell 6 @ ~$170
                        AssetTransaction(assetId = assetId, timestamp = time - 153.days, type = AssetTransactionType.Trade, amount = -6.0, value = 1018.41),
                        // Sell 3 @ ~$175
                        AssetTransaction(assetId = assetId, timestamp = time - 153.days, type = AssetTransactionType.Trade, amount = -3.0, value = 524.0),
                        // Buy 6 @ ~$172
                        AssetTransaction(assetId = assetId, timestamp = time - 152.days, type = AssetTransactionType.Trade, amount = 6.0, value = 1034.51),
                        // Buy 3 @ ~$170
                        AssetTransaction(assetId = assetId, timestamp = time - 151.days, type = AssetTransactionType.Trade, amount = 3.0, value = 510.0),
                        // Sell 1 @ ~$173
                        AssetTransaction(assetId = assetId, timestamp = time - 151.days, type = AssetTransactionType.Trade, amount = -1.0, value = 172.52),
                        // Sell 1 @ ~$167
                        AssetTransaction(assetId = assetId, timestamp = time - 148.days, type = AssetTransactionType.Trade, amount = -1.0, value = 166.83),
                        // Sell 2 @ ~$168
                        AssetTransaction(assetId = assetId, timestamp = time - 147.days, type = AssetTransactionType.Trade, amount = -2.0, value = 336.86),
                        // Sell 1 @ ~$169
                        AssetTransaction(assetId = assetId, timestamp = time - 146.days, type = AssetTransactionType.Trade, amount = -1.0, value = 168.64),
                        // Sell 5 @ ~$165
                        AssetTransaction(assetId = assetId, timestamp = time - 145.days, type = AssetTransactionType.Trade, amount = -5.0, value = 826.07),
                        // Sell 2 @ ~$166
                        AssetTransaction(assetId = assetId, timestamp = time - 144.days, type = AssetTransactionType.Trade, amount = -2.0, value = 331.92),
                        // Buy 2 @ ~$162
                        AssetTransaction(assetId = assetId, timestamp = time - 142.days, type = AssetTransactionType.Trade, amount = 2.0, value = 324.5),
                        // Buy 5 @ ~$163
                        AssetTransaction(assetId = assetId, timestamp = time - 141.days, type = AssetTransactionType.Trade, amount = 5.0, value = 812.81),
                        // Sell 4 @ ~$163
                        AssetTransaction(assetId = assetId, timestamp = time - 140.days, type = AssetTransactionType.Trade, amount = -4.0, value = 651.44),
                        // Buy 2 @ ~$164
                        AssetTransaction(assetId = assetId, timestamp = time - 140.days, type = AssetTransactionType.Trade, amount = 2.0, value = 328.32),
                        // Buy 5 @ ~$161
                        AssetTransaction(assetId = assetId, timestamp = time - 139.days, type = AssetTransactionType.Trade, amount = 5.0, value = 805.13),
                        // Buy 2 @ ~$156
                        AssetTransaction(assetId = assetId, timestamp = time - 137.days, type = AssetTransactionType.Trade, amount = 2.0, value = 311.88),
                        // Buy 4 @ ~$153
                        AssetTransaction(assetId = assetId, timestamp = time - 136.days, type = AssetTransactionType.Trade, amount = 4.0, value = 611.86),
                        // Buy 4 @ ~$153
                        AssetTransaction(assetId = assetId, timestamp = time - 135.days, type = AssetTransactionType.Trade, amount = 4.0, value = 610.08),
                        // Buy 4 @ ~$152
                        AssetTransaction(assetId = assetId, timestamp = time - 135.days, type = AssetTransactionType.Trade, amount = 4.0, value = 607.57),
                        // Buy 2 @ ~$150
                        AssetTransaction(assetId = assetId, timestamp = time - 134.days, type = AssetTransactionType.Trade, amount = 2.0, value = 299.88),
                        // Buy 2 @ ~$148
                        AssetTransaction(assetId = assetId, timestamp = time - 133.days, type = AssetTransactionType.Trade, amount = 2.0, value = 295.9),
                        // Buy 2 @ ~$139
                        AssetTransaction(assetId = assetId, timestamp = time - 131.days, type = AssetTransactionType.Trade, amount = 2.0, value = 278.71),
                        // Buy 3 @ ~$133
                        AssetTransaction(assetId = assetId, timestamp = time - 127.days, type = AssetTransactionType.Trade, amount = 3.0, value = 399.24),
                        // Buy 5 @ ~$135
                        AssetTransaction(assetId = assetId, timestamp = time - 127.days, type = AssetTransactionType.Trade, amount = 5.0, value = 673.04),
                        // Sell 1 @ ~$133
                        AssetTransaction(assetId = assetId, timestamp = time - 126.days, type = AssetTransactionType.Trade, amount = -1.0, value = 133.25),
                        // Buy 6 @ ~$137
                        AssetTransaction(assetId = assetId, timestamp = time - 124.days, type = AssetTransactionType.Trade, amount = 6.0, value = 823.27),
                        // Buy 1 @ ~$135
                        AssetTransaction(assetId = assetId, timestamp = time - 124.days, type = AssetTransactionType.Trade, amount = 1.0, value = 135.2),
                        // Buy 4 @ ~$137
                        AssetTransaction(assetId = assetId, timestamp = time - 123.days, type = AssetTransactionType.Trade, amount = 4.0, value = 549.43),
                        // Buy 1 @ ~$140
                        AssetTransaction(assetId = assetId, timestamp = time - 119.days, type = AssetTransactionType.Trade, amount = 1.0, value = 140.42),
                        // Buy 4 @ ~$141
                        AssetTransaction(assetId = assetId, timestamp = time - 119.days, type = AssetTransactionType.Trade, amount = 4.0, value = 562.94),
                        // Buy 2 @ ~$140
                        AssetTransaction(assetId = assetId, timestamp = time - 118.days, type = AssetTransactionType.Trade, amount = 2.0, value = 279.02),
                        // Buy 2 @ ~$141
                        AssetTransaction(assetId = assetId, timestamp = time - 116.days, type = AssetTransactionType.Trade, amount = 2.0, value = 282.93),
                        // Buy 3 @ ~$139
                        AssetTransaction(assetId = assetId, timestamp = time - 113.days, type = AssetTransactionType.Trade, amount = 3.0, value = 417.42),
                        // Buy 1 @ ~$137
                        AssetTransaction(assetId = assetId, timestamp = time - 113.days, type = AssetTransactionType.Trade, amount = 1.0, value = 136.55),
                        // Buy 2 @ ~$136
                        AssetTransaction(assetId = assetId, timestamp = time - 112.days, type = AssetTransactionType.Trade, amount = 2.0, value = 271.87),
                        // Buy 5 @ ~$136
                        AssetTransaction(assetId = assetId, timestamp = time - 112.days, type = AssetTransactionType.Trade, amount = 5.0, value = 678.88),
                        // Buy 2 @ ~$136
                        AssetTransaction(assetId = assetId, timestamp = time - 110.days, type = AssetTransactionType.Trade, amount = 2.0, value = 272.84),
                        // Buy 1 @ ~$134
                        AssetTransaction(assetId = assetId, timestamp = time - 109.days, type = AssetTransactionType.Trade, amount = 1.0, value = 133.51),
                        // Buy 3 @ ~$135
                        AssetTransaction(assetId = assetId, timestamp = time - 107.days, type = AssetTransactionType.Trade, amount = 3.0, value = 403.74),
                        // Sell 3 @ ~$131
                        AssetTransaction(assetId = assetId, timestamp = time - 106.days, type = AssetTransactionType.Trade, amount = -3.0, value = 394.07),
                        // Sell 1 @ ~$134
                        AssetTransaction(assetId = assetId, timestamp = time - 106.days, type = AssetTransactionType.Trade, amount = -1.0, value = 133.96),
                        // Buy 2 @ ~$133
                        AssetTransaction(assetId = assetId, timestamp = time - 104.days, type = AssetTransactionType.Trade, amount = 2.0, value = 265.26),
                        // Buy 2 @ ~$131
                        AssetTransaction(assetId = assetId, timestamp = time - 102.days, type = AssetTransactionType.Trade, amount = 2.0, value = 261.05),
                        // Sell 6 @ ~$131
                        AssetTransaction(assetId = assetId, timestamp = time - 99.days, type = AssetTransactionType.Trade, amount = -6.0, value = 785.91),
                        // Buy 2 @ ~$129
                        AssetTransaction(assetId = assetId, timestamp = time - 98.days, type = AssetTransactionType.Trade, amount = 2.0, value = 257.65),
                        // Reconciliation: 329 shares @ ~$129
                        AssetTransaction(assetId = assetId, timestamp = time - 97.days, type = AssetTransactionType.Reconciliation, amount = 329.0, value = 42370.18),
                        // Buy 1 @ ~$127
                        AssetTransaction(assetId = assetId, timestamp = time - 96.days, type = AssetTransactionType.Trade, amount = 1.0, value = 127.13),
                        // Sell 5 @ ~$127
                        AssetTransaction(assetId = assetId, timestamp = time - 95.days, type = AssetTransactionType.Trade, amount = -5.0, value = 636.02),
                        // Buy 2 @ ~$131
                        AssetTransaction(assetId = assetId, timestamp = time - 92.days, type = AssetTransactionType.Trade, amount = 2.0, value = 261.15),
                        // Buy 4 @ ~$130
                        AssetTransaction(assetId = assetId, timestamp = time - 90.days, type = AssetTransactionType.Trade, amount = 4.0, value = 521.46),
                        // Buy 2 @ ~$129
                        AssetTransaction(assetId = assetId, timestamp = time - 89.days, type = AssetTransactionType.Trade, amount = 2.0, value = 257.93),
                        // Buy 2 @ ~$129
                        AssetTransaction(assetId = assetId, timestamp = time - 89.days, type = AssetTransactionType.Trade, amount = 2.0, value = 257.69),
                        // Sell 4 @ ~$129
                        AssetTransaction(assetId = assetId, timestamp = time - 88.days, type = AssetTransactionType.Trade, amount = -4.0, value = 516.09),
                        // Buy 4 @ ~$132
                        AssetTransaction(assetId = assetId, timestamp = time - 84.days, type = AssetTransactionType.Trade, amount = 4.0, value = 528.49),
                        // Buy 5 @ ~$132
                        AssetTransaction(assetId = assetId, timestamp = time - 83.days, type = AssetTransactionType.Trade, amount = 5.0, value = 659.76),
                        // Buy 3 @ ~$133
                        AssetTransaction(assetId = assetId, timestamp = time - 82.days, type = AssetTransactionType.Trade, amount = 3.0, value = 397.63),
                        // Buy 2 @ ~$130
                        AssetTransaction(assetId = assetId, timestamp = time - 82.days, type = AssetTransactionType.Trade, amount = 2.0, value = 260.21),
                        // Buy 2 @ ~$130
                        AssetTransaction(assetId = assetId, timestamp = time - 81.days, type = AssetTransactionType.Trade, amount = 2.0, value = 261.0),
                        // Buy 4 @ ~$135
                        AssetTransaction(assetId = assetId, timestamp = time - 79.days, type = AssetTransactionType.Trade, amount = 4.0, value = 538.62),
                        // Buy 4 @ ~$133
                        AssetTransaction(assetId = assetId, timestamp = time - 79.days, type = AssetTransactionType.Trade, amount = 4.0, value = 531.96),
                        // Buy 2 @ ~$132
                        AssetTransaction(assetId = assetId, timestamp = time - 78.days, type = AssetTransactionType.Trade, amount = 2.0, value = 263.61),
                        // Sell 6 @ ~$132
                        AssetTransaction(assetId = assetId, timestamp = time - 77.days, type = AssetTransactionType.Trade, amount = -6.0, value = 792.53),
                        // Buy 1 @ ~$132
                        AssetTransaction(assetId = assetId, timestamp = time - 76.days, type = AssetTransactionType.Trade, amount = 1.0, value = 132.16),
                        // Sell 3 @ ~$134
                        AssetTransaction(assetId = assetId, timestamp = time - 75.days, type = AssetTransactionType.Trade, amount = -3.0, value = 400.8),
                        // Buy 1 @ ~$135
                        AssetTransaction(assetId = assetId, timestamp = time - 74.days, type = AssetTransactionType.Trade, amount = 1.0, value = 135.02),
                        // Buy 1 @ ~$133
                        AssetTransaction(assetId = assetId, timestamp = time - 70.days, type = AssetTransactionType.Trade, amount = 1.0, value = 133.39),
                        // Buy 2 @ ~$132
                        AssetTransaction(assetId = assetId, timestamp = time - 69.days, type = AssetTransactionType.Trade, amount = 2.0, value = 263.77),
                        // Sell 5 @ ~$132
                        AssetTransaction(assetId = assetId, timestamp = time - 68.days, type = AssetTransactionType.Trade, amount = -5.0, value = 661.55),
                        // Buy 2 @ ~$132
                        AssetTransaction(assetId = assetId, timestamp = time - 65.days, type = AssetTransactionType.Trade, amount = 2.0, value = 264.9),
                        // Buy 4 @ ~$129
                        AssetTransaction(assetId = assetId, timestamp = time - 64.days, type = AssetTransactionType.Trade, amount = 4.0, value = 517.5),
                        // Buy 1 @ ~$130
                        AssetTransaction(assetId = assetId, timestamp = time - 63.days, type = AssetTransactionType.Trade, amount = 1.0, value = 130.43),
                        // Sell 3 @ ~$129
                        AssetTransaction(assetId = assetId, timestamp = time - 62.days, type = AssetTransactionType.Trade, amount = -3.0, value = 385.96),
                        // Buy 1 @ ~$128
                        AssetTransaction(assetId = assetId, timestamp = time - 61.days, type = AssetTransactionType.Trade, amount = 1.0, value = 128.01),
                        // Sell 5 @ ~$128
                        AssetTransaction(assetId = assetId, timestamp = time - 60.days, type = AssetTransactionType.Trade, amount = -5.0, value = 641.61),
                        // Sell 4 @ ~$122
                        AssetTransaction(assetId = assetId, timestamp = time - 57.days, type = AssetTransactionType.Trade, amount = -4.0, value = 488.76),
                        // Buy 2 @ ~$121
                        AssetTransaction(assetId = assetId, timestamp = time - 56.days, type = AssetTransactionType.Trade, amount = 2.0, value = 241.9),
                        // Sell 6 @ ~$120
                        AssetTransaction(assetId = assetId, timestamp = time - 55.days, type = AssetTransactionType.Trade, amount = -6.0, value = 721.3),
                        // Buy 4 @ ~$117
                        AssetTransaction(assetId = assetId, timestamp = time - 53.days, type = AssetTransactionType.Trade, amount = 4.0, value = 468.45),
                        // Sell 5 @ ~$113
                        AssetTransaction(assetId = assetId, timestamp = time - 52.days, type = AssetTransactionType.Trade, amount = -5.0, value = 563.21),
                        // Buy 3 @ ~$109
                        AssetTransaction(assetId = assetId, timestamp = time - 50.days, type = AssetTransactionType.Trade, amount = 3.0, value = 325.73),
                        // Buy 6 @ ~$109
                        AssetTransaction(assetId = assetId, timestamp = time - 49.days, type = AssetTransactionType.Trade, amount = 6.0, value = 652.94),
                        // Buy 5 @ ~$108
                        AssetTransaction(assetId = assetId, timestamp = time - 48.days, type = AssetTransactionType.Trade, amount = 5.0, value = 537.5),
                        // Buy 2 @ ~$94
                        AssetTransaction(assetId = assetId, timestamp = time - 43.days, type = AssetTransactionType.Trade, amount = 2.0, value = 188.85),
                        // Buy 4 @ ~$91
                        AssetTransaction(assetId = assetId, timestamp = time - 41.days, type = AssetTransactionType.Trade, amount = 4.0, value = 364.53),
                        // Buy 4 @ ~$90
                        AssetTransaction(assetId = assetId, timestamp = time - 40.days, type = AssetTransactionType.Trade, amount = 4.0, value = 359.23),
                        // Buy 3 @ ~$88
                        AssetTransaction(assetId = assetId, timestamp = time - 39.days, type = AssetTransactionType.Trade, amount = 3.0, value = 263.35),
                        // Buy 5 @ ~$88
                        AssetTransaction(assetId = assetId, timestamp = time - 39.days, type = AssetTransactionType.Trade, amount = 5.0, value = 440.96),
                        // Buy 1 @ ~$85
                        AssetTransaction(assetId = assetId, timestamp = time - 37.days, type = AssetTransactionType.Trade, amount = 1.0, value = 84.9),
                        // Buy 3 @ ~$84
                        AssetTransaction(assetId = assetId, timestamp = time - 36.days, type = AssetTransactionType.Trade, amount = 3.0, value = 251.73),
                        // Buy 1 @ ~$81
                        AssetTransaction(assetId = assetId, timestamp = time - 35.days, type = AssetTransactionType.Trade, amount = 1.0, value = 80.58),
                        // Sell 1 @ ~$80
                        AssetTransaction(assetId = assetId, timestamp = time - 34.days, type = AssetTransactionType.Trade, amount = -1.0, value = 79.96),
                        // Sell 6 @ ~$78
                        AssetTransaction(assetId = assetId, timestamp = time - 33.days, type = AssetTransactionType.Trade, amount = -6.0, value = 467.65),
                        // Sell 1 @ ~$77
                        AssetTransaction(assetId = assetId, timestamp = time - 32.days, type = AssetTransactionType.Trade, amount = -1.0, value = 77.14),
                        // Sell 3 @ ~$77
                        AssetTransaction(assetId = assetId, timestamp = time - 32.days, type = AssetTransactionType.Trade, amount = -3.0, value = 231.28),
                        // Buy 2 @ ~$74
                        AssetTransaction(assetId = assetId, timestamp = time - 29.days, type = AssetTransactionType.Trade, amount = 2.0, value = 148.33),
                        // Sell 6 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 28.days, type = AssetTransactionType.Trade, amount = -6.0, value = 447.68),
                        // Reconciliation: 361 shares @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 27.days, type = AssetTransactionType.Reconciliation, amount = 361.0, value = 27075.0),
                        // Sell 3 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 25.days, type = AssetTransactionType.Trade, amount = -3.0, value = 225.06),
                        // Sell 2 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 22.days, type = AssetTransactionType.Trade, amount = -2.0, value = 149.94),
                        // Buy 4 @ ~$74
                        AssetTransaction(assetId = assetId, timestamp = time - 22.days, type = AssetTransactionType.Trade, amount = 4.0, value = 296.89),
                        // Buy 2 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 21.days, type = AssetTransactionType.Trade, amount = 2.0, value = 151.51),
                        // Buy 1 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 20.days, type = AssetTransactionType.Trade, amount = 1.0, value = 75.27),
                        // Buy 2 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 20.days, type = AssetTransactionType.Trade, amount = 2.0, value = 151.49),
                        // Buy 3 @ ~$74
                        AssetTransaction(assetId = assetId, timestamp = time - 19.days, type = AssetTransactionType.Trade, amount = 3.0, value = 221.92),
                        // Buy 1 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 18.days, type = AssetTransactionType.Trade, amount = 1.0, value = 75.57),
                        // Buy 5 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 16.days, type = AssetTransactionType.Trade, amount = 5.0, value = 378.77),
                        // Buy 1 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 15.days, type = AssetTransactionType.Trade, amount = 1.0, value = 74.51),
                        // Sell 3 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 14.days, type = AssetTransactionType.Trade, amount = -3.0, value = 227.03),
                        // Buy 4 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 12.days, type = AssetTransactionType.Trade, amount = 4.0, value = 298.84),
                        // Buy 1 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 11.days, type = AssetTransactionType.Trade, amount = 1.0, value = 74.52),
                        // Sell 2 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 11.days, type = AssetTransactionType.Trade, amount = -2.0, value = 151.49),
                        // Buy 2 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 10.days, type = AssetTransactionType.Trade, amount = 2.0, value = 151.94),
                        // Buy 1 @ ~$74
                        AssetTransaction(assetId = assetId, timestamp = time - 10.days, type = AssetTransactionType.Trade, amount = 1.0, value = 74.41),
                        // Sell 1 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 7.days, type = AssetTransactionType.Trade, amount = -1.0, value = 74.63),
                        // Buy 2 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 6.days, type = AssetTransactionType.Trade, amount = 2.0, value = 151.48),
                        // Sell 3 @ ~$74
                        AssetTransaction(assetId = assetId, timestamp = time - 5.days, type = AssetTransactionType.Trade, amount = -3.0, value = 221.72),
                        // Buy 3 @ ~$75
                        AssetTransaction(assetId = assetId, timestamp = time - 4.days, type = AssetTransactionType.Trade, amount = 3.0, value = 224.08),
                        // Buy 3 @ ~$76
                        AssetTransaction(assetId = assetId, timestamp = time - 1.days, type = AssetTransactionType.Trade, amount = 3.0, value = 226.97),
                    )
                ) {
                    this[Transaction.assetId] = it.assetId
                    this[Transaction.timestamp] = it.timestamp.toEpochMilliseconds()
                    this[Transaction.type] = it.type
                    this[Transaction.amount] = it.amount
                    this[Transaction.value] = it.value
                }
            }
            logger.info { "Finished seed data" }
            jobRequestScheduler.enqueue(RecomputeUserAggregationRequest(Uuid.parse("6c942179-c993-4b25-86dd-6346fb0e3005")))
        }

        companion object {
        }
    }
}
