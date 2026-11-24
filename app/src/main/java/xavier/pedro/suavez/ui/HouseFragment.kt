package xavier.pedro.suavez.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.databinding.FragmentHouseBinding
import xavier.pedro.suavez.databinding.ItemHouseMemberBinding
import xavier.pedro.suavez.databinding.ItemHouseRankRowBinding
import xavier.pedro.suavez.databinding.ItemHouseRecurringTaskBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.UiRecurringTask

class HouseFragment : Fragment() {

    private var _binding: FragmentHouseBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    private var currentModel: HouseUiModel? = null
    private var isInviteCopied = false
    private var copyResetJob: Job? = null

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

        binding.btnCopyInvite.setOnClickListener {
            val code = currentModel?.inviteCode ?: return@setOnClickListener
            copyInviteCode(code)
        }
        binding.btnShareInvite.setOnClickListener {
            val model = currentModel ?: return@setOnClickListener
            shareInviteCode(model.houseName, model.inviteCode)
        }
        binding.btnAddRecurringTop.setOnClickListener {
            showSheet(ActivityFormBottomSheet.TAG) { ActivityFormBottomSheet.newCreateInstance() }
        }
        binding.btnCreateRecurringDashed.setOnClickListener {
            showSheet(ActivityFormBottomSheet.TAG) { ActivityFormBottomSheet.newCreateInstance() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState
                    .mapNotNull { it.toHouseUiModel() }
                    .distinctUntilChanged()
                    .collect(::renderHouseUi)
            }
        }
    }

    private fun renderHouseUi(model: HouseUiModel) {
        val previous = currentModel
        currentModel = model
        val ctx = requireContext()

        // 1. Cabeçalho
        binding.tvHouseName.text = model.houseName
        binding.tvHouseMeta.text = if (model.memberCount == 1) {
            getString(R.string.house_meta_singular, model.inviteCode)
        } else {
            getString(R.string.house_meta_plural, model.inviteCode, model.memberCount)
        }

        // 2. Card Código de convite (somente Admin)
        binding.cardInvite.isVisible = model.isAdmin
        if (model.isAdmin) {
            binding.tvInviteCode.text = model.inviteCode
            ViewCompat.setBackgroundTintList(
                binding.tvInviteActiveChip,
                ColorStateList.valueOf(okChipBgColor()),
            )
            binding.btnCopyInvite.setText(
                if (isInviteCopied) R.string.house_invite_copied else R.string.house_invite_copy,
            )
        }

        // 3. Pontuação da semana
        if (previous?.rankedMembers != model.rankedMembers) {
            renderRankings(model.rankedMembers)
        }
        if (model.isBalanced) {
            binding.tvBalanceChip.setText(R.string.house_balanced)
            ViewCompat.setBackgroundTintList(
                binding.tvBalanceChip,
                ColorStateList.valueOf(okChipBgColor()),
            )
            binding.tvBalanceChip.setTextColor(ContextCompat.getColor(ctx, R.color.pref_gosto))
        } else {
            binding.tvBalanceChip.text = getString(R.string.house_balance_gap, model.balanceGapPercent)
            ViewCompat.setBackgroundTintList(
                binding.tvBalanceChip,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.ambar_vez_suave)),
            )
            binding.tvBalanceChip.setTextColor(ContextCompat.getColor(ctx, R.color.ambar_vez_texto))
        }
        binding.tvHouseDoneSummary.text = getString(
            R.string.house_done_summary,
            model.houseDoneCount,
            model.houseTotalCount,
        )

        // 4. Moradores
        binding.tvMembersCountChip.text = if (model.memberCount == 1) {
            getString(R.string.house_members_count_singular)
        } else {
            getString(R.string.house_members_count_plural, model.memberCount)
        }
        if (previous?.members != model.members) {
            renderMembers(model.members)
        }

        // 5. Tarefas recorrentes da casa
        binding.btnAddRecurringTop.isVisible = model.isAdmin
        binding.btnCreateRecurringDashed.isVisible = model.isAdmin
        binding.tvWeeklyQuotaBanner.text = buildWeeklyQuotaBanner(model.weeklyQuotaPerMember)
        if (previous?.recurringTasks != model.recurringTasks || previous.isAdmin != model.isAdmin) {
            renderRecurringTasks(model.recurringTasks, model.isAdmin)
        }
    }

    private fun renderRankings(rankedMembers: List<HouseRankItemUiModel>) {
        val ctx = requireContext()
        binding.llRankedMembers.removeAllViews()
        val rowGapPx = ctx.dp(18)

        rankedMembers.forEachIndexed { index, item ->
            val rowBinding = ItemHouseRankRowBinding.inflate(
                layoutInflater,
                binding.llRankedMembers,
                false,
            )
            val params = rowBinding.root.layoutParams as LinearLayout.LayoutParams
            params.bottomMargin = if (index < rankedMembers.lastIndex) rowGapPx else 0
            rowBinding.root.layoutParams = params

            rowBinding.tvAvatar.bindSuaVezAvatar(item.member.nome, item.member.uid)

            if (item.isMe) {
                val pad = ctx.dp(3)
                rowBinding.flAvatarRing.setPadding(pad, pad, pad, pad)
                rowBinding.flAvatarRing.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(ctx.dp(2).coerceAtLeast(1), ContextCompat.getColor(ctx, R.color.ambar_vez))
                }
            } else {
                rowBinding.flAvatarRing.setPadding(0, 0, 0, 0)
                rowBinding.flAvatarRing.background = null
            }

            rowBinding.flLeaderBadge.isVisible = item.isLeader
            if (item.isLeader) {
                rowBinding.flLeaderBadge.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(ctx, R.color.ambar_vez))
                    setStroke(ctx.dp(2).coerceAtLeast(1), ContextCompat.getColor(ctx, R.color.superficie))
                }
            }

            rowBinding.tvRankName.text = SpannableStringBuilder().apply {
                val nameStart = length
                append(item.member.nome)
                setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.tinta)),
                    nameStart,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    StyleSpan(if (item.isMe) Typeface.BOLD else Typeface.NORMAL),
                    nameStart,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                if (item.isMe) {
                    val suffixStart = length
                    append(getString(R.string.house_rank_me_suffix))
                    setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.tinta_suave)),
                        suffixStart,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }

            rowBinding.tvRankPoints.text = getString(R.string.house_rank_points, item.points)
            rowBinding.tvRankPoints.setTextColor(
                ContextCompat.getColor(ctx, if (item.isMe) R.color.ambar_vez_texto else R.color.verde_casa),
            )
            rowBinding.barRankProgress.setProgress(
                done = item.progressFraction,
                useAmberGradient = item.isMe,
            )

            binding.llRankedMembers.addView(rowBinding.root)
        }
    }

    private fun renderMembers(members: List<HouseMemberItemUiModel>) {
        val ctx = requireContext()
        binding.llMembers.removeAllViews()

        members.forEach { item ->
            val cardBinding = ItemHouseMemberBinding.inflate(
                layoutInflater,
                binding.llMembers,
                false,
            )
            cardBinding.tvMemberAvatar.bindSuaVezAvatar(item.member.nome, item.member.uid)
            cardBinding.tvMemberName.text = item.member.nome

            when (item.roleBadge) {
                HouseMemberRoleBadge.ADMIN -> {
                    cardBinding.tvMemberRoleChip.isVisible = true
                    cardBinding.tvMemberRoleChip.setText(R.string.house_member_role_admin)
                    cardBinding.tvMemberRoleChip.setBackgroundResource(R.drawable.bg_chip_admin)
                    ViewCompat.setBackgroundTintList(cardBinding.tvMemberRoleChip, null)
                    cardBinding.tvMemberRoleChip.setTextColor(
                        ContextCompat.getColor(ctx, R.color.verde_fundo),
                    )
                }
                HouseMemberRoleBadge.NEXT_IN_TURN -> {
                    cardBinding.tvMemberRoleChip.isVisible = true
                    cardBinding.tvMemberRoleChip.setText(R.string.house_member_role_next)
                    cardBinding.tvMemberRoleChip.setBackgroundResource(R.drawable.bg_pill_shape)
                    ViewCompat.setBackgroundTintList(
                        cardBinding.tvMemberRoleChip,
                        ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.ambar_vez_suave)),
                    )
                    cardBinding.tvMemberRoleChip.setTextColor(
                        ContextCompat.getColor(ctx, R.color.ambar_vez_texto),
                    )
                }
                HouseMemberRoleBadge.NONE -> {
                    cardBinding.tvMemberRoleChip.isVisible = false
                }
            }

            cardBinding.tvMemberOpenMeta.text = when (item.openCount) {
                0 -> getString(R.string.house_member_open_zero)
                1 -> getString(R.string.house_member_open_singular, item.openPoints)
                else -> getString(R.string.house_member_open_plural, item.openCount, item.openPoints)
            }

            cardBinding.btnManageMember.isVisible = item.canManage
            if (item.canManage) {
                cardBinding.btnManageMember.contentDescription =
                    getString(R.string.cd_manage_member, item.member.nome)
                cardBinding.btnManageMember.setOnClickListener {
                    showSheet(MemberActionsBottomSheet.TAG) {
                        MemberActionsBottomSheet.newInstance(item.member.uid)
                    }
                }
            } else {
                cardBinding.btnManageMember.setOnClickListener(null)
            }

            binding.llMembers.addView(cardBinding.root)
        }
    }

    private fun renderRecurringTasks(tasks: List<UiRecurringTask>, isAdmin: Boolean) {
        val ctx = requireContext()
        binding.llRecurringTasks.removeAllViews()
        val endPadPx = ctx.dp(if (isAdmin) 8 else 16)

        tasks.forEach { task ->
            val cardBinding = ItemHouseRecurringTaskBinding.inflate(
                layoutInflater,
                binding.llRecurringTasks,
                false,
            )
            cardBinding.llRecurringRow.setPadding(
                ctx.dp(16),
                ctx.dp(14),
                endPadPx,
                ctx.dp(14),
            )

            val style = ctx.effortVisualStyle(task.effortLevel)

            ViewCompat.setBackgroundTintList(
                cardBinding.flTaskIconCircle,
                ColorStateList.valueOf(style.bg),
            )
            cardBinding.ivTaskIcon.setImageResource(task.iconHint.iconRes())
            cardBinding.ivTaskIcon.imageTintList = ColorStateList.valueOf(style.iconTint)

            cardBinding.tvTaskTitle.text = task.titulo

            ViewCompat.setBackgroundTintList(
                cardBinding.viewEffortDot,
                ColorStateList.valueOf(style.dotColor),
            )
            cardBinding.tvTaskMeta.text = getString(
                R.string.house_recurring_meta,
                getString(style.labelRes),
                task.frequencia.label,
            )

            ViewCompat.setBackgroundTintList(
                cardBinding.tvEffortPill,
                ColorStateList.valueOf(style.bg),
            )
            cardBinding.tvEffortPill.setTextColor(style.pillText)
            cardBinding.tvEffortPill.text = if (task.pontos == 1) {
                getString(R.string.tasks_effort_pt_singular, task.pontos)
            } else {
                getString(R.string.tasks_effort_pts_plural, task.pontos)
            }

            cardBinding.btnEditRecurring.isVisible = isAdmin
            if (isAdmin) {
                cardBinding.btnEditRecurring.contentDescription =
                    getString(R.string.cd_edit_recurring_task, task.titulo)
                cardBinding.btnEditRecurring.setOnClickListener {
                    showSheet(ActivityFormBottomSheet.TAG) {
                        ActivityFormBottomSheet.newEditRecurringTaskInstance(task.id)
                    }
                }
            } else {
                cardBinding.btnEditRecurring.setOnClickListener(null)
            }

            binding.llRecurringTasks.addView(cardBinding.root)
        }
    }

    private fun buildWeeklyQuotaBanner(weeklyQuota: Int): CharSequence {
        val prefix = getString(R.string.house_weekly_quota_prefix)
        val value = getString(R.string.house_weekly_quota_value, weeklyQuota)
        return SpannableStringBuilder(prefix).apply {
            val start = length
            append(value)
            setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun copyInviteCode(code: String) {
        val clipboard = ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.house_invite_clip_label), code),
        )
        isInviteCopied = true
        binding.btnCopyInvite.setText(R.string.house_invite_copied)

        copyResetJob?.cancel()
        copyResetJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(2000)
            isInviteCopied = false
            _binding?.btnCopyInvite?.setText(R.string.house_invite_copy)
        }
    }

    private fun shareInviteCode(houseName: String, code: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                getString(R.string.house_invite_share_text, houseName, code),
            )
        }
        try {
            startActivity(
                Intent.createChooser(shareIntent, getString(R.string.house_invite_share_chooser)),
            )
        } catch (_: ActivityNotFoundException) {
            // Sem aplicativo compatível para compartilhamento no dispositivo.
        }
    }

    private inline fun showSheet(tag: String, factory: () -> DialogFragment) {
        if (childFragmentManager.findFragmentByTag(tag) == null && !childFragmentManager.isStateSaved) {
            factory().show(childFragmentManager, tag)
        }
    }

    private fun okChipBgColor(): Int {
        val ctx = requireContext()
        return ColorUtils.setAlphaComponent(
            ContextCompat.getColor(ctx, R.color.pref_gosto),
            if (ctx.isNightMode()) 56 else 38,
        )
    }

    override fun onDestroyView() {
        copyResetJob?.cancel()
        copyResetJob = null
        isInviteCopied = false
        currentModel = null
        super.onDestroyView()
        _binding = null
    }
}
