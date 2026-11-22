package xavier.pedro.suavez.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.databinding.FragmentTasksBinding
import xavier.pedro.suavez.databinding.ItemOpenTaskPreviewBinding
import xavier.pedro.suavez.databinding.ItemTaskCardBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.TaskIconHint
import xavier.pedro.suavez.ui.state.avatarIndexFor
import xavier.pedro.suavez.ui.state.initialsOf
import java.util.Locale
import kotlin.math.roundToInt

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddActivityTop.setOnClickListener { openCreateActivitySheet() }
        binding.btnCreateActivityDashed.setOnClickListener { openCreateActivitySheet() }
        binding.btnEmptyOpenTasks.setOnClickListener { openOpenTasks() }
        binding.btnViewOpenTasks.setOnClickListener { openOpenTasks() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    val model = state.toTasksUiModel() ?: return@collect
                    renderTasksUi(model)
                }
            }
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

    private fun openOpenTasks() {
        sharedViewModel.stateHolder.notifyMessage(getString(R.string.open_tasks_coming_soon))
    }

    private fun renderTasksUi(model: TasksUiModel) {
        val ctx = requireContext()

        // 1. Saudação
        binding.tvGreeting.text = getString(R.string.tasks_greeting, model.firstName)
        binding.tvWeekMeta.text = if (model.memberCount == 1) {
            getString(R.string.tasks_week_meta_singular, model.weekNumber)
        } else {
            getString(R.string.tasks_week_meta_plural, model.weekNumber, model.memberCount)
        }

        // 2. Sua cota da semana
        if (model.isQuotaCompleted) {
            binding.tvQuotaStatusChip.text = getString(R.string.tasks_quota_completed)
            ViewCompat.setBackgroundTintList(
                binding.tvQuotaStatusChip,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.nevoa)),
            )
            binding.tvQuotaStatusChip.setTextColor(ContextCompat.getColor(ctx, R.color.verde_fundo))
        } else {
            binding.tvQuotaStatusChip.text = getString(R.string.tasks_quota_pending_pts, model.pendingPoints)
            ViewCompat.setBackgroundTintList(
                binding.tvQuotaStatusChip,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.ambar_vez_suave)),
            )
            binding.tvQuotaStatusChip.setTextColor(ContextCompat.getColor(ctx, R.color.ambar_vez_texto))
        }

        val doneStr = model.donePoints.toString()
        val suffixStr = getString(R.string.tasks_quota_points_suffix, model.totalPoints)
        val quotaSpan = SpannableStringBuilder(doneStr).apply {
            setSpan(AbsoluteSizeSpan(40, true), 0, doneStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), 0, doneStr.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.verde_casa)),
                0,
                doneStr.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            val suffixStart = length
            append(suffixStr)
            setSpan(AbsoluteSizeSpan(16, true), suffixStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.tinta_suave)),
                suffixStart,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        binding.tvQuotaPoints.text = quotaSpan
        binding.quotaProgressBar.setProgress(model.doneFraction, model.pendingFraction)

        // 3. Suas tarefas da semana
        binding.tvPendingCountChip.text = if (model.pendingCount == 1) {
            getString(R.string.tasks_pending_one)
        } else {
            getString(R.string.tasks_pending_many, model.pendingCount)
        }

        if (model.myTasks.isEmpty()) {
            binding.cardEmptyTasks.isVisible = true
            binding.llMyTasksContainer.isVisible = false
            binding.llMyTasksContainer.removeAllViews()
        } else {
            binding.cardEmptyTasks.isVisible = false
            binding.llMyTasksContainer.isVisible = true
            binding.llMyTasksContainer.removeAllViews()
            model.myTasks.forEach { item ->
                val cardBinding = ItemTaskCardBinding.inflate(
                    layoutInflater,
                    binding.llMyTasksContainer,
                    false,
                )
                bindTaskCard(cardBinding, item)
                binding.llMyTasksContainer.addView(cardBinding.root)
            }
        }

        // 4. Ritmo da casa + prévia de Tarefas abertas
        binding.tvHouseRhythmSubtitle.text = getString(R.string.tasks_rhythm_subtitle, model.houseName)
        binding.tvHousePct.text = getString(R.string.tasks_rhythm_pct, model.houseCompletionPercent)
        binding.circularHouseProgress.progress = (model.houseProgressFraction * 100f)
            .roundToInt()
            .coerceIn(0, 100)
        binding.tvHouseDoneSummary.text = getString(
            R.string.tasks_rhythm_done_summary,
            model.houseDoneCount,
            model.houseTotalCount,
        )
        binding.tvHouseRemaining.text = if (model.houseRemainingCount > 0) {
            getString(R.string.tasks_rhythm_remaining, model.houseRemainingCount)
        } else {
            getString(R.string.tasks_rhythm_all_done)
        }

        renderMemberAvatars(model.visibleAvatars)

        binding.tvOpenTasksCountChip.text = String.format(Locale.getDefault(), "%d", model.openTasksCount)
        if (model.openPreviewTasks.isEmpty()) {
            binding.tvOpenTasksEmpty.isVisible = true
            binding.llOpenTasksPreview.isVisible = false
            binding.llOpenTasksPreview.removeAllViews()
            binding.tvOpenTasksExtra.isVisible = false
        } else {
            binding.tvOpenTasksEmpty.isVisible = false
            binding.llOpenTasksPreview.isVisible = true
            binding.llOpenTasksPreview.removeAllViews()
            model.openPreviewTasks.forEachIndexed { index, openTask ->
                val rowBinding = ItemOpenTaskPreviewBinding.inflate(
                    layoutInflater,
                    binding.llOpenTasksPreview,
                    false,
                )
                rowBinding.dividerOpenPreview.isVisible = index > 0
                val effortStyle = effortColors(openTask.effortLevel)
                ViewCompat.setBackgroundTintList(
                    rowBinding.flOpenIconCircle,
                    ColorStateList.valueOf(effortStyle.bg),
                )
                rowBinding.ivOpenTaskIcon.setImageResource(iconResFor(openTask.iconHint))
                rowBinding.ivOpenTaskIcon.imageTintList = ColorStateList.valueOf(effortStyle.iconTint)
                rowBinding.tvOpenTaskTitle.text = openTask.titulo
                ViewCompat.setBackgroundTintList(
                    rowBinding.tvOpenTaskEffort,
                    ColorStateList.valueOf(effortStyle.bg),
                )
                rowBinding.tvOpenTaskEffort.setTextColor(effortStyle.pillText)
                rowBinding.tvOpenTaskEffort.text = formatPoints(openTask.pontos)
                binding.llOpenTasksPreview.addView(rowBinding.root)
            }
            binding.tvOpenTasksExtra.isVisible = model.extraOpenTasksCount > 0
            if (model.extraOpenTasksCount > 0) {
                binding.tvOpenTasksExtra.text = if (model.extraOpenTasksCount == 1) {
                    getString(R.string.tasks_open_preview_extra_one)
                } else {
                    getString(R.string.tasks_open_preview_extra_many, model.extraOpenTasksCount)
                }
            }
        }
    }

    private fun bindTaskCard(cardBinding: ItemTaskCardBinding, item: TaskItemUiModel) {
        val ctx = requireContext()
        val task = item.assignment
        val showSuaVez = item.isSuaVez && !task.isDone
        val nightMode = isNightMode()

        cardBinding.viewSuaVezAccent.isVisible = showSuaVez
        cardBinding.tvSuaVezBadge.isVisible = showSuaVez
        if (showSuaVez) {
            val amberStart = ColorUtils.setAlphaComponent(
                ContextCompat.getColor(ctx, R.color.ambar_vez),
                if (nightMode) 41 else 28,
            )
            val surface = ContextCompat.getColor(ctx, R.color.superficie)
            cardBinding.flTaskContent.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(amberStart, surface, surface),
            )
        } else {
            cardBinding.flTaskContent.background = null
        }

        cardBinding.viewCheckboxBox.setBackgroundResource(
            if (task.isDone) R.drawable.bg_checkbox_checked else R.drawable.bg_checkbox_unchecked,
        )
        cardBinding.ivCheckboxCheck.isVisible = task.isDone
        cardBinding.ivCheckboxCheck.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(ctx, if (nightMode) R.color.parede else R.color.white),
        )
        cardBinding.btnTaskCheckbox.contentDescription = getString(R.string.cd_toggle_task, task.titulo)

        val effortStyle = effortColors(task.effortLevel)
        ViewCompat.setBackgroundTintList(
            cardBinding.flTaskIconCircle,
            ColorStateList.valueOf(effortStyle.bg),
        )
        cardBinding.ivTaskIcon.setImageResource(iconResFor(task.iconHint))
        cardBinding.ivTaskIcon.imageTintList = ColorStateList.valueOf(effortStyle.iconTint)
        cardBinding.flTaskIconCircle.alpha = if (task.isDone) 0.55f else 1f

        cardBinding.tvTaskTitle.text = task.titulo
        cardBinding.tvTaskTitle.paintFlags = if (task.isDone) {
            cardBinding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            cardBinding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        cardBinding.tvTaskTitle.setTextColor(
            ContextCompat.getColor(ctx, if (task.isDone) R.color.tinta_apagada else R.color.tinta),
        )
        cardBinding.tvTaskMeta.text = task.metaLabel()

        ViewCompat.setBackgroundTintList(
            cardBinding.tvEffortPill,
            ColorStateList.valueOf(effortStyle.bg),
        )
        cardBinding.tvEffortPill.setTextColor(effortStyle.pillText)
        cardBinding.tvEffortPill.text = formatPoints(task.pontos)
        cardBinding.tvEffortPill.alpha = if (task.isDone) 0.55f else 1f

        val onToggle = View.OnClickListener {
            sharedViewModel.stateHolder.toggleTaskDone(task.id)
        }
        cardBinding.cardTaskRoot.setOnClickListener(onToggle)
        cardBinding.btnTaskCheckbox.setOnClickListener(onToggle)
    }

    private fun renderMemberAvatars(members: List<xavier.pedro.suavez.ui.state.UiMember>) {
        val ctx = requireContext()
        binding.llMemberAvatars.removeAllViews()
        val sizePx = dp(32)
        val overlapPx = dp(8)
        val borderPx = dp(2)
        val borderColor = ContextCompat.getColor(ctx, R.color.superficie)
        val textColor = ContextCompat.getColor(ctx, R.color.verde_fundo)
        val typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        members.forEachIndexed { index, member ->
            val avatarView = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    if (index > 0) marginStart = -overlapPx
                }
                gravity = Gravity.CENTER
                text = initialsOf(member.nome)
                textSize = 12f
                this.typeface = typeface
                setTextColor(textColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(ctx, AVATAR_COLORS[avatarIndexFor(member.uid)]))
                    setStroke(borderPx, borderColor)
                }
            }
            binding.llMemberAvatars.addView(avatarView)
        }
    }

    private fun formatPoints(points: Int): String = if (points == 1) {
        getString(R.string.tasks_effort_pt_singular, points)
    } else {
        getString(R.string.tasks_effort_pts_plural, points)
    }

    private fun effortColors(level: TaskWeightLevel): EffortVisualStyle {
        val ctx = requireContext()
        return when (level) {
            TaskWeightLevel.LIGHT -> EffortVisualStyle(
                bg = ContextCompat.getColor(ctx, R.color.nevoa),
                iconTint = ContextCompat.getColor(ctx, R.color.verde_casa),
                pillText = ContextCompat.getColor(ctx, R.color.verde_fundo),
            )
            TaskWeightLevel.MEDIUM -> EffortVisualStyle(
                bg = ContextCompat.getColor(ctx, R.color.ambar_vez_suave),
                iconTint = ContextCompat.getColor(ctx, R.color.ambar_traco),
                pillText = ContextCompat.getColor(ctx, R.color.ambar_vez_texto),
            )
            TaskWeightLevel.HEAVY -> EffortVisualStyle(
                bg = ColorUtils.setAlphaComponent(
                    ContextCompat.getColor(ctx, R.color.pref_odeio),
                    if (isNightMode()) 56 else 31,
                ),
                iconTint = ContextCompat.getColor(ctx, R.color.pref_odeio),
                pillText = ContextCompat.getColor(ctx, R.color.pref_odeio_texto),
            )
        }
    }

    private fun iconResFor(hint: TaskIconHint): Int = when (hint) {
        TaskIconHint.DROP -> R.drawable.ic_drop
        TaskIconHint.TRASH -> R.drawable.ic_trash
        TaskIconHint.CART -> R.drawable.ic_cart
        TaskIconHint.RECEIPT -> R.drawable.ic_receipt
        TaskIconHint.FRIDGE -> R.drawable.ic_fridge
        TaskIconHint.LEAF -> R.drawable.ic_leaf
        TaskIconHint.CALENDAR -> R.drawable.ic_calendar
    }

    private fun isNightMode(): Boolean {
        val mask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mask == Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class EffortVisualStyle(
        val bg: Int,
        val iconTint: Int,
        val pillText: Int,
    )

    companion object {
        private val AVATAR_COLORS = intArrayOf(
            R.color.avatar_1,
            R.color.avatar_2,
            R.color.avatar_3,
            R.color.avatar_4,
        )
    }
}
