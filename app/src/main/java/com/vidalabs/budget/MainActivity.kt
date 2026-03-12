package com.vidalabs.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.vidalabs.budget.data.AppDatabase
import com.vidalabs.budget.repo.BudgetRepository
import com.vidalabs.budget.ui.BudgetApp
import com.vidalabs.budget.ui.BudgetViewModel
import com.vidalabs.budget.ui.theme.Budgetp2pTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "budgetp2p.db"
        ).fallbackToDestructiveMigration().build()

        val repo = BudgetRepository(db.dao())

        val syncManager = com.vidalabs.budget.sync.SyncManager(applicationContext, db)

        val vm = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BudgetViewModel(repo, syncManager) as T
                }
            }
        )[BudgetViewModel::class.java]

        setContent {
            Budgetp2pTheme {
                BudgetApp(vm = vm, sync = syncManager)
            }
        }
    }
}
