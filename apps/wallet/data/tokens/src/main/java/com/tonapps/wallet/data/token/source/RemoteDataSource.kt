package com.tonapps.wallet.data.token.source

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

internal class RemoteDataSource(
    private val api: API
) {


    suspend fun getJetton(accountId: String, testnet: Boolean) = api.getJetton(accountId, testnet)

    suspend fun load(
        currency: WalletCurrency,
        accountId: String,
        testnet: Boolean
    ): List<BalanceEntity>? = withContext(Dispatchers.IO) {
        val tonBalanceDeferred = async { api.getTonBalance(accountId, testnet) }
        val jettonBalancesDeferred = async { api.getJettonsBalances(accountId, testnet, currency.code) }
        val tonBalance = tonBalanceDeferred.await() ?: return@withContext null
        val jettons = jettonBalancesDeferred.await()?.toMutableList() ?: mutableListOf()

        val usdtIndex = jettons.indexOfFirst {
            it.token.address == TokenEntity.USDT.address
        }

        val entities = mutableListOf<BalanceEntity>()
        entities.add(tonBalance)
        if (usdtIndex == -1 && !testnet) {
            entities.add(BalanceEntity(
                token = TokenEntity.USDT,
                value = Coins.ZERO,
                walletAddress = accountId,
                initializedAccount = tonBalance.initializedAccount
            ))
        } else if (usdtIndex >= 0) {
            jettons[usdtIndex] = jettons[usdtIndex].copy(
                token = TokenEntity.USDT
            )
        }

        entities.addAll(jettons)
        entities.toList()
    }

}