package com.air.quality.meter.ui.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.air.quality.meter.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Navigation is fully handled by NavHostFragment + nav_graph.xml
        // SplashFragment is the start destination and routes based on auth state
    }
}