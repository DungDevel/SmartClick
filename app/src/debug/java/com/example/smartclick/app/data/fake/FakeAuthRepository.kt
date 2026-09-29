package com.example.smartclick.app.data.fake

import com.example.smartclick.app.domain.model.UserAccount
import com.example.smartclick.app.domain.repository.IAuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAuthRepository @Inject constructor() : IAuthRepository {
    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    override val currentUser: Flow<UserAccount?> = _currentUser

    override suspend fun login(userId: String) {
        _currentUser.value = UserAccount(id = userId, name = "Guest User")
    }

    override suspend fun logout() {
        _currentUser.value = null
    }
}
