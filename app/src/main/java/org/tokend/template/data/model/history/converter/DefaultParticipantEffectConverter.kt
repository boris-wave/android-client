package org.tokend.template.data.model.history.converter

import android.util.Log
import org.tokend.sdk.api.generated.resources.*
import org.tokend.template.data.model.history.BalanceChange
import org.tokend.template.data.model.history.BalanceChangeAction
import org.tokend.template.data.model.history.SimpleFeeRecord
import org.tokend.template.data.model.history.details.BalanceChangeDetails

class DefaultParticipantEffectConverter(
        private val contextBalanceId: String
) : ParticipantEffectConverter {
    override fun toBalanceChanges(participantEffects: Collection<ParticipantEffectResource>)
            : Collection<BalanceChange> {
        val result = ArrayList<BalanceChange>(participantEffects.size)

        participantEffects.forEach { participantEffect ->
            val operation = participantEffect.operation

            if (operation == null) {
                logError("No related operation for participant effect ${participantEffect.id}")
                return@forEach
            }

            if (!operation.hasAttributes()) {
                logError("Operation ${operation.id} has no attributes")
                return@forEach
            }

            val date = operation.appliedAt

            val effect = participantEffect.effect

            if (effect == null) {
                logError("No related effect for participant effect ${participantEffect.id}")
                return@forEach
            }

            val action: BalanceChangeAction = when (effect) {
                is EffectLockedResource ->
                    BalanceChangeAction.LOCKED
                is EffectChargedFromLockedResource ->
                    BalanceChangeAction.CHARGED_FROM_LOCKED
                is EffectUnlockedResource ->
                    BalanceChangeAction.UNLOCKED
                is EffectChargedResource ->
                    BalanceChangeAction.CHARGED
                is EffectWithdrawnResource ->
                    BalanceChangeAction.WITHDRAWN
                is EffectMatchedResource ->
                    BalanceChangeAction.MATCHED
                is EffectIssuedResource ->
                    BalanceChangeAction.ISSUED
                is EffectFundedResource ->
                    BalanceChangeAction.FUNDED
                else -> {
                    logError("Cannot obtain action from effect ${effect.id} $effect")
                    return@forEach
                }
            }

            val relatedAssetCode = participantEffect.asset?.id

            val (amount, fee, assetCode) = when (effect) {
                is EffectBalanceChangeResource ->
                    Triple(effect.amount, effect.fee, relatedAssetCode)
                is EffectMatchedResource -> {
                    when (contextBalanceId) {
                        effect.charged.balanceAddress ->
                            effect.charged.let {
                                Triple(it.amount, it.fee, it.assetCode)
                            }
                        effect.funded.balanceAddress ->
                            effect.funded.let {
                                Triple(it.amount, it.fee, it.assetCode)
                            }
                        else -> {
                            logError("Cannot choose 'funded' or 'charged' " +
                                    "for balance $contextBalanceId and effect ${effect.id}")
                            return@forEach
                        }
                    }
                }
                else -> {
                    logError("Cannot obtain amount and fee from effect ${effect.id} $effect")
                    return@forEach
                }
            }

            if (assetCode == null) {
                logError("Failed to specify asset of participant effect ${participantEffect.id} " +
                        "and balance $contextBalanceId")
                return@forEach
            }

            val operationDetails = operation.details

            if (operationDetails == null) {
                logError("No related operation details for operation ${operation.id}")
                return@forEach
            }

            if (!operationDetails.hasAttributes()) {
                logError("Operation details ${operationDetails.id} has no attributes")
                return@forEach
            }

            val details = getBalanceChangeDetails(effect, operationDetails)

            result.add(
                    BalanceChange(
                            id = effect.id,
                            action = action,
                            amount = amount,
                            fee = SimpleFeeRecord(fee),
                            assetCode = assetCode,
                            balanceId = contextBalanceId,
                            date = date,
                            details = details
                    )
            )
        }

        return result
    }

    private fun getBalanceChangeDetails(effect: EffectResource,
                                        operationDetails: OperationDetailsResource)
            : BalanceChangeDetails {
        return try {
            when (operationDetails) {
                is OpPaymentDetailsResource ->
                    BalanceChangeDetails.Payment(operationDetails)
                is OpCreateIssuanceRequestDetailsResource ->
                    BalanceChangeDetails.Issuance(operationDetails)
                is OpCreateWithdrawRequestDetailsResource ->
                    BalanceChangeDetails.Withdrawal(operationDetails)
                is OpManageOfferDetailsResource ->
                    if (effect is EffectMatchedResource)
                        BalanceChangeDetails.MatchedOffer(operationDetails, effect)
                    else
                        BalanceChangeDetails.Offer(operationDetails)
                is OpCheckSaleStateDetailsResource ->
                    if (effect is EffectMatchedResource)
                        BalanceChangeDetails.Investment(effect)
                    else
                        BalanceChangeDetails.Unknown
                is OpCreateAMLAlertRequestDetailsResource ->
                    BalanceChangeDetails.AmlAlert(operationDetails)
                is OpPayoutDetailsResource ->
                    BalanceChangeDetails.Payout(operationDetails)
                else ->
                    BalanceChangeDetails.Unknown
            }
        } catch (e: Exception) {
            logError("Unable to parse operation details ${operationDetails.id}: "
                    + e.localizedMessage)
            BalanceChangeDetails.Unknown
        }
    }

    private fun logError(error: String) {
        Log.e(LOG_TAG, error)
    }

    companion object {
        private const val LOG_TAG = "DefaultEffectConverter"
    }
}