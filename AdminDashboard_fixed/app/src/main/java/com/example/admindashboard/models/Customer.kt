package com.example.admindashboard.models

data class Customer(
    val id: Int,
    var name: String,
    var phone: String,
    var bill: Int,
    var paid: Int,
    var due: String = "",   // ISO date string e.g. "2025-04-30"
    var note: String = ""
) {
    val balance: Int get() = bill - paid
    val isPaid: Boolean get() = balance <= 0
}
