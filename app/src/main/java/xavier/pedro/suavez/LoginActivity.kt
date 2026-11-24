package xavier.pedro.suavez

import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import xavier.pedro.suavez.auth.GoogleAuthHelper
import xavier.pedro.suavez.auth.GoogleSignInOutcome
import xavier.pedro.suavez.databinding.ActivityLoginBinding
import xavier.pedro.suavez.notifications.TaskDeadlineNotifications
import xavier.pedro.suavez.ui.state.SnackbarFeedback
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val sharedViewModel: SuaVezSharedViewModel by viewModels {
        SuaVezSharedViewModel.Factory
    }

    private var navigatedToMain = false
    private var isRequestingCredential = false
    private var lastShownSnackbarId: Long? = null
    private var currentSnackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = sys.left,
                top = sys.top,
                right = sys.right,
                bottom = maxOf(sys.bottom, ime.bottom),
            )
            insets
        }

        sharedViewModel.stateHolder.restoreSessionIfNeeded()

        binding.btnSignInGoogle.setOnClickListener {
            signInWithGoogle()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    if (state.currentUser != null) {
                        navigateToMain()
                        return@collect
                    }

                    renderLoadingState(state.isAuthenticating)
                    renderSnackbar(state.snackbar)
                }
            }
        }
    }

    private fun signInWithGoogle() {
        if (isRequestingCredential || sharedViewModel.uiState.value.isAuthenticating) return
        isRequestingCredential = true
        renderLoadingState(sharedViewModel.uiState.value.isAuthenticating)

        lifecycleScope.launch {
            val outcome = GoogleAuthHelper.requestGoogleIdToken(this@LoginActivity)
            isRequestingCredential = false
            renderLoadingState(sharedViewModel.uiState.value.isAuthenticating)

            when (outcome) {
                is GoogleSignInOutcome.Success -> {
                    sharedViewModel.stateHolder.startGoogleSignIn(outcome.idToken)
                }
                is GoogleSignInOutcome.Cancelled -> {
                    sharedViewModel.stateHolder.reportAuthError(getString(R.string.login_cancelled))
                }
                is GoogleSignInOutcome.Error -> {
                    sharedViewModel.stateHolder.reportAuthError(outcome.message)
                }
            }
        }
    }

    private fun renderLoadingState(isStateAuthenticating: Boolean) {
        val loading = isRequestingCredential || isStateAuthenticating
        binding.btnSignInGoogle.isEnabled = !loading
        binding.btnSignInGoogle.text = getString(
            if (loading) R.string.login_loading else R.string.login_with_google,
        )
        binding.progressLogin.isVisible = loading
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
        val snackbar = Snackbar.make(binding.root, feedback.message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.tinta))
            .setTextColor(ContextCompat.getColor(this, R.color.parede))
            .addCallback(
                object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (!isChangingConfigurations && sharedViewModel.uiState.value.snackbar?.id == feedback.id) {
                            sharedViewModel.stateHolder.clearSnackbar()
                        }
                    }
                },
            )
        currentSnackbar = snackbar
        snackbar.show()
    }

    private fun navigateToMain() {
        if (navigatedToMain) return
        navigatedToMain = true
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        TaskDeadlineNotifications.copyDeepLinkExtras(intent, mainIntent)
        startActivity(mainIntent)
        finish()
    }
}
