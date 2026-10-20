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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface BillingState {
    data object Connecting : BillingState
    data object Ready : BillingState
    data class Products(val items: List<ProductDetails>) : BillingState
    data class Purchased(val token: String, val productId: String) : BillingState
    data class Error(val message: String) : BillingState
}

class BillingManager(context: Context) : PurchasesUpdatedListener {
    private val _state = MutableStateFlow<BillingState>(BillingState.Connecting)
    val state: StateFlow<BillingState> = _state
    private val productIds = listOf("jawlinefit_premium_monthly", "jawlinefit_premium_yearly")
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init { connect() }

    private fun connect() = client.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) queryProducts()
            else _state.value = BillingState.Error(result.debugMessage)
        }
        override fun onBillingServiceDisconnected() { _state.value = BillingState.Connecting }
    })

    private fun queryProducts() {
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.SUBS).build()
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, details ->
            _state.value = if (result.responseCode == BillingClient.BillingResponseCode.OK) BillingState.Products(details)
            else BillingState.Error(result.debugMessage)
        }
    }

    fun purchase(activity: Activity, details: ProductDetails) {
        val offer = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offer == null) { _state.value = BillingState.Error("This subscription is currently unavailable."); return }
        val parameter = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer).build()
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(parameter)).build())
    }

    fun restore() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) _state.value = BillingState.Error(result.debugMessage)
            else handle(purchases)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(purchases.orEmpty())
        else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) _state.value = BillingState.Error(result.debugMessage)
    }

    private fun handle(purchases: List<Purchase>) {
        val purchase = purchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchase == null) { _state.value = BillingState.Error("No active subscription was found."); return }
        if (!purchase.isAcknowledged) client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { }
        _state.value = BillingState.Purchased(purchase.purchaseToken, purchase.products.first())
    }
}
