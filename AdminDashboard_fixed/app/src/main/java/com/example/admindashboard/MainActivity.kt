package com.example.admindashboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.admindashboard.databinding.ActivityMainBinding
import com.example.admindashboard.utils.DataStore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── MUST be called before any fragment touches DataStore ──────────
        DataStore.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.bottomNavigationView.setupWithNavController(navController)

        // Update top-bar title on destination change
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.tvPageTitle.text = destination.label ?: getString(R.string.app_name)
        }
    }
}
