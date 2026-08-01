package com.example.musalla

import android.os.Bundle
import android.widget.TextView
import android.app.Activity

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this)
        textView.text = "Welcome to Musalla App!"
        textView.textSize = 28f
        textView.gravity = android.view.Gravity.CENTER
        
        setContentView(textView)
    }
}
