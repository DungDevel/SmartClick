package com.example.smartclick.app.domain.usecase

import com.example.smartclick.app.domain.model.AppLaunchConfig
import com.example.smartclick.app.domain.model.SubscriptionPlan
import com.example.smartclick.app.domain.policy.FeatureGate
import com.example.smartclick.app.domain.policy.PremiumFeature
import com.example.smartclick.app.domain.repository.ISubscriptionRepository
import com.example.smartclick.app.domain.connection.IActiveTvSession
import com.example.smartclick.app.domain.connection.TvConnectionState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class LaunchAppOnTvUseCase @Inject constructor(
    private val activeTvSession: IActiveTvSession,
    private val featureGate: FeatureGate,
    private val subscriptionRepository: ISubscriptionRepository
) {
    suspend operator fun invoke(appConfig: AppLaunchConfig): Result<Unit> {
        val idToLaunch = appConfig.packageName ?: appConfig.appId
        return this.invoke(idToLaunch) 
    }
    
    suspend operator fun invoke(appId: String): Result<Unit> {
        val controller = activeTvSession.currentController.value ?: return Result.failure(Exception("No TV connected"))

        return if (controller.connectionState.value is TvConnectionState.Connected) {
            controller.launchApp(appId)
        } else {
            Result.failure(Exception("TV is not connected"))
        }
    }
}


