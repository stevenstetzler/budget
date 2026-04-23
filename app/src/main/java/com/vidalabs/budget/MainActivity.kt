package com.vidalabs.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.vidalabs.budget.data.AppDatabase
import com.vidalabs.budget.data.MIGRATION_11_12
import com.vidalabs.budget.data.MIGRATION_12_13
import com.vidalabs.budget.repo.BudgetRepository
import com.vidalabs.budget.ui.BudgetApp
import com.vidalabs.budget.ui.BudgetViewModel
import com.vidalabs.budget.ui.theme.Budgetp2pTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "budgetp2p.db"
        )
            .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
            .fallbackToDestructiveMigration()
            .build()

        val repo = BudgetRepository(db.dao())

        val syncManager = com.vidalabs.budget.sync.SyncManager(applicationContext, db)

        // On startup: populate validity_lookup 12 months ahead for all existing recurrences
        lifecycleScope.launch(Dispatchers.IO) {
            repo.populateValidityLookup()
        }

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
