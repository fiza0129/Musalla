package com.example.musalla

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple Screen Text (Directly without layout bug)
        val textView = TextView(this)
        textView.text = "Welcome to Musalla App!"
        textView.textSize = 24f
        textView.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        
        setContentView(textView)
    }
}
