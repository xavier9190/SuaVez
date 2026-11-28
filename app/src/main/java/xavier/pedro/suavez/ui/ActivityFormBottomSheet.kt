package xavier.pedro.suavez.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.databinding.BottomSheetActivityFormBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.TaskCategory
import xavier.pedro.suavez.ui.state.TaskDeadline
import xavier.pedro.suavez.ui.state.TaskFrequency

class ActivityFormBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetActivityFormBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    private var draft = ActivityFormDraft.forCreate(isAdmin = false)
    private var errors = ActivityFormErrors()
    private var initializedFromState = false
    private var isSyncingTitle = false
    private var deleteConfirmDialog: AlertDialog? = null
    private var confirmDeleteOpen = false

    private val formMode: ActivityFormMode by lazy {
        val raw = arguments?.getString(ARG_MODE)
        ActivityFormMode.entries.firstOrNull { it.name == raw } ?: ActivityFormMode.CREATE
    }

    private val targetId: String? by lazy {
        arguments?.getString(ARG_TARGET_ID)
    }

    override fun onStart() {
        super.onStart()
        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        bottomSheetDialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetActivityFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        restoreStateIfPresent(savedInstanceState)
        setupStaticLabels()
        setupListeners()

        if (initializedFromState) {
            isSyncingTitle = true
            binding.etActivityTitle.setText(draft.title)
            isSyncingTitle = false
            render()
            if (confirmDeleteOpen) {
                showDeleteConfirmation()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    when (formMode) {
                        ActivityFormMode.CREATE -> {
                            if (!initializedFromState) {
                                draft = ActivityFormDraft.forCreate(isAdmin = state.isAdmin)
                                initializedFromState = true
                                render()
                            } else if (draft.isAdmin != state.isAdmin) {
                                draft = draft.copy(
                                    isAdmin = state.isAdmin,
                                    recurrence = if (!state.isAdmin) {
                                        ActivityFormRecurrence.UNICA
                                    } else {
                                        draft.recurrence
                                    },
                                )
                                render()
                            }
                        }

                        ActivityFormMode.EDIT_ASSIGNMENT -> {
                            val assignment = state.assignments.firstOrNull { it.id == targetId }
                            if (!state.isAdmin || assignment == null || assignment.justClaimedInSession) {
                                dismissAllowingStateLoss()
                                return@collect
                            }
                            if (!initializedFromState) {
                                draft = ActivityFormDraft.forEditAssignment(assignment, state.isAdmin)
                                initializedFromState = true
                                isSyncingTitle = true
                                binding.etActivityTitle.setText(draft.title)
                                isSyncingTitle = false
                                render()
                            }
                        }

                        ActivityFormMode.EDIT_RECURRING -> {
                            val recurring = state.recurringTasks.firstOrNull { it.id == targetId }
                            if (!state.isAdmin || recurring == null) {
                                dismissAllowingStateLoss()
                                return@collect
                            }
                            if (!initializedFromState) {
                                draft = ActivityFormDraft.forEditRecurring(recurring, state.isAdmin)
                                initializedFromState = true
                                isSyncingTitle = true
                                binding.etActivityTitle.setText(draft.title)
                                isSyncingTitle = false
                                render()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupStaticLabels() {
        binding.chipCatLimpeza.text = TaskCategory.LIMPEZA.label
        binding.chipCatCompras.text = TaskCategory.COMPRAS.label
        binding.chipCatContas.text = TaskCategory.CONTAS.label
        binding.chipCatOutras.text = TaskCategory.OUTRAS.label

        binding.chipEffortLight.text = TaskWeightLevel.LIGHT.label
        binding.chipEffortMedium.text = TaskWeightLevel.MEDIUM.label
        binding.chipEffortHeavy.text = TaskWeightLevel.HEAVY.label

        binding.chipFreqDiaria.text = TaskFrequency.DIARIA.label
        binding.chipFreqSemanal.text = TaskFrequency.SEMANAL.label
        binding.chipFreqQuinzenal.text = TaskFrequency.QUINZENAL.label
        binding.chipFreqMensal.text = TaskFrequency.MENSAL.label

        binding.chipDeadlineHoje.text = TaskDeadline.HOJE.label
        binding.chipDeadlineSabado.text = TaskDeadline.SABADO.label
        binding.chipDeadlineDomingo.text = TaskDeadline.DOMINGO.label
        binding.chipDeadlineSemPrazo.text = TaskDeadline.SEM_PRAZO.label
    }

    private fun setupListeners() {
        binding.etActivityTitle.doAfterTextChanged { text ->
            if (isSyncingTitle) return@doAfterTextChanged
            draft = draft.copy(title = text?.toString().orEmpty())
            if (errors.titleRequired) {
                errors = errors.copy(titleRequired = false)
                binding.tilActivityTitle.error = null
            }
        }

        binding.etActivityTitle.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.etActivityTitle.clearFocus()
                true
            } else {
                false
            }
        }

        mapOf(
            binding.chipCatLimpeza to TaskCategory.LIMPEZA,
            binding.chipCatCompras to TaskCategory.COMPRAS,
            binding.chipCatContas to TaskCategory.CONTAS,
            binding.chipCatOutras to TaskCategory.OUTRAS,
        ).forEach { (chip, category) ->
            chip.setOnClickListener {
                draft = draft.copy(category = category)
                errors = errors.copy(categoryRequired = false)
                render()
            }
        }

        mapOf(
            binding.chipEffortLight to TaskWeightLevel.LIGHT.points,
            binding.chipEffortMedium to TaskWeightLevel.MEDIUM.points,
            binding.chipEffortHeavy to TaskWeightLevel.HEAVY.points,
        ).forEach { (chip, points) ->
            chip.setOnClickListener {
                draft = draft.copy(effortPoints = points)
                errors = errors.copy(effortRequired = false)
                render()
            }
        }

        binding.chipRecUnica.setOnClickListener {
            draft = draft.withRecurrence(ActivityFormRecurrence.UNICA)
            errors = errors.copy(
                recurrenceRequired = false,
                frequencyRequired = false,
                openNowRequired = false,
                deadlineRequired = false,
            )
            render()
        }

        binding.chipRecRecorrente.setOnClickListener {
            draft = draft.withRecurrence(ActivityFormRecurrence.RECORRENTE)
            errors = errors.copy(
                recurrenceRequired = false,
                frequencyRequired = false,
                openNowRequired = false,
                deadlineRequired = false,
            )
            render()
        }

        mapOf(
            binding.chipFreqDiaria to TaskFrequency.DIARIA,
            binding.chipFreqSemanal to TaskFrequency.SEMANAL,
            binding.chipFreqQuinzenal to TaskFrequency.QUINZENAL,
            binding.chipFreqMensal to TaskFrequency.MENSAL,
        ).forEach { (chip, frequency) ->
            chip.setOnClickListener {
                draft = draft.copy(frequency = frequency)
                errors = errors.copy(frequencyRequired = false)
                render()
            }
        }

        binding.chipOpenNowYes.setOnClickListener {
            draft = draft.withOpenInCurrentWeek(true)
            errors = errors.copy(openNowRequired = false, deadlineRequired = false)
            render()
        }

        binding.chipOpenNowNo.setOnClickListener {
            draft = draft.withOpenInCurrentWeek(false)
            errors = errors.copy(openNowRequired = false, deadlineRequired = false)
            render()
        }

        mapOf(
            binding.chipDeadlineHoje to TaskDeadline.HOJE,
            binding.chipDeadlineSabado to TaskDeadline.SABADO,
            binding.chipDeadlineDomingo to TaskDeadline.DOMINGO,
            binding.chipDeadlineSemPrazo to TaskDeadline.SEM_PRAZO,
        ).forEach { (chip, deadline) ->
            chip.setOnClickListener {
                draft = draft.copy(deadline = deadline)
                errors = errors.copy(deadlineRequired = false)
                render()
            }
        }

        binding.btnSubmitActivity.setOnClickListener { submitForm() }
        binding.btnDeleteActivity.setOnClickListener { showDeleteConfirmation() }
        binding.btnCancelActivity.setOnClickListener { dismiss() }
    }

    private fun submitForm() {
        when (val result = draft.validate(targetId = targetId)) {
            is ActivityFormValidationResult.Invalid -> {
                errors = result.errors
                render()
            }

            ActivityFormValidationResult.Unauthorized -> {
                dismissAllowingStateLoss()
            }

            is ActivityFormValidationResult.Valid -> {
                val stateHolder = sharedViewModel.stateHolder
                when (val submission = result.submission) {
                    is ActivityFormSubmission.CreateWeekly -> {
                        stateHolder.addWeeklyTask(
                            title = submission.title,
                            category = submission.category,
                            points = submission.points,
                            deadline = submission.deadline,
                        )
                    }

                    is ActivityFormSubmission.UpdateWeekly -> {
                        stateHolder.updateWeeklyAssignment(
                            assignmentId = submission.assignmentId,
                            title = submission.title,
                            category = submission.category,
                            points = submission.points,
                            deadline = submission.deadline,
                        )
                    }

                    is ActivityFormSubmission.SaveRecurring -> {
                        stateHolder.saveRecurringTask(
                            editingId = submission.editingId,
                            title = submission.title,
                            category = submission.category,
                            points = submission.points,
                            frequency = submission.frequency,
                            openInCurrentWeek = submission.openInCurrentWeek,
                            deadline = submission.deadline,
                        )
                    }
                }
                dismiss()
            }
        }
    }

    private fun showDeleteConfirmation() {
        if (!draft.canDelete || targetId.isNullOrBlank()) return
        val isRecurring = draft.mode == ActivityFormMode.EDIT_RECURRING
        val currentState = sharedViewModel.uiState.value
        val displayTitle = if (isRecurring) {
            currentState.recurringTasks.firstOrNull { it.id == targetId }?.titulo ?: draft.title
        } else {
            currentState.assignments.firstOrNull { it.id == targetId }?.titulo ?: draft.title
        }

        val titleRes = if (isRecurring) {
            R.string.dialog_delete_recurring_title
        } else {
            R.string.dialog_delete_activity_title
        }
        val message = if (isRecurring) {
            getString(R.string.dialog_delete_recurring_message, displayTitle)
        } else {
            getString(R.string.dialog_delete_activity_message, displayTitle)
        }

        confirmDeleteOpen = true
        deleteConfirmDialog?.dismiss()
        deleteConfirmDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                confirmDeleteOpen = false
            }
            .setPositiveButton(R.string.action_confirm_delete) { _, _ ->
                confirmDeleteOpen = false
                val id = targetId ?: return@setPositiveButton
                if (isRecurring) {
                    sharedViewModel.stateHolder.deleteRecurringTask(id)
                } else {
                    sharedViewModel.stateHolder.deleteWeeklyAssignment(id)
                }
                dismiss()
            }
            .setOnDismissListener {
                confirmDeleteOpen = false
            }
            .show()
    }

    private fun render() {
        if (_binding == null) return

        binding.tvSheetTitle.setText(
            when (draft.mode) {
                ActivityFormMode.EDIT_RECURRING -> R.string.sheet_activity_edit_recurring_title
                ActivityFormMode.EDIT_ASSIGNMENT -> R.string.sheet_activity_edit_title
                ActivityFormMode.CREATE -> R.string.sheet_activity_create_title
            },
        )
        binding.tvModeChip.isVisible = draft.isEditing
        if (draft.isEditing) {
            binding.tvModeChip.setText(
                if (draft.mode == ActivityFormMode.EDIT_RECURRING) {
                    R.string.sheet_activity_badge_recurring
                } else {
                    R.string.sheet_activity_badge_unique
                },
            )
        }

        binding.etActivityTitle.setHint(
            if (draft.showFrequencySection) {
                R.string.hint_activity_title_recurring
            } else {
                R.string.hint_activity_title_unique
            },
        )

        bindRequiredLabel(binding.tvLabelTitle, R.string.label_activity_title, errors.titleRequired)
        binding.tilActivityTitle.error = if (errors.titleRequired) {
            getString(R.string.error_activity_title)
        } else {
            null
        }

        bindRequiredLabel(binding.tvLabelCategory, R.string.label_activity_category, errors.categoryRequired)
        binding.tvErrorCategory.isVisible = errors.categoryRequired
        styleChip(binding.chipCatLimpeza, draft.category == TaskCategory.LIMPEZA, errors.categoryRequired)
        styleChip(binding.chipCatCompras, draft.category == TaskCategory.COMPRAS, errors.categoryRequired)
        styleChip(binding.chipCatContas, draft.category == TaskCategory.CONTAS, errors.categoryRequired)
        styleChip(binding.chipCatOutras, draft.category == TaskCategory.OUTRAS, errors.categoryRequired)

        bindRequiredLabel(binding.tvLabelEffort, R.string.label_activity_effort, errors.effortRequired)
        binding.tvErrorEffort.isVisible = errors.effortRequired
        styleChip(
            binding.chipEffortLight,
            draft.effortPoints == TaskWeightLevel.LIGHT.points,
            errors.effortRequired,
            ChipTone.DEFAULT,
        )
        styleChip(
            binding.chipEffortMedium,
            draft.effortPoints == TaskWeightLevel.MEDIUM.points,
            errors.effortRequired,
            ChipTone.EFFORT_MEDIA,
        )
        styleChip(
            binding.chipEffortHeavy,
            draft.effortPoints == TaskWeightLevel.HEAVY.points,
            errors.effortRequired,
            ChipTone.EFFORT_PESADA,
        )

        binding.layoutSectionRecurrence.isVisible = draft.showTypeSelector
        if (draft.showTypeSelector) {
            bindRequiredLabel(
                binding.tvLabelRecurrence,
                R.string.label_activity_recurrence,
                errors.recurrenceRequired,
            )
            binding.tvErrorRecurrence.isVisible = errors.recurrenceRequired
            styleChip(
                binding.chipRecUnica,
                draft.effectiveRecurrence == ActivityFormRecurrence.UNICA,
                errors.recurrenceRequired,
            )
            styleChip(
                binding.chipRecRecorrente,
                draft.effectiveRecurrence == ActivityFormRecurrence.RECORRENTE,
                errors.recurrenceRequired,
            )
        }

        binding.layoutSectionFrequency.isVisible = draft.showFrequencySection
        if (draft.showFrequencySection) {
            bindRequiredLabel(
                binding.tvLabelFrequency,
                R.string.label_activity_frequency,
                errors.frequencyRequired,
            )
            binding.tvErrorFrequency.isVisible = errors.frequencyRequired
            styleChip(binding.chipFreqDiaria, draft.frequency == TaskFrequency.DIARIA, errors.frequencyRequired)
            styleChip(binding.chipFreqSemanal, draft.frequency == TaskFrequency.SEMANAL, errors.frequencyRequired)
            styleChip(
                binding.chipFreqQuinzenal,
                draft.frequency == TaskFrequency.QUINZENAL,
                errors.frequencyRequired,
            )
            styleChip(binding.chipFreqMensal, draft.frequency == TaskFrequency.MENSAL, errors.frequencyRequired)
        }

        binding.layoutSectionOpenNow.isVisible = draft.showOpenNowSection
        if (draft.showOpenNowSection) {
            bindRequiredLabel(
                binding.tvLabelOpenNow,
                R.string.label_activity_open_now,
                errors.openNowRequired,
            )
            binding.tvErrorOpenNow.isVisible = errors.openNowRequired
            styleChip(binding.chipOpenNowYes, draft.openInCurrentWeek == true, errors.openNowRequired)
            styleChip(binding.chipOpenNowNo, draft.openInCurrentWeek == false, errors.openNowRequired)
        }

        binding.layoutSectionDeadline.isVisible = draft.showDeadlineSection
        if (draft.showDeadlineSection) {
            bindRequiredLabel(
                binding.tvLabelDeadline,
                R.string.label_activity_deadline,
                errors.deadlineRequired,
            )
            binding.tvErrorDeadline.isVisible = errors.deadlineRequired
            styleChip(binding.chipDeadlineHoje, draft.deadline == TaskDeadline.HOJE, errors.deadlineRequired)
            styleChip(binding.chipDeadlineSabado, draft.deadline == TaskDeadline.SABADO, errors.deadlineRequired)
            styleChip(binding.chipDeadlineDomingo, draft.deadline == TaskDeadline.DOMINGO, errors.deadlineRequired)
            styleChip(binding.chipDeadlineSemPrazo, draft.deadline == TaskDeadline.SEM_PRAZO, errors.deadlineRequired)
        }

        binding.btnSubmitActivity.setText(
            if (draft.isEditing) R.string.action_save_changes else R.string.action_create_activity,
        )
        binding.btnDeleteActivity.isVisible = draft.canDelete
        binding.btnCancelActivity.isVisible = !draft.canDelete
        if (draft.canDelete) {
            binding.btnDeleteActivity.setText(
                if (draft.mode == ActivityFormMode.EDIT_RECURRING) {
                    R.string.action_delete_recurring_task
                } else {
                    R.string.action_delete_activity
                },
            )
        }
    }

    private fun bindRequiredLabel(view: TextView, labelRes: Int, hasError: Boolean) {
        val ctx = requireContext()
        val label = getString(labelRes)
        val star = " *"
        val span = SpannableStringBuilder(label).apply {
            val start = length
            append(star)
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.pref_odeio)),
                start,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        view.text = span
        view.setTextColor(
            ContextCompat.getColor(ctx, if (hasError) R.color.pref_odeio_texto else R.color.tinta),
        )
    }

    private fun styleChip(
        chip: Chip,
        selected: Boolean,
        sectionHasError: Boolean,
        tone: ChipTone = ChipTone.DEFAULT,
    ) {
        val ctx = requireContext()
        val nightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val bgColor: Int
        val strokeColor: Int
        val textColor: Int

        if (selected) {
            when (tone) {
                ChipTone.DEFAULT -> {
                    bgColor = ContextCompat.getColor(ctx, R.color.nevoa)
                    strokeColor = ContextCompat.getColor(ctx, R.color.verde_casa)
                    textColor = ContextCompat.getColor(ctx, R.color.verde_fundo)
                }

                ChipTone.EFFORT_MEDIA -> {
                    bgColor = ContextCompat.getColor(ctx, R.color.ambar_vez_suave)
                    strokeColor = ContextCompat.getColor(ctx, R.color.ambar_traco)
                    textColor = ContextCompat.getColor(ctx, R.color.ambar_vez_texto)
                }

                ChipTone.EFFORT_PESADA -> {
                    bgColor = ColorUtils.setAlphaComponent(
                        ContextCompat.getColor(ctx, R.color.pref_odeio),
                        if (nightMode) 56 else 31,
                    )
                    strokeColor = ContextCompat.getColor(ctx, R.color.pref_odeio)
                    textColor = ContextCompat.getColor(ctx, R.color.pref_odeio_texto)
                }
            }
        } else {
            bgColor = ContextCompat.getColor(ctx, R.color.superficie)
            strokeColor = ContextCompat.getColor(
                ctx,
                if (sectionHasError) R.color.pref_odeio else R.color.linha,
            )
            textColor = ContextCompat.getColor(ctx, R.color.tinta_suave)
        }

        chip.chipBackgroundColor = ColorStateList.valueOf(bgColor)
        chip.chipStrokeColor = ColorStateList.valueOf(strokeColor)
        chip.setTextColor(textColor)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INITIALIZED, initializedFromState)
        outState.putBoolean(STATE_IS_ADMIN, draft.isAdmin)
        outState.putString(STATE_TITLE, draft.title)
        outState.putString(STATE_CATEGORY, draft.category?.name)
        draft.effortPoints?.let { outState.putInt(STATE_EFFORT, it) }
        outState.putString(STATE_RECURRENCE, draft.recurrence?.name)
        outState.putString(STATE_FREQUENCY, draft.frequency?.name)
        if (draft.openInCurrentWeek != null) {
            outState.putBoolean(STATE_HAS_OPEN_NOW, true)
            outState.putBoolean(STATE_OPEN_NOW, draft.openInCurrentWeek == true)
        }
        outState.putString(STATE_DEADLINE, draft.deadline?.name)

        outState.putBoolean(STATE_ERR_TITLE, errors.titleRequired)
        outState.putBoolean(STATE_ERR_CATEGORY, errors.categoryRequired)
        outState.putBoolean(STATE_ERR_EFFORT, errors.effortRequired)
        outState.putBoolean(STATE_ERR_RECURRENCE, errors.recurrenceRequired)
        outState.putBoolean(STATE_ERR_FREQUENCY, errors.frequencyRequired)
        outState.putBoolean(STATE_ERR_OPEN_NOW, errors.openNowRequired)
        outState.putBoolean(STATE_ERR_DEADLINE, errors.deadlineRequired)
        outState.putBoolean(STATE_CONFIRM_DELETE, confirmDeleteOpen)
    }

    private fun restoreStateIfPresent(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        initializedFromState = savedInstanceState.getBoolean(STATE_INITIALIZED, false)
        if (!initializedFromState) return

        val restoredCategory = savedInstanceState.getString(STATE_CATEGORY)
            ?.let { name -> TaskCategory.entries.firstOrNull { it.name == name } }
        val restoredEffort = if (savedInstanceState.containsKey(STATE_EFFORT)) {
            savedInstanceState.getInt(STATE_EFFORT)
        } else {
            null
        }
        val restoredRecurrence = savedInstanceState.getString(STATE_RECURRENCE)
            ?.let { name -> ActivityFormRecurrence.entries.firstOrNull { it.name == name } }
        val restoredFrequency = savedInstanceState.getString(STATE_FREQUENCY)
            ?.let { name -> TaskFrequency.entries.firstOrNull { it.name == name } }
        val restoredOpenNow = if (savedInstanceState.getBoolean(STATE_HAS_OPEN_NOW, false)) {
            savedInstanceState.getBoolean(STATE_OPEN_NOW)
        } else {
            null
        }
        val restoredDeadline = savedInstanceState.getString(STATE_DEADLINE)
            ?.let { name -> TaskDeadline.entries.firstOrNull { it.name == name } }

        draft = ActivityFormDraft(
            mode = formMode,
            isAdmin = savedInstanceState.getBoolean(STATE_IS_ADMIN, false),
            title = savedInstanceState.getString(STATE_TITLE, ""),
            category = restoredCategory,
            effortPoints = restoredEffort,
            recurrence = restoredRecurrence,
            frequency = restoredFrequency,
            openInCurrentWeek = restoredOpenNow,
            deadline = restoredDeadline,
        )

        errors = ActivityFormErrors(
            titleRequired = savedInstanceState.getBoolean(STATE_ERR_TITLE, false),
            categoryRequired = savedInstanceState.getBoolean(STATE_ERR_CATEGORY, false),
            effortRequired = savedInstanceState.getBoolean(STATE_ERR_EFFORT, false),
            recurrenceRequired = savedInstanceState.getBoolean(STATE_ERR_RECURRENCE, false),
            frequencyRequired = savedInstanceState.getBoolean(STATE_ERR_FREQUENCY, false),
            openNowRequired = savedInstanceState.getBoolean(STATE_ERR_OPEN_NOW, false),
            deadlineRequired = savedInstanceState.getBoolean(STATE_ERR_DEADLINE, false),
        )
        confirmDeleteOpen = savedInstanceState.getBoolean(STATE_CONFIRM_DELETE, false)
    }

    override fun onDestroyView() {
        deleteConfirmDialog?.setOnDismissListener(null)
        deleteConfirmDialog?.dismiss()
        deleteConfirmDialog = null
        super.onDestroyView()
        _binding = null
    }

    private enum class ChipTone {
        DEFAULT,
        EFFORT_MEDIA,
        EFFORT_PESADA,
    }

    companion object {
        const val TAG = "ActivityFormBottomSheet"

        private const val ARG_MODE = "arg_mode"
        private const val ARG_TARGET_ID = "arg_target_id"

        private const val STATE_INITIALIZED = "state_initialized"
        private const val STATE_IS_ADMIN = "state_is_admin"
        private const val STATE_TITLE = "state_title"
        private const val STATE_CATEGORY = "state_category"
        private const val STATE_EFFORT = "state_effort"
        private const val STATE_RECURRENCE = "state_recurrence"
        private const val STATE_FREQUENCY = "state_frequency"
        private const val STATE_HAS_OPEN_NOW = "state_has_open_now"
        private const val STATE_OPEN_NOW = "state_open_now"
        private const val STATE_DEADLINE = "state_deadline"
        private const val STATE_ERR_TITLE = "state_err_title"
        private const val STATE_ERR_CATEGORY = "state_err_category"
        private const val STATE_ERR_EFFORT = "state_err_effort"
        private const val STATE_ERR_RECURRENCE = "state_err_recurrence"
        private const val STATE_ERR_FREQUENCY = "state_err_frequency"
        private const val STATE_ERR_OPEN_NOW = "state_err_open_now"
        private const val STATE_ERR_DEADLINE = "state_err_deadline"
        private const val STATE_CONFIRM_DELETE = "state_confirm_delete"

        fun newCreateInstance(): ActivityFormBottomSheet = ActivityFormBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_MODE, ActivityFormMode.CREATE.name)
            }
        }

        fun newEditAssignmentInstance(assignmentId: String): ActivityFormBottomSheet =
            ActivityFormBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, ActivityFormMode.EDIT_ASSIGNMENT.name)
                    putString(ARG_TARGET_ID, assignmentId)
                }
            }

        fun newEditRecurringTaskInstance(taskId: String): ActivityFormBottomSheet =
            ActivityFormBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MODE, ActivityFormMode.EDIT_RECURRING.name)
                    putString(ARG_TARGET_ID, taskId)
                }
            }
    }
}
