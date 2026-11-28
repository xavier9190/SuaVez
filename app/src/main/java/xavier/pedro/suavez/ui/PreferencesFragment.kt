package xavier.pedro.suavez.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.data.model.PreferenceLevel
import xavier.pedro.suavez.databinding.FragmentPreferencesBinding
import xavier.pedro.suavez.databinding.ItemPreferenceTaskBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import kotlin.math.roundToInt

class PreferencesFragment : Fragment() {

    private var _binding: FragmentPreferencesBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    private val renderedBindings = LinkedHashMap<String, ItemPreferenceTaskBinding>()
    private val mediumTypeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val boldTypeface by lazy { Typeface.create("sans-serif-medium", Typeface.BOLD) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPreferencesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState
                    .mapNotNull { it.toPreferencesUiModel() }
                    .distinctUntilChanged()
                    .collect(::renderPreferencesUi)
            }
        }
    }

    private fun renderPreferencesUi(model: PreferencesUiModel) {
        binding.cardPreferencesEmpty.isVisible = model.isEmpty
        binding.llPreferencesList.isVisible = !model.isEmpty

        if (model.isEmpty) {
            binding.llPreferencesList.removeAllViews()
            renderedBindings.clear()
            return
        }

        val currentIds = model.items.map { it.task.id }
        if (renderedBindings.keys.toList() != currentIds) {
            binding.llPreferencesList.removeAllViews()
            renderedBindings.clear()
            model.items.forEach { item ->
                val itemBinding = ItemPreferenceTaskBinding.inflate(
                    layoutInflater,
                    binding.llPreferencesList,
                    false,
                )
                renderedBindings[item.task.id] = itemBinding
                binding.llPreferencesList.addView(itemBinding.root)
            }
        }

        model.items.forEach { item ->
            val itemBinding = renderedBindings[item.task.id] ?: return@forEach
            bindPreferenceItem(itemBinding, item)
        }
    }

    private fun bindPreferenceItem(
        itemBinding: ItemPreferenceTaskBinding,
        item: PreferenceTaskItemUiModel,
    ) {
        itemBinding.tvTaskTitle.text = item.task.titulo
        val buttons = listOf(
            PreferenceLevel.ODEIO to itemBinding.btnPrefOdeio,
            PreferenceLevel.TANTO_FAZ to itemBinding.btnPrefTantoFaz,
            PreferenceLevel.GOSTO to itemBinding.btnPrefGosto,
        )
        buttons.forEach { (level, button) ->
            val selected = item.selectedPreference == level
            styleSegmentButton(button, level, selected)
            button.isSelected = selected
            button.contentDescription = getString(
                R.string.cd_preference_option,
                item.task.titulo,
                button.text,
            )
            button.setOnClickListener {
                sharedViewModel.stateHolder.setPreference(item.task.id, level)
            }
        }
    }

    private fun styleSegmentButton(
        button: TextView,
        level: PreferenceLevel,
        selected: Boolean,
    ) {
        val ctx = requireContext()
        if (!selected) {
            button.background = null
            button.setTextColor(ContextCompat.getColor(ctx, R.color.tinta_suave))
            button.typeface = mediumTypeface
            return
        }

        val (fillColorRes, fillAlpha, strokeColorRes, textColorRes) = when (level) {
            PreferenceLevel.ODEIO -> SegmentColors(
                fillColorRes = R.color.pref_odeio,
                fillAlpha = 36,
                strokeColorRes = R.color.pref_odeio,
                textColorRes = R.color.pref_odeio_texto,
            )
            PreferenceLevel.TANTO_FAZ -> SegmentColors(
                fillColorRes = R.color.pref_tanto_faz,
                fillAlpha = 56,
                strokeColorRes = R.color.pref_tanto_faz,
                textColorRes = R.color.tinta_suave,
            )
            PreferenceLevel.GOSTO -> SegmentColors(
                fillColorRes = R.color.pref_gosto,
                fillAlpha = 36,
                strokeColorRes = R.color.pref_gosto,
                textColorRes = R.color.pref_gosto,
            )
        }

        val strokeWidthPx = (1.5f * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val radiusPx = 999f * resources.displayMetrics.density
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(ColorUtils.setAlphaComponent(ContextCompat.getColor(ctx, fillColorRes), fillAlpha))
            setStroke(strokeWidthPx, ContextCompat.getColor(ctx, strokeColorRes))
        }
        button.setTextColor(ContextCompat.getColor(ctx, textColorRes))
        button.typeface = boldTypeface
    }

    override fun onDestroyView() {
        super.onDestroyView()
        renderedBindings.clear()
        _binding = null
    }

    private data class SegmentColors(
        val fillColorRes: Int,
        val fillAlpha: Int,
        val strokeColorRes: Int,
        val textColorRes: Int,
    )
}
