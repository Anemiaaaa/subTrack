package com.example.subtracker
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val familyName: String,
    val familyCode: String,
    val isAdmin: Boolean = false // глава семьи
)


// +-------------------+              +-------------------------+
// |    UserEntity     |              |   SubscriptionEntity    |
// +-------------------+              +-------------------------+
// | id (PK)           |              | id (PK)                 |
// | username          |              | familyCode (FK)         |
// | familyName        |              | name                    |
// | familyCode        |<-------------| price                   |
// | isAdmin           |              | nextPaymentDate         |
// +-------------------+              | iconResName             |
// +-------------------------+

// Legend:
// PK - Primary Key
// FK - Foreign Key (not enforced by Room, but used logically)

// Relationships:
// - Один familyCode может быть у нескольких пользователей (1 семья → много участников)
// - Один familyCode может иметь несколько подписок (1 семья → много подписок)