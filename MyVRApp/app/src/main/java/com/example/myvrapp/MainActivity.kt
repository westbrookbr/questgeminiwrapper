package com.example.myvrapp

import android.app.Activity // Use android.app.Activity for simplicity
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view using the existing layout file
        // This layout (activity_main.xml) should contain R.id.textView and R.id.scrollView
        setContentView(R.layout.activity_main)
    }
}
