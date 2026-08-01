package com.example.musalla

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Color.parseColor("#0F766E")) // Beautiful Teal Color
        
        val textView = TextView(this)
        textView.text = "Welcome to Musalla App!"
        textView.textSize = 24f
        textView.setTextColor(Color.WHITE)
        textView.gravity = android.view.Gravity.CENTER
        
        layout.addView(textView)
        
        // Center the text on screen
        layout.gravity = android.view.Gravity.CENTER
        
        setContentView(layout)
    }
}
