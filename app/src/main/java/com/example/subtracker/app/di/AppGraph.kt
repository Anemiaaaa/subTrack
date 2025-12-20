package com.example.subtracker.app.di

import android.content.Context
import com.example.subtracker.data.auth.FirebaseAuthRepository
import com.example.subtracker.data.local.AppDatabase
import com.example.subtracker.data.remote.FirestoreSyncDataSource
import com.example.subtracker.data.repository.PaymentRepositoryImpl
import com.example.subtracker.data.repository.SubscriptionRepositoryImpl
import com.example.subtracker.domain.auth.AuthRepository
import com.example.subtracker.domain.auth.usecase.CreateFamilyUseCase
import com.example.subtracker.domain.auth.usecase.JoinFamilyUseCase
import com.example.subtracker.domain.auth.usecase.LoginGuestUseCase
import com.example.subtracker.domain.auth.usecase.LogoutUseCase
import com.example.subtracker.domain.auth.usecase.RecoverFamilyCodeUseCase
import com.example.subtracker.domain.repository.PaymentRepository
import com.example.subtracker.domain.repository.SubscriptionRepository
import com.example.subtracker.domain.usecase.CreateSubscriptionUseCase
import com.example.subtracker.domain.usecase.DeleteSubscriptionUseCase
import com.example.subtracker.domain.usecase.ObservePaymentsUseCase
import com.example.subtracker.domain.usecase.ObserveSubscriptionsUseCase
import com.example.subtracker.domain.usecase.PaySubscriptionUseCase
import com.example.subtracker.domain.usecase.UpdateSubscriptionUseCase
import com.example.subtracker.presentation.auth.LoginViewModelFactory
import com.example.subtracker.presentation.auth.RegisterViewModelFactory
import com.example.subtracker.presentation.main.MainFrameViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object AppGraph {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }
    
    fun getAppContext(): Context {
        return if (::appContext.isInitialized) appContext else throw IllegalStateException("AppGraph not initialized")
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val roomDb by lazy { AppDatabase.get(appContext) }
    private val syncDs by lazy { FirestoreSyncDataSource(firestore) }

    // --- Auth ---
    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(
            context = appContext,
            auth = auth,
            db = firestore
        )
    }

    val loginGuestUseCase by lazy { LoginGuestUseCase(authRepository) }
    val joinFamilyUseCase by lazy { JoinFamilyUseCase(authRepository) }
    val createFamilyUseCase by lazy { CreateFamilyUseCase(authRepository) }
    val logoutUseCase by lazy { LogoutUseCase(authRepository) }
    val recoverFamilyCodeUseCase by lazy { RecoverFamilyCodeUseCase(authRepository) }

    // --- Subscriptions ---
    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepositoryImpl(
            context = appContext,
            db = roomDb,
            remote = syncDs
        )
    }
    val observeSubscriptionsUseCase by lazy { ObserveSubscriptionsUseCase(subscriptionRepository) }
    val createSubscriptionUseCase by lazy { CreateSubscriptionUseCase(subscriptionRepository) }
    val paySubscriptionUseCase by lazy { PaySubscriptionUseCase(subscriptionRepository) }
    val deleteSubscriptionUseCase by lazy { DeleteSubscriptionUseCase(subscriptionRepository) }
    val updateSubscriptionUseCase by lazy { UpdateSubscriptionUseCase(subscriptionRepository) }

    // --- Payments ---
    val paymentRepository: PaymentRepository by lazy { PaymentRepositoryImpl(roomDb) }
    val observePaymentsUseCase by lazy { ObservePaymentsUseCase(paymentRepository) }

    // --- Presentation factories ---
    fun mainFrameViewModelFactory(): MainFrameViewModelFactory {
        return MainFrameViewModelFactory(
            observeSubscriptionsUseCase = observeSubscriptionsUseCase,
            paySubscriptionUseCase = paySubscriptionUseCase,
            deleteSubscriptionUseCase = deleteSubscriptionUseCase,
            updateSubscriptionUseCase = updateSubscriptionUseCase,
            subscriptionRepository = subscriptionRepository,
            firestore = firestore
        )
    }

    fun loginViewModelFactory(): LoginViewModelFactory {
        return LoginViewModelFactory(
            joinFamilyUseCase = joinFamilyUseCase,
            loginGuestUseCase = loginGuestUseCase,
            recoverFamilyCodeUseCase = recoverFamilyCodeUseCase
        )
    }

    fun registerViewModelFactory(): RegisterViewModelFactory {
        return RegisterViewModelFactory(
            createFamilyUseCase = createFamilyUseCase
        )
    }
}
