package com.jacksonrakena.mixer.controller.values

import kotlinx.serialization.Serializable

@Serializable
data class PortfolioAssetValue(
    val assetId: String,
    val assetName: String,
    val nativeCurrency: String,
    val value: Double,
)
