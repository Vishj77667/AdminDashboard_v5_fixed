package com.example.admindashboard.models

data class Product(
    val id: Int,
    var name: String,
    var category: String,   // "plans", "addons", "hardware"
    var price: Int,
    var stock: Int,
    var rating: Double,
    var sales: Int = 0
)
