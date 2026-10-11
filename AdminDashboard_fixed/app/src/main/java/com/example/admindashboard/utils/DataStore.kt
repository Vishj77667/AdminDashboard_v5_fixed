package com.example.admindashboard.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.admindashboard.models.Customer
import com.example.admindashboard.models.Product
import com.example.admindashboard.models.Task
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persistent data store backed by SharedPreferences + Gson.
 * Call DataStore.init(context) once from MainActivity.onCreate()
 * before any fragment accesses the store.
 */
object DataStore {

    private const val PREFS_NAME           = "admin_dashboard_prefs"
    private const val KEY_PRODUCTS         = "products"
    private const val KEY_CUSTOMERS        = "customers"
    private const val KEY_TASKS            = "tasks"
    private const val KEY_NEXT_PRODUCT_ID  = "next_product_id"
    private const val KEY_NEXT_CUSTOMER_ID = "next_customer_id"
    private const val KEY_NEXT_TASK_ID     = "next_task_id"
    private const val KEY_BACKUP_LOG       = "backup_log"

    private val gson = Gson()
    private lateinit var prefs: SharedPreferences

    // ── In-memory lists (loaded from prefs on init) ──────────────────────
    val products:  MutableList<Product>  = mutableListOf()
    val customers: MutableList<Customer> = mutableListOf()
    val tasks:     MutableList<Task>     = mutableListOf()
    val backupLog: MutableList<LogEntry> = mutableListOf()

    var nextProductId  = 7
    var nextCustomerId = 1
    var nextTaskId     = 1

    // ── Init ─────────────────────────────────────────────────────────────
    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        load()
    }

    // ── Load from SharedPreferences ───────────────────────────────────────
    private fun load() {
        // Products
        val prodJson = prefs.getString(KEY_PRODUCTS, null)
        if (prodJson != null) {
            val type = object : TypeToken<List<Product>>() {}.type
            val loaded: List<Product> = gson.fromJson(prodJson, type) ?: emptyList()
            products.clear()
            products.addAll(loaded)
        } else {
            // First launch — seed default products
            products.addAll(listOf(
                Product(1, "Enterprise Plan", "plans",    12999, 999, 4.8, 312),
                Product(2, "Pro Plan",         "plans",    4999, 999, 4.5, 541),
                Product(3, "Starter Plan",     "plans",    1499, 999, 4.1, 873),
                Product(4, "Analytics Add-on", "addons",    799,  45, 4.3, 210),
                Product(5, "Priority Support", "addons",   1299,   8, 4.7,  98),
                Product(6, "Developer Kit",    "hardware", 8499,  22, 4.2,  34)
            ))
        }

        // Customers
        val custJson = prefs.getString(KEY_CUSTOMERS, null)
        if (custJson != null) {
            val type = object : TypeToken<List<Customer>>() {}.type
            val loaded: List<Customer> = gson.fromJson(custJson, type) ?: emptyList()
            customers.clear()
            customers.addAll(loaded)
        }

        // Tasks
        val taskJson = prefs.getString(KEY_TASKS, null)
        if (taskJson != null) {
            val type = object : TypeToken<List<Task>>() {}.type
            val loaded: List<Task> = gson.fromJson(taskJson, type) ?: emptyList()
            tasks.clear()
            tasks.addAll(loaded)
        }

        // Counters
        nextProductId  = prefs.getInt(KEY_NEXT_PRODUCT_ID,  (products.maxOfOrNull { it.id } ?: 6) + 1)
        nextCustomerId = prefs.getInt(KEY_NEXT_CUSTOMER_ID, (customers.maxOfOrNull { it.id } ?: 0) + 1)
        nextTaskId     = prefs.getInt(KEY_NEXT_TASK_ID,     (tasks.maxOfOrNull { it.id } ?: 0) + 1)

        // Backup log
        val logJson = prefs.getString(KEY_BACKUP_LOG, null)
        if (logJson != null) {
            val type = object : TypeToken<List<LogEntry>>() {}.type
            val loaded: List<LogEntry> = gson.fromJson(logJson, type) ?: emptyList()
            backupLog.clear()
            backupLog.addAll(loaded)
        }
    }

    // ── Save to SharedPreferences ─────────────────────────────────────────
    fun save() {
        prefs.edit().apply {
            putString(KEY_PRODUCTS,  gson.toJson(products))
            putString(KEY_CUSTOMERS, gson.toJson(customers))
            putString(KEY_TASKS,     gson.toJson(tasks))
            putInt(KEY_NEXT_PRODUCT_ID,  nextProductId)
            putInt(KEY_NEXT_CUSTOMER_ID, nextCustomerId)
            putInt(KEY_NEXT_TASK_ID,     nextTaskId)
            putString(KEY_BACKUP_LOG, gson.toJson(backupLog))
            apply()
        }
    }

    // ── Backup / Restore ─────────────────────────────────────────────────

    data class BackupWrapper(
        val products: List<Product>? = null,
        val customers: List<Customer>? = null,
        val tasks: List<Task>? = null
    )

    fun getBackupJson(type: String): String {
        val backup = when (type) {
            "products" -> BackupWrapper(products = products)
            "customers" -> BackupWrapper(customers = customers)
            "tasks" -> BackupWrapper(tasks = tasks)
            else -> BackupWrapper(products, customers, tasks)
        }
        return gson.toJson(backup)
    }

    fun restoreFromJson(json: String): Boolean {
        return try {
            val backup = gson.fromJson(json, BackupWrapper::class.java) ?: return false
            var changed = false
            backup.products?.let {
                products.clear()
                products.addAll(it)
                nextProductId = (products.maxOfOrNull { p -> p.id } ?: 0) + 1
                changed = true
            }
            backup.customers?.let {
                customers.clear()
                customers.addAll(it)
                nextCustomerId = (customers.maxOfOrNull { c -> c.id } ?: 0) + 1
                changed = true
            }
            backup.tasks?.let {
                tasks.clear()
                tasks.addAll(it)
                nextTaskId = (tasks.maxOfOrNull { t -> t.id } ?: 0) + 1
                changed = true
            }
            if (changed) save()
            changed
        } catch (e: Exception) {
            false
        }
    }

    // ── Backup log entry ──────────────────────────────────────────────────
    data class LogEntry(val action: String, val detail: String, val time: String)

    // ── Helpers ───────────────────────────────────────────────────────────
    fun formatRupees(amount: Int): String =
        "₹${String.format("%,d", amount)}"

    fun stars(rating: Double): String {
        val full  = rating.toInt()
        val half  = if (rating - full >= 0.5) 1 else 0
        val empty = 5 - full - half
        return "★".repeat(full) + (if (half == 1) "½" else "") + "☆".repeat(empty)
    }

    fun initials(name: String): String =
        name.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

    val categoryEmojis = mapOf(
        "plans"    to listOf("📦", "🚀", "⭐", "💎"),
        "addons"   to listOf("🔌", "🛠", "🔧", "📊"),
        "hardware" to listOf("💻", "🖥", "⌨", "🖱")
    )

    fun emojiFor(p: Product): String =
        (categoryEmojis[p.category] ?: listOf("📦")).let { it[p.id % it.size] }
}
