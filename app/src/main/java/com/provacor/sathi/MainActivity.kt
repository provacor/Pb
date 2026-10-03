package com.provacor.sathi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.provacor.sathi.ui.SathiNavHost
import com.provacor.sathi.ui.theme.SathiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SathiTheme {
                SathiNavHost()
            }
        }
    }
}
