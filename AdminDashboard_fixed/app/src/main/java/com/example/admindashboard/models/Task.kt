package com.example.admindashboard.models

data class Task(
    val id: Int,
    var title: String,
    var desc: String = "",
    var priority: String = "medium",   // "high", "medium", "low"
    var category: String = "work",     // "work", "personal", "other"
    var date: String = "",             // ISO date "2025-04-30"
    var time: String = "",             // "HH:mm"
    var repeat: Boolean = false,
    var done: Boolean = false
)
