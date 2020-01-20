package org.tokend.template.data.repository

import io.reactivex.Observable
import io.reactivex.Single
import org.tokend.sdk.api.general.model.SystemInfo
import org.tokend.template.data.repository.base.SingleItemRepository
import org.tokend.template.di.providers.ApiProvider
import org.tokend.rx.extensions.toSingle
import org.tokend.wallet.NetworkParams

class SystemInfoRepository(
        private val apiProvider: ApiProvider
) : SingleItemRepository<SystemInfo>() {
    override fun getItem(): Observable<SystemInfo> {
        return apiProvider.getApi().general.getSystemInfo().toSingle().toObservable()
    }

    fun getNetworkParams(): Single<NetworkParams> {
        return updateIfNotFreshDeferred()
                .toSingle {
                    item?.toNetworkParams()
                            ?: throw IllegalStateException("Missing network passphrase")
                }
    }
}