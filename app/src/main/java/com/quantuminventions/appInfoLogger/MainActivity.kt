package com.quantuminventions.appInfoLogger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.quantuminventions.appInfoLogger.databinding.ActivityMainBinding
import com.quantuminventions.appInfoLogger.library.AppInfoLogger

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // FirebaseApp.initializeApp(this)

        binding.fab.setOnClickListener {
            AppInfoLogger.Builder(this)
                // .setFirebaseFtnCallCounter()
                // .setTimeIntervalToSave()
                .build()
                .saveAppInfo("dev", "1234")
        }
    }
}
