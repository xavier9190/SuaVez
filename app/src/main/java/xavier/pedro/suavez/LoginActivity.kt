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
import xavier.pedro.suavez.databinding.ActivityLoginBinding
import xavier.pedro.suavez.ui.state.SnackbarFeedback
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.TestProfile

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val sharedViewModel: SuaVezSharedViewModel by viewModels {
        SuaVezSharedViewModel.Factory
    }

    private var navigatedToMain = false
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

        binding.btnSignInAdmin.setOnClickListener {
            signIn(TestProfile.ADMIN)
        }
        binding.btnSignInMember.setOnClickListener {
            signIn(TestProfile.MEMBER)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    if (state.currentUser != null) {
                        navigateToMain()
                        return@collect
                    }

                    val loading = state.isAuthenticating
                    binding.btnSignInAdmin.isEnabled = !loading
                    binding.btnSignInMember.isEnabled = !loading
                    binding.btnSignInAdmin.text = getString(
                        if (loading) R.string.login_loading else R.string.login_as_admin,
                    )
                    binding.btnSignInMember.text = getString(
                        if (loading) R.string.login_loading else R.string.login_as_member,
                    )
                    binding.progressLogin.isVisible = loading

                    renderSnackbar(state.snackbar)
                }
            }
        }
    }

    private fun signIn(profile: TestProfile) {
        sharedViewModel.stateHolder.startSignIn(profile)
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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
