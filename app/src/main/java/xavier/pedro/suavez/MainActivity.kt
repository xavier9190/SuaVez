package xavier.pedro.suavez

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import xavier.pedro.suavez.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.taskCreateFragment) {
                binding.bottomNavigation.menu.findItem(R.id.tasksFragment)?.isChecked = true
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.tasksFragment && navController.currentDestination?.id == R.id.taskCreateFragment) {
                navController.popBackStack(R.id.tasksFragment, false)
            } else {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }

        binding.bottomNavigation.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.tasksFragment) {
                navController.popBackStack(R.id.tasksFragment, false)
            }
        }
    }

}
