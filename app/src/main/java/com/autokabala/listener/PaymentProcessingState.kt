package com.autokabala.listener

data class PaymentProcessingState(
    val payment: PaymentEntity,
    val matchResult: MatchResult = MatchResult.NoMatch,
    val isProcessing: Boolean = false,
    val error: String? = null
)
