package com.example.di

import androidx.hilt.work.HiltWorkerFactory
import com.example.data.*
import com.example.processor.MessageProcessor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint @InstallIn(SingletonComponent::class)
interface AppServices {
    fun settings(): SettingsDataStore
    fun processor(): MessageProcessor
    fun dao(): SmsDao
    fun vault(): LocalVault
    fun workerFactory(): HiltWorkerFactory
}
