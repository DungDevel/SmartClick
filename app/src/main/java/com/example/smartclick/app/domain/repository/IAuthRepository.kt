package com.example.smartclick.app.domain.repository

import com.example.smartclick.app.domain.model.UserAccount
import kotlinx.coroutines.flow.Flow

interface IAuthRepository {
    val currentUser: Flow<UserAccount?>
    suspend fun login(userId: String)
    suspend fun logout() // Tương đương clear guest nếu dùng
}
