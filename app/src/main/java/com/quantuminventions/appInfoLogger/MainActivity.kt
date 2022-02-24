package com.quantuminventions.appInfoLogger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.quantuminventions.appInfoLogger.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // FirebaseApp.initializeApp(this)

        binding.fab.setOnClickListener {
            AppInfoLogger(
                this,
                AppInfoLogger.Config("app-info-logger-sample", "dev")
            ).saveAppInfo()
        }
    }
}
