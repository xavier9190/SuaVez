package xavier.pedro.suavez

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import xavier.pedro.suavez.databinding.ActivityMainBinding
import xavier.pedro.suavez.ui.MainShellDestination
import xavier.pedro.suavez.ui.MainShellUiModel
import xavier.pedro.suavez.ui.bindSuaVezAvatar
import xavier.pedro.suavez.ui.state.MainTab
import xavier.pedro.suavez.ui.state.SnackbarFeedback
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.toMainShellUiModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val sharedViewModel: SuaVezSharedViewModel by viewModels {
        SuaVezSharedViewModel.Factory
    }

    private var navigatedToLogin = false
    private var isSyncingBottomNav = false
    private var previousHasHouse: Boolean? = null
    private var lastShownSnackbarId: Long? = null
    private var currentSnackbar: Snackbar? = null

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!sharedViewModel.stateHolder.navigateBack()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupNavigation()
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    if (state.currentUser == null) {
                        navigateToLogin()
                        return@collect
                    }

                    val shell = state.toMainShellUiModel()
                    renderTopBar(shell)
                    renderBottomNavigation(shell)
                    syncNavigationDestination(shell)
                    renderSnackbar(state.snackbar)
                }
            }
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeOpen = ime.bottom > sys.bottom
            view.updatePadding(
                left = sys.left,
                top = sys.top,
                right = sys.right,
                bottom = if (imeOpen) ime.bottom else 0,
            )
            binding.bottomNavigation.updatePadding(
                bottom = if (imeOpen) 0 else sys.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (isSyncingBottomNav) return@setOnItemSelectedListener true
            val tab = when (item.itemId) {
                R.id.tasksFragment -> MainTab.TASKS
                R.id.preferencesFragment -> MainTab.PREFERENCES
                R.id.houseFragment -> MainTab.HOUSE
                R.id.profileFragment -> MainTab.PROFILE
                else -> return@setOnItemSelectedListener false
            }
            sharedViewModel.stateHolder.selectTab(tab)
            true
        }

        binding.flTopAvatar.setOnClickListener {
            sharedViewModel.stateHolder.selectTab(MainTab.PROFILE)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val menuItemId = when (destination.id) {
                R.id.tasksFragment, R.id.openTasksFragment -> R.id.tasksFragment
                R.id.preferencesFragment -> R.id.preferencesFragment
                R.id.houseFragment, R.id.noHouseFragment -> R.id.houseFragment
                R.id.profileFragment -> R.id.profileFragment
                else -> null
            }
            if (menuItemId != null && binding.bottomNavigation.selectedItemId != menuItemId) {
                isSyncingBottomNav = true
                binding.bottomNavigation.selectedItemId = menuItemId
                isSyncingBottomNav = false
            }
        }
    }

    private fun renderTopBar(shell: MainShellUiModel) {
        binding.topAppBar.isVisible = shell.showTopBar

        val houseTitle = shell.houseTitle
        val showHouseName = !houseTitle.isNullOrBlank()
        binding.viewHouseDot.isVisible = showHouseName
        binding.tvHouseName.isVisible = showHouseName
        binding.spaceTopBar.isVisible = !showHouseName
        binding.tvHouseName.text = houseTitle.orEmpty()

        val avatarUser = shell.avatarUser
        binding.flTopAvatar.isVisible = avatarUser != null
        if (avatarUser != null) {
            bindSuaVezAvatar(
                photoView = binding.ivTopAvatar,
                initialsView = binding.tvTopAvatar,
                name = avatarUser.nome,
                idForColor = avatarUser.uid,
                fotoUrl = avatarUser.fotoUrl,
            )
        }
    }

    private fun renderBottomNavigation(shell: MainShellUiModel) {
        val menu = binding.bottomNavigation.menu
        val tasksItem = menu.findItem(R.id.tasksFragment)
        val prefsItem = menu.findItem(R.id.preferencesFragment)
        if (tasksItem.isVisible != shell.hasHouse) {
            tasksItem.isVisible = shell.hasHouse
        }
        if (prefsItem.isVisible != shell.hasHouse) {
            prefsItem.isVisible = shell.hasHouse
        }

        val selectedMenuId = when (shell.selectedTab) {
            MainTab.TASKS -> R.id.tasksFragment
            MainTab.PREFERENCES -> R.id.preferencesFragment
            MainTab.HOUSE -> R.id.houseFragment
            MainTab.PROFILE -> R.id.profileFragment
        }
        if (binding.bottomNavigation.selectedItemId != selectedMenuId) {
            isSyncingBottomNav = true
            binding.bottomNavigation.selectedItemId = selectedMenuId
            isSyncingBottomNav = false
        }
    }

    private fun syncNavigationDestination(shell: MainShellUiModel) {
        val startDestId = if (shell.hasHouse) R.id.tasksFragment else R.id.noHouseFragment
        if (navController.graph.startDestinationId != startDestId) {
            navController.graph.setStartDestination(startDestId)
        }

        val targetDestId = when (shell.destination) {
            MainShellDestination.NO_HOUSE -> R.id.noHouseFragment
            MainShellDestination.TASKS -> R.id.tasksFragment
            MainShellDestination.OPEN_TASKS -> R.id.openTasksFragment
            MainShellDestination.PREFERENCES -> R.id.preferencesFragment
            MainShellDestination.HOUSE -> R.id.houseFragment
            MainShellDestination.PROFILE -> R.id.profileFragment
        }

        val houseStateChanged = previousHasHouse != null && previousHasHouse != shell.hasHouse
        val initialResetNeeded = previousHasHouse == null && navController.currentDestination?.id != targetDestId
        previousHasHouse = shell.hasHouse

        if (navController.currentDestination?.id == targetDestId) return

        if (houseStateChanged || initialResetNeeded) {
            navController.navigate(
                targetDestId,
                null,
                navOptions {
                    launchSingleTop = true
                    popUpTo(navController.graph.id) { inclusive = true }
                },
            )
            return
        }

        if (!navController.popBackStack(targetDestId, false)) {
            navController.navigate(
                targetDestId,
                null,
                navOptions {
                    launchSingleTop = true
                    popUpTo(startDestId) { inclusive = false }
                },
            )
        }
    }

    private fun renderSnackbar(feedback: SnackbarFeedback?) {
        if (feedback == null) {
            currentSnackbar?.dismiss()
            currentSnackbar = null
            return
        }
        if (feedback.id == lastShownSnackbarId) return

        lastShownSnackbarId = feedback.id
        currentSnackbar?.dismiss()

        val snackbar = Snackbar.make(binding.snackbarContainer, feedback.message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.tinta))
            .setTextColor(ContextCompat.getColor(this, R.color.parede))
            .setActionTextColor(ContextCompat.getColor(this, R.color.ambar_vez))
            .addCallback(
                object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (!isChangingConfigurations && sharedViewModel.uiState.value.snackbar?.id == feedback.id) {
                            sharedViewModel.stateHolder.clearSnackbar()
                        }
                    }
                },
            )

        val actionLabel = feedback.actionLabel
        val onAction = feedback.onAction
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            snackbar.setAction(actionLabel) {
                onAction.invoke()
            }
        }

        currentSnackbar = snackbar
        snackbar.show()
    }

    private fun navigateToLogin() {
        if (navigatedToLogin) return
        navigatedToLogin = true
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
