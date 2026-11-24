package xavier.pedro.suavez.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.databinding.FragmentOpenTasksBinding
import xavier.pedro.suavez.databinding.ItemOpenTaskCardBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.SuaVezUiState
import xavier.pedro.suavez.ui.state.UiAssignment

class OpenTasksFragment : Fragment() {

    private var _binding: FragmentOpenTasksBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOpenTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnAddActivityTop.setOnClickListener { openCreateActivitySheet() }
        binding.btnCreateActivityDashed.setOnClickListener { openCreateActivitySheet() }
        binding.btnDistributePending.setOnClickListener {
            sharedViewModel.stateHolder.distributePendingTasks()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(uiState: SuaVezUiState) {
        val visibleOpenTasks = uiState.openScreenAssignments
        val trulyOpenTasks = uiState.openAssignments

        binding.cardOpenTasksEmpty.isVisible = visibleOpenTasks.isEmpty()
        binding.llOpenTasks.isVisible = visibleOpenTasks.isNotEmpty()
        binding.llOpenTasks.removeAllViews()

        visibleOpenTasks.forEach { task ->
            val cardBinding = ItemOpenTaskCardBinding.inflate(
                layoutInflater,
                binding.llOpenTasks,
                false,
            )
            bindOpenTaskCard(cardBinding, task, uiState.isAdmin)
            binding.llOpenTasks.addView(cardBinding.root)
        }

        binding.btnDistributePending.isVisible = uiState.isAdmin
        binding.btnDistributePending.isEnabled = trulyOpenTasks.isNotEmpty()
        binding.btnDistributePending.alpha = if (trulyOpenTasks.isNotEmpty()) 1f else 0.5f
    }

    private fun bindOpenTaskCard(
        cardBinding: ItemOpenTaskCardBinding,
        task: UiAssignment,
        isAdmin: Boolean,
    ) {
        val ctx = requireContext()
        val (bgColorRes, tintColorRes) = when (task.effortLevel) {
            TaskWeightLevel.HEAVY -> R.color.ambar_vez_suave to R.color.pref_odeio
            TaskWeightLevel.MEDIUM -> R.color.ambar_vez_suave to R.color.ambar_vez_texto
            TaskWeightLevel.LIGHT -> R.color.nevoa to R.color.verde_fundo
        }
        ViewCompat.setBackgroundTintList(
            cardBinding.flIconCircle,
            ColorStateList.valueOf(ContextCompat.getColor(ctx, bgColorRes)),
        )
        cardBinding.ivTaskIcon.setImageResource(task.iconHint.iconRes())
        cardBinding.ivTaskIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, tintColorRes))

        cardBinding.tvTaskTitle.text = task.titulo
        val pointsLabel = if (task.pontos == 1) {
            getString(R.string.tasks_effort_pt_singular, task.pontos)
        } else {
            getString(R.string.tasks_effort_pts_plural, task.pontos)
        }
        val metaParts = buildList {
            add(task.categoria.label)
            add(pointsLabel)
            task.deadline?.toString()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        cardBinding.tvTaskMeta.text = metaParts.joinToString(" · ")

        val canEdit = isAdmin && !task.justClaimedInSession
        cardBinding.btnEditTask.isVisible = canEdit
        cardBinding.btnEditTask.setOnClickListener { openEditAssignmentSheet(task.id) }

        cardBinding.btnClaimTask.isVisible = !task.justClaimedInSession
        cardBinding.tvClaimedBadge.isVisible = task.justClaimedInSession
        cardBinding.btnClaimTask.setOnClickListener {
            sharedViewModel.stateHolder.claimOpenTask(task.id)
        }
    }

    private fun openCreateActivitySheet() {
        if (childFragmentManager.findFragmentByTag(ActivityFormBottomSheet.TAG) == null &&
            !childFragmentManager.isStateSaved
        ) {
            ActivityFormBottomSheet.newCreateInstance()
                .show(childFragmentManager, ActivityFormBottomSheet.TAG)
        }
    }

    private fun openEditAssignmentSheet(assignmentId: String) {
        if (childFragmentManager.findFragmentByTag(ActivityFormBottomSheet.TAG) == null &&
            !childFragmentManager.isStateSaved
        ) {
            ActivityFormBottomSheet.newEditAssignmentInstance(assignmentId)
                .show(childFragmentManager, ActivityFormBottomSheet.TAG)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
