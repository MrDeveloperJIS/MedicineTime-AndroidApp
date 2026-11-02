package com.mrjis.medicinetime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mrjis.medicinetime.ui.MedicineTimeApp
import com.mrjis.medicinetime.ui.theme.MedicineTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedicineTimeTheme {
                MedicineTimeApp()
            }
        }
    }
}