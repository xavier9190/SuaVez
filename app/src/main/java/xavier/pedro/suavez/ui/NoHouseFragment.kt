package xavier.pedro.suavez.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.data.repository.HouseRepository
import xavier.pedro.suavez.databinding.FragmentNoHouseBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel

class NoHouseFragment : Fragment() {

    private var _binding: FragmentNoHouseBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNoHouseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etInviteCode.doAfterTextChanged {
            binding.tilInviteCode.error = null
        }

        binding.etInviteCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitInviteCode()
                true
            } else {
                false
            }
        }

        binding.btnJoinHouse.setOnClickListener {
            submitInviteCode()
        }

        binding.btnCreateHouse.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(CreateHouseBottomSheet.TAG) == null &&
                !childFragmentManager.isStateSaved
            ) {
                CreateHouseBottomSheet().show(childFragmentManager, CreateHouseBottomSheet.TAG)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    val enabled = !state.isAuthenticating
                    binding.btnCreateHouse.isEnabled = enabled
                    binding.btnJoinHouse.isEnabled = enabled
                    binding.etInviteCode.isEnabled = enabled
                }
            }
        }
    }

    private fun submitInviteCode() {
        val raw = binding.etInviteCode.text?.toString().orEmpty()
        if (!HouseRepository.isValidInviteCode(raw)) {
            binding.tilInviteCode.error = getString(R.string.error_invalid_invite_code)
            return
        }
        val normalized = HouseRepository.normalizeInviteCode(raw)
        sharedViewModel.stateHolder.joinHouseByCode(normalized)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
