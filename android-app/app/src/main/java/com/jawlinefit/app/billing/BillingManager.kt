package com.jawlinefit.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.jawlinefit.app.data.remote.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface BillingState {
    data object Connecting : BillingState
    data object Ready : BillingState
    data class Products(val items: List<PlayPlan>) : BillingState
    data class PurchaseReady(val token: String, val productId: String, val acknowledged: Boolean) : BillingState
    data object Pending : BillingState
    data object Cancelled : BillingState
    data object Owned : BillingState
    data object Unavailable : BillingState
    data object NetworkError : BillingState
    data class Error(val message: String) : BillingState
}

data class PlayPlan(
    val plan: Plan,
    val details: ProductDetails,
    val offerToken: String,
    val formattedPrice: String,
    val billingPeriod: String
)

class BillingManager(context: Context) : PurchasesUpdatedListener {
    private val _state = MutableStateFlow<BillingState>(BillingState.Connecting)
    val state: StateFlow<BillingState> = _state
    private var plans: List<Plan> = emptyList()
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init { connect() }

    private fun connect() {
        if (client.isReady) {
            queryProducts()
            restore(auto = true)
            return
        }
        _state.value = BillingState.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = BillingState.Ready
                    queryProducts()
                    restore(auto = true)
                } else publishError(result)
            }

            override fun onBillingServiceDisconnected() {
                _state.value = BillingState.NetworkError
            }
        })
    }

    fun updatePlans(value: List<Plan>) {
        plans = value
        if (client.isReady) queryProducts() else connect()
    }

    private fun queryProducts() {
        if (!client.isReady || plans.isEmpty()) return
        val products = plans.distinctBy { it.productId }.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                publishError(result)
                return@queryProductDetailsAsync
            }
            val details = queryResult.productDetailsList
            val playPlans = plans.mapNotNull { plan ->
                val detail = details.firstOrNull { it.productId == plan.productId } ?: return@mapNotNull null
                val offer = detail.subscriptionOfferDetails
                    ?.filter { it.basePlanId == plan.basePlanId }
                    ?.firstOrNull { candidate ->
                        candidate.pricingPhases.pricingPhaseList.lastOrNull()?.billingPeriod
                            ?.let(::billingPeriodDays) == plan.durationDays
                    } ?: return@mapNotNull null
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                PlayPlan(plan, detail, offer.offerToken, phase.formattedPrice, phase.billingPeriod)
            }
            _state.value = if (playPlans.isEmpty()) BillingState.Unavailable else BillingState.Products(playPlans)
        }
    }

    fun purchase(activity: Activity, playPlan: PlayPlan) {
        if (!client.isReady) {
            connect()
            return
        }
        val parameter = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(playPlan.details)
            .setOfferToken(playPlan.offerToken)
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(parameter)).build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) publishError(result)
    }

    fun restore() = restore(auto = false)

    private fun restore(auto: Boolean) {
        if (!client.isReady) {
            connect()
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                if (!auto) publishError(result)
            } else if (purchases.isNotEmpty()) {
                handle(purchases)
            } else if (!auto && _state.value !is BillingState.Products) {
                _state.value = BillingState.Unavailable
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> _state.value = BillingState.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.value = BillingState.Owned
                restore(auto = false)
            }
            else -> publishError(result)
        }
    }

    private fun handle(purchases: List<Purchase>) {
        val purchased = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased != null) {
            val productId = purchased.products.firstOrNull()
            _state.value = if (productId == null) BillingState.Unavailable else BillingState.PurchaseReady(
                purchased.purchaseToken,
                productId,
                purchased.isAcknowledged
            )
            return
        }
        _state.value = if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
            BillingState.Pending
        } else BillingState.Unavailable
    }

    fun acknowledgeAfterAuthoritativeConfirmation(token: String, alreadyAcknowledged: Boolean, onComplete: (Boolean) -> Unit) {
        if (alreadyAcknowledged) {
            _state.value = BillingState.Owned
            onComplete(true)
            return
        }
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
            val accepted = result.responseCode == BillingClient.BillingResponseCode.OK ||
                result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
            _state.value = if (accepted) BillingState.Owned else BillingState.Error(
                "Premium is confirmed, but Google Play acknowledgement will be retried when purchases are restored."
            )
            onComplete(accepted)
        }
    }

    fun close() = client.endConnection()

    private fun publishError(result: BillingResult) {
        _state.value = when (result.responseCode) {
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR -> BillingState.NetworkError
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> BillingState.Unavailable
            else -> BillingState.Error("Google Play could not complete the request.")
        }
    }
}

internal fun billingPeriodDays(period: String): Int? {
    val match = Regex("^P(\\d+)([DWMY])$").matchEntire(period) ?: return null
    val amount = match.groupValues[1].toIntOrNull() ?: return null
    return when (match.groupValues[2]) {
        "D" -> amount
        "W" -> amount * 7
        "M" -> amount * 30
        "Y" -> amount * 365
        else -> null
    }
}
