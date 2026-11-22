package xavier.pedro.suavez.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.databinding.FragmentProfileBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.avatarIndexFor
import xavier.pedro.suavez.ui.state.initialsOf

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSignOut.setOnClickListener {
            sharedViewModel.stateHolder.signOut()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    val user = state.currentUser
                    binding.tvProfileName.text = user?.nome.orEmpty()
                    binding.tvProfileEmail.text = user?.email.orEmpty()
                    if (user != null) {
                        binding.tvProfileAvatar.text = initialsOf(user.nome)
                        val colorRes = AVATAR_COLORS[avatarIndexFor(user.uid)]
                        ViewCompat.setBackgroundTintList(
                            binding.tvProfileAvatar,
                            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes)),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val AVATAR_COLORS = intArrayOf(
            R.color.avatar_1,
            R.color.avatar_2,
            R.color.avatar_3,
            R.color.avatar_4,
        )
    }
}
