package xavier.pedro.suavez.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.databinding.BottomSheetCreateHouseBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel

class CreateHouseBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateHouseBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetCreateHouseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.etHouseName.doAfterTextChanged {
            binding.tilHouseName.error = null
        }

        binding.etHouseName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitHouseName()
                true
            } else {
                false
            }
        }

        binding.btnConfirmCreate.setOnClickListener {
            submitHouseName()
        }

        binding.btnCancelCreate.setOnClickListener {
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    binding.btnConfirmCreate.isEnabled = !state.isAuthenticating
                }
            }
        }
    }

    private fun submitHouseName() {
        val cleanName = binding.etHouseName.text?.toString().orEmpty().trim()
        if (cleanName.length < 2) {
            binding.tilHouseName.error = getString(R.string.error_invalid_house_name)
            return
        }
        sharedViewModel.stateHolder.createHouse(cleanName)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateHouseBottomSheet"
    }
}
