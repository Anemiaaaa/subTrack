package com.example.subtracker.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.subtracker.app.di.AppGraph
import com.example.subtracker.data.local.AppDatabase
import com.example.subtracker.data.mapper.toUser
import com.example.subtracker.data.mapper.toUserEntity
import com.example.subtracker.domain.model.Subscription
import com.example.subtracker.domain.model.User
import com.example.subtracker.domain.repository.SubscriptionRepository
import com.example.subtracker.domain.usecase.DeleteSubscriptionUseCase
import com.example.subtracker.domain.usecase.ObserveSubscriptionsUseCase
import com.example.subtracker.domain.usecase.PaySubscriptionUseCase
import com.example.subtracker.domain.usecase.UpdateSubscriptionUseCase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainFrameViewModel(
    private val observeSubscriptionsUseCase: ObserveSubscriptionsUseCase,
    private val paySubscriptionUseCase: PaySubscriptionUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
    private val updateSubscriptionUseCase: UpdateSubscriptionUseCase,
    private val subscriptionRepository: SubscriptionRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _state = MutableStateFlow(MainFrameUiState())
    val state: StateFlow<MainFrameUiState> = _state

    private val _events = MutableSharedFlow<MainFrameEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MainFrameEvent> = _events.asSharedFlow()

    private var raw: List<Subscription> = emptyList()

    private var familyCode: String = ""
    private var sessionUserDocId: String = ""
    private var sessionUsername: String = ""

    fun start(familyCode: String, userDocId: String, username: String, role: String) {
        this.familyCode = familyCode
        this.sessionUserDocId = userDocId
        this.sessionUsername = username

        _state.value = _state.value.copy(role = role)

        subscriptionRepository.startSync(familyCode)

        viewModelScope.launch {
            observeSubscriptionsUseCase(familyCode, userDocId, role).collectLatest { list ->
                raw = list
                recompute()
            }
        }

        // Для UI (фильтр по участникам/роль админа)
        loadFamilyMembersAndRole()
    }

    fun toggleSortPrice() {
        val cur = _state.value
        val next =
            if (cur.sortMode == MainFrameUiState.SortMode.PRICE) cur.copy(sortAsc = !cur.sortAsc)
            else cur.copy(sortMode = MainFrameUiState.SortMode.PRICE, sortAsc = true)

        _state.value = next
        recompute()
    }

    fun toggleSortDate() {
        val cur = _state.value
        val next =
            if (cur.sortMode == MainFrameUiState.SortMode.DATE) cur.copy(sortAsc = !cur.sortAsc)
            else cur.copy(sortMode = MainFrameUiState.SortMode.DATE, sortAsc = true)

        _state.value = next
        recompute()
    }

    fun setUserFilter(username: String?) {
        _state.value = _state.value.copy(
            filterByUserEnabled = username != null,
            filterUsername = username
        )
        recompute()
    }

    fun refreshFamilyMembers() {
        loadFamilyMembersAndRole()
    }

    fun requestFamilyInfo() {
        val fc = familyCode
        if (fc.isBlank()) return

        // Все операции выполняем в фоновом потоке
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Загружаем информацию о семье из Firestore
                val familyDoc = com.google.android.gms.tasks.Tasks.await(
                    firestore.collection("families").document(fc).get()
                )
                val familyName = familyDoc.getString("familyName") ?: "—"

                // Загружаем пользователей из Firestore
                val snapshot = com.google.android.gms.tasks.Tasks.await(
                    firestore.collection("users")
                        .whereEqualTo("familyCode", fc)
                        .get()
                )

                // Синхронизируем пользователей в Room
                val db = AppDatabase.get(AppGraph.getAppContext())
                val userEntities = snapshot.documents.mapNotNull { doc ->
                    doc.toUserEntity()
                }
                db.users().upsertAll(userEntities)

                // Загружаем пользователей из Room (с локальными путями к аватарам)
                val roomUserEntities = db.users().getFamilyUsers(fc)
                android.util.Log.d("MainFrameViewModel", "Loaded ${roomUserEntities.size} users from Room")
                val members = roomUserEntities.map { entity ->
                    android.util.Log.d("MainFrameViewModel", "User: ${entity.username}, avatarUrl: ${entity.avatarUrl}")
                    User(
                        id = entity.id,
                        username = entity.username,
                        familyCode = entity.familyCode,
                        familyName = entity.familyName,
                        role = entity.role,
                        avatarUrl = entity.avatarUrl // Локальный путь из Room
                    )
                }

                // Обновим роль (на всякий случай)
                val me = members.find { it.id == sessionUserDocId }
                    ?: members.find { it.username == sessionUsername }
                if (me != null && me.role.isNotBlank()) {
                    _state.value = _state.value.copy(role = me.role)
                }

                _events.tryEmit(
                    MainFrameEvent.ShowFamilyInfo(
                        familyName = familyName,
                        familyCode = fc,
                        members = members
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("MainFrameViewModel", "Ошибка загрузки информации о семье", e)
                _events.tryEmit(MainFrameEvent.Toast("Не удалось загрузить информацию о семье: ${e.message}"))
            }
        }
    }

    private fun loadFamilyMembersAndRole() {
        val fc = familyCode
        if (fc.isBlank()) return

        firestore.collection("users")
            .whereEqualTo("familyCode", fc)
            .get()
            .addOnSuccessListener { snapshot ->
                val members = snapshot.documents.mapNotNull { doc ->
                    doc.toUser()
                }

                // Синхронизируем пользователей в Room
                viewModelScope.launch {
                    try {
                        val db = AppDatabase.get(AppGraph.getAppContext())
                        val userEntities = snapshot.documents.mapNotNull { doc ->
                            doc.toUserEntity()
                        }
                        db.users().upsertAll(userEntities)
                    } catch (e: Exception) {
                        android.util.Log.e("MainFrameViewModel", "Ошибка синхронизации пользователей в Room", e)
                    }
                }

                val me = members.find { it.id == sessionUserDocId }
                    ?: members.find { it.username == sessionUsername }
                val resolvedRole = me?.role?.ifBlank { _state.value.role } ?: _state.value.role

                _state.value = _state.value.copy(
                    role = resolvedRole,
                    familyMembers = members
                )
            }
            .addOnFailureListener {
                // Не считаем критичным — просто оставим пустой список, фильтр будет недоступен.
                _state.value = _state.value.copy(familyMembers = emptyList())
            }
    }

    private fun recompute() {
        val cur = _state.value
        var list = raw

        if (cur.filterByUserEnabled && cur.filterUsername != null) {
            list = list.filter { it.ownerUsername == cur.filterUsername }
        }

        list = when (cur.sortMode) {
            MainFrameUiState.SortMode.PRICE ->
                if (cur.sortAsc) list.sortedBy { it.price } else list.sortedByDescending { it.price }

            MainFrameUiState.SortMode.DATE ->
                if (cur.sortAsc) list.sortedBy { it.nextPaymentDate } else list.sortedByDescending { it.nextPaymentDate }

            MainFrameUiState.SortMode.NONE -> list
        }

        _state.value = cur.copy(items = list)
    }

    // ===== Offline-first actions =====

    fun pay(sub: Subscription) {
        viewModelScope.launch { paySubscriptionUseCase(sessionUserDocId, sessionUsername, sub) }
    }

    fun delete(sub: Subscription) {
        viewModelScope.launch { deleteSubscriptionUseCase(sub) }
    }

    fun update(
        sub: Subscription,
        newName: String,
        newPrice: Double,
        newPeriodicity: String,
        newIconResName: String,
        newNextPaymentDate: Long
    ) {
        viewModelScope.launch {
            updateSubscriptionUseCase(sub, newName, newPrice, newPeriodicity, newIconResName, newNextPaymentDate)
        }
    }
}

sealed interface MainFrameEvent {
    data class ShowFamilyInfo(
        val familyName: String,
        val familyCode: String,
        val members: List<User>
    ) : MainFrameEvent

    data class Toast(val message: String) : MainFrameEvent
}
