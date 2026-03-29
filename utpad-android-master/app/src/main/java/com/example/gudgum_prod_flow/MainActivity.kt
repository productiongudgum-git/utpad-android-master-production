package com.example.gudgum_prod_flow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.gudgum_prod_flow.ui.navigation.UtpadNavGraph
import com.example.gudgum_prod_flow.ui.theme.GudGumProdFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GudGumProdFlowTheme {
                val navController = rememberNavController()
                UtpadNavGraph(navController = navController)
            }
        }
    }
}
