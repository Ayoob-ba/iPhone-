package com.tonapps.tonkeeper.ui.screen.backup.main

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.BalanceType
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take

class BackupViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val backupRepository: BackupRepository,
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
): BaseWalletVM(app) {

    val uiItemsFlow = combine(
        backupRepository.stream,
        accountRepository.selectedWalletFlow.take(1)
    ) { backups, wallet ->
        Pair(backups.filter { it.walletId == wallet.id }, wallet)
    }.map { (backups, wallet) ->
        val backupsCount = backups.size
        val balanceFiat = if (backupsCount > 0) Coins.ZERO else getTotalBalanceFiat(wallet)
        val balanceType = getBalanceType(balanceFiat)

        val items = mutableListOf<Item>()
        if (balanceType != BalanceType.Zero) {
            val format = CurrencyFormatter.formatFiat(settingsRepository.currency.code, balanceFiat)
            items.add(Item.Alert(format, balanceType == BalanceType.Huge))
            items.add(Item.Space)
        }
        items.add(Item.Header)
        items.add(Item.Space)

        for ((index, backup) in backups.withIndex()) {
            val position = ListCell.getPosition(backupsCount, index)
            items.add(Item.Backup(position, backup, settingsRepository.getLocale()))
        }
        if (backupsCount > 0) {
            items.add(Item.Space)
            items.add(Item.RecoveryPhrase)
        } else if (balanceType == BalanceType.Zero) {
            items.add(Item.ManualBackup)
        } else {
            items.add(Item.ManualAccentBackup)
        }
        items.toList()
    }.flowOn(Dispatchers.IO)

    fun getRecoveryPhrase(
        context: Context
    ) = accountRepository.selectedWalletFlow.take(1).combine(passcodeManager.confirmationFlow(context, context.getString(Localization.app_name))) { wallet, _ ->
        accountRepository.getMnemonic(wallet.id)
    }.take(1)

    private suspend fun getBalanceType(
        balanceFiat: Coins,
    ): Int {
        val rates = ratesRepository.getTONRates(settingsRepository.currency)
        val balanceTON = rates.convertFromFiat(TokenEntity.TON.address, balanceFiat)
        return BalanceType.getBalanceType(balanceTON)
    }

    private suspend fun getTotalBalanceFiat(
        wallet: WalletEntity
    ): Coins {
        val assets = getAssets(wallet)
        return if (wallet.testnet) {
            assets.first().fiat
        } else {
            assets.map { it.fiat }.sumOf { it }
        }
    }

    private suspend fun getAssets(
        wallet: WalletEntity
    ): List<AssetsEntity> {
        val currency = settingsRepository.currency
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet) ?: emptyList()
        val staking = stakingRepository.get(wallet.accountId, wallet.testnet)
        val staked = StakedEntity.create(staking, tokens, currency, ratesRepository)
        val liquid = staked.find { it.isTonstakers }?.liquidToken
        val filteredTokens = if (liquid == null) tokens else tokens.filter { !liquid.token.address.contains(it.address)  }
        return (filteredTokens.map { AssetsEntity.Token(it) } + staked.map {
            AssetsEntity.Staked(it)
        }).sortedBy { it.fiat }.reversed()
    }
}