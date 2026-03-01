package com.jacksonrakena.mixer.controller.values

import kotlinx.serialization.Serializable

@Serializable
data class PortfolioAggregationPoint(
    val date: String,
    val totalValue: Double,
    val displayCurrency: String,
    val assetCount: Int,
    val assetBreakdown: List<PortfolioAssetValue>,
)
