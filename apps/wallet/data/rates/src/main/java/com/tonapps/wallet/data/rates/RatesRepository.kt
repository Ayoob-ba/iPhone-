package com.tonapps.wallet.data.rates

import android.content.Context
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.entity.RateDiffEntity
import com.tonapps.wallet.data.rates.entity.RateEntity
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.data.rates.source.BlobDataSource
import io.tonapi.models.TokenRates
import java.math.BigDecimal

class RatesRepository(
    context: Context,
    private val api: API
) {

    private val localDataSource = BlobDataSource(context)

    fun cache(currency: WalletCurrency, tokens: List<String>): RatesEntity {
        return localDataSource.get(currency).filter(tokens)
    }

    suspend fun load(currency: WalletCurrency, token: String) {
        load(currency, mutableListOf(token))
    }

    private suspend fun load(currency: WalletCurrency, tokens: MutableList<String>) {
        if (!tokens.contains(TokenEntity.TON.address)) {
            tokens.add(TokenEntity.TON.address)
        }
        if (!tokens.contains(TokenEntity.USDT.address)) {
            tokens.add(TokenEntity.USDT.address)
        }
        val rates = api.getRates(currency.code, tokens) ?: return
        insertRates(currency, rates)
    }

    fun insertRates(currency: WalletCurrency, rates: Map<String, TokenRates>) {
        if (rates.isEmpty()) {
            return
        }
        val entities = mutableListOf<RateEntity>()
        for (rate in rates) {
            val value = rate.value
            val bigDecimal = value.prices?.get(currency.code) ?: BigDecimal.ZERO

            entities.add(RateEntity(
                tokenCode = rate.key,
                currency = currency,
                value = Coins.of(bigDecimal, currency.decimals),
                diff = RateDiffEntity(currency, value),
            ))
        }
        localDataSource.add(currency, entities)
    }

    fun getRates(currency: WalletCurrency, token: String): RatesEntity {
        return getRates(currency, listOf(token))
    }

    fun getRates(currency: WalletCurrency, tokens: List<String>): RatesEntity {
        return localDataSource.get(currency).filter(tokens)
    }
}