package com.fran.futbolfanatico

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.fran.futbolfanatico.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

       // enableEdgeToEdge()
        // Fondo negro para la barra de estado
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)

        // Establecer iconos de la barra de estado como blancos
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)


    }


}