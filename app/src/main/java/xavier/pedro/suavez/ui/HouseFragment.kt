package xavier.pedro.suavez.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.databinding.FragmentHouseBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel

class HouseFragment : Fragment() {

    private var _binding: FragmentHouseBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHouseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    val house = state.currentHouse
                    binding.tvHouseTitle.text = house?.nome ?: getString(R.string.nav_house)
                    binding.tvHouseInvite.isVisible = house != null
                    if (house != null) {
                        binding.tvHouseInvite.text = getString(
                            R.string.placeholder_house_invite,
                            house.codigoConvite,
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
}
