package xavier.pedro.suavez.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.databinding.DialogDestroyHouseBinding
import xavier.pedro.suavez.databinding.DialogRenameHouseBinding
import xavier.pedro.suavez.databinding.DialogTransferAdminBinding
import xavier.pedro.suavez.databinding.FragmentProfileBinding
import xavier.pedro.suavez.databinding.ItemTransferAdminMemberBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.UiMember

class ProfileFragment : Fragment() {

    private enum class ActiveDialog {
        NONE,
        RENAME_HOUSE,
        TRANSFER_ADMIN,
        DESTROY_HOUSE,
        LEAVE_MEMBER,
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    private var latestModel: ProfileUiModel? = null
    private var activeDialog: AlertDialog? = null
    private var activeDialogType: ActiveDialog = ActiveDialog.NONE
    private var pendingRestoreDialog: ActiveDialog = ActiveDialog.NONE
    private var renameDraft: String? = null
    private var destroyDraft: String? = null
    private var selectedNewAdminUid: String? = null
    private var displayedTransferMemberUids: List<String>? = null
    private var displayedDestroyHouseName: String? = null

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

        if (savedInstanceState != null) {
            pendingRestoreDialog = savedInstanceState.getString(STATE_DIALOG)
                ?.let { name -> ActiveDialog.entries.firstOrNull { it.name == name } }
                ?: ActiveDialog.NONE
            renameDraft = savedInstanceState.getString(STATE_RENAME_DRAFT)
            destroyDraft = savedInstanceState.getString(STATE_DESTROY_DRAFT)
            selectedNewAdminUid = savedInstanceState.getString(STATE_NEW_ADMIN_UID)
        }

        binding.btnRenameHouse.setOnClickListener {
            val houseName = latestModel?.houseName ?: return@setOnClickListener
            renameDraft = houseName
            showRenameHouseDialog(houseName)
        }

        binding.btnLeaveHouse.setOnClickListener {
            val model = latestModel ?: return@setOnClickListener
            val houseName = model.houseName ?: return@setOnClickListener
            if (model.isAdmin) {
                selectedNewAdminUid = model.otherMembers.firstOrNull()?.uid
                showTransferAdminDialog(model.otherMembers)
            } else {
                showLeaveAsMemberDialog(houseName)
            }
        }

        binding.btnDestroyHouse.setOnClickListener {
            val houseName = latestModel?.houseName ?: return@setOnClickListener
            destroyDraft = ""
            showDestroyHouseDialog(houseName)
        }

        binding.btnSignOut.setOnClickListener {
            sharedViewModel.stateHolder.signOut()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState
                    .mapNotNull { it.toProfileUiModel() }
                    .distinctUntilChanged()
                    .collect(::renderProfileUi)
            }
        }
    }

    private fun renderProfileUi(model: ProfileUiModel) {
        latestModel = model

        binding.tvProfileAvatar.bindSuaVezAvatar(model.user.nome, model.user.uid)
        binding.tvProfileName.text = model.user.nome
        binding.tvProfileEmail.text = model.user.email
        binding.tvProfileEmail.isVisible = model.user.email.isNotBlank()
        binding.tvAdminBadge.isVisible = model.showAdminBadge

        binding.cardCurrentHouse.isVisible = model.hasHouse
        binding.tvHouseName.text = model.houseName.orEmpty()

        binding.btnRenameHouse.isVisible = model.hasHouse && model.isAdmin
        binding.btnLeaveHouse.isVisible = model.hasHouse
        binding.btnDestroyHouse.isVisible = model.hasHouse && model.isAdmin
        styleSignOutButton(hasHouse = model.hasHouse)

        val dialogInvalidForState = (!model.hasHouse && activeDialogType != ActiveDialog.NONE) ||
            (!model.isAdmin && activeDialogType in ADMIN_ONLY_DIALOGS) ||
            (model.isAdmin && activeDialogType == ActiveDialog.LEAVE_MEMBER)
        if (dialogInvalidForState) {
            dismissAndClearDialog()
        } else if (
            activeDialogType == ActiveDialog.TRANSFER_ADMIN &&
            model.otherMembers.map { it.uid } != displayedTransferMemberUids
        ) {
            showTransferAdminDialog(model.otherMembers)
        } else if (
            activeDialogType == ActiveDialog.DESTROY_HOUSE &&
            model.houseName != null &&
            model.houseName != displayedDestroyHouseName
        ) {
            showDestroyHouseDialog(model.houseName)
        }

        if (pendingRestoreDialog != ActiveDialog.NONE) {
            val toRestore = pendingRestoreDialog
            pendingRestoreDialog = ActiveDialog.NONE
            restoreDialogIfAllowed(toRestore, model)
        }
    }

    private fun restoreDialogIfAllowed(dialogType: ActiveDialog, model: ProfileUiModel) {
        val houseName = model.houseName ?: return
        when (dialogType) {
            ActiveDialog.RENAME_HOUSE -> if (model.isAdmin) showRenameHouseDialog(houseName)
            ActiveDialog.TRANSFER_ADMIN -> if (model.isAdmin) showTransferAdminDialog(model.otherMembers)
            ActiveDialog.DESTROY_HOUSE -> if (model.isAdmin) showDestroyHouseDialog(houseName)
            ActiveDialog.LEAVE_MEMBER -> if (!model.isAdmin) showLeaveAsMemberDialog(houseName)
            ActiveDialog.NONE -> Unit
        }
    }

    private fun styleSignOutButton(hasHouse: Boolean) {
        val ctx = requireContext()
        if (hasHouse) {
            binding.btnSignOut.strokeWidth = 0
            binding.btnSignOut.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            binding.btnSignOut.setTextColor(ContextCompat.getColor(ctx, R.color.tinta_suave))
        } else {
            binding.btnSignOut.strokeWidth = ctx.dp(1)
            binding.btnSignOut.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.linha))
            binding.btnSignOut.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.superficie))
            binding.btnSignOut.setTextColor(ContextCompat.getColor(ctx, R.color.tinta))
        }
    }

    private fun showRenameHouseDialog(currentHouseName: String) {
        val dialogBinding = DialogRenameHouseBinding.inflate(layoutInflater)
        val initialText = renameDraft ?: currentHouseName
        renameDraft = initialText
        dialogBinding.etRenameHouse.setText(initialText)
        dialogBinding.etRenameHouse.setSelection(initialText.length)
        dialogBinding.etRenameHouse.doAfterTextChanged {
            dialogBinding.tilRenameHouse.error = null
            renameDraft = it?.toString().orEmpty()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_rename_house_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.action_cancel) { _, _ -> clearDialogTracking() }
            .setOnDismissListener { clearDialogTracking() }
            .create()

        fun submitRename() {
            val text = dialogBinding.etRenameHouse.text?.toString().orEmpty()
            if (!isValidHouseRename(text)) {
                dialogBinding.tilRenameHouse.error = getString(R.string.error_invalid_rename_house)
                return
            }
            clearDialogTracking()
            sharedViewModel.stateHolder.renameHouse(text.trim())
            dialog.dismiss()
        }

        dialogBinding.etRenameHouse.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitRename()
                true
            } else {
                false
            }
        }

        showTrackedDialog(ActiveDialog.RENAME_HOUSE, dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { submitRename() }
    }

    private fun showTransferAdminDialog(otherMembers: List<UiMember>) {
        displayedTransferMemberUids = otherMembers.map { it.uid }
        val dialogBinding = DialogTransferAdminBinding.inflate(layoutInflater)
        if (otherMembers.isEmpty()) {
            dialogBinding.tvTransferMessage.setText(R.string.dialog_transfer_admin_only_member)
            dialogBinding.llTransferMembers.isVisible = false
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_transfer_admin_title)
                .setView(dialogBinding.root)
                .setNegativeButton(R.string.action_close) { _, _ -> clearDialogTracking() }
                .setOnDismissListener { clearDialogTracking() }
                .create()
            showTrackedDialog(ActiveDialog.TRANSFER_ADMIN, dialog)
            return
        }

        dialogBinding.tvTransferMessage.setText(R.string.dialog_transfer_admin_message)
        dialogBinding.llTransferMembers.isVisible = true
        var chosenUid = selectedNewAdminUid?.takeIf { uid -> otherMembers.any { it.uid == uid } }
            ?: otherMembers.first().uid
        selectedNewAdminUid = chosenUid

        val rowBindings = mutableListOf<Pair<UiMember, ItemTransferAdminMemberBinding>>()
        fun refreshMemberSelection() {
            val ctx = requireContext()
            val selectedBg = ContextCompat.getColor(ctx, R.color.nevoa)
            val normalBg = ContextCompat.getColor(ctx, R.color.parede)
            val selectedText = ContextCompat.getColor(ctx, R.color.verde_fundo)
            val normalText = ContextCompat.getColor(ctx, R.color.tinta)
            val cornerPx = ctx.dp(16).toFloat()

            rowBindings.forEach { (member, row) ->
                val isSelected = member.uid == chosenUid
                row.rowMemberRoot.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerPx
                    setColor(if (isSelected) selectedBg else normalBg)
                }
                row.tvMemberName.setTextColor(if (isSelected) selectedText else normalText)
                row.ivMemberSelected.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            }
        }

        otherMembers.forEach { member ->
            val rowBinding = ItemTransferAdminMemberBinding.inflate(
                layoutInflater,
                dialogBinding.llTransferMembers,
                false,
            )
            rowBinding.tvMemberAvatar.bindSuaVezAvatar(member.nome, member.uid)
            rowBinding.tvMemberName.text = member.nome
            rowBinding.rowMemberRoot.setOnClickListener {
                chosenUid = member.uid
                selectedNewAdminUid = chosenUid
                refreshMemberSelection()
            }
            rowBindings.add(member to rowBinding)
            dialogBinding.llTransferMembers.addView(rowBinding.root)
        }
        refreshMemberSelection()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_transfer_admin_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_transfer_and_leave) { _, _ ->
                clearDialogTracking()
                sharedViewModel.stateHolder.transferAdminAndLeave(chosenUid)
            }
            .setNegativeButton(R.string.action_close) { _, _ -> clearDialogTracking() }
            .setOnDismissListener { clearDialogTracking() }
            .create()

        showTrackedDialog(ActiveDialog.TRANSFER_ADMIN, dialog)
    }

    private fun showDestroyHouseDialog(houseName: String) {
        displayedDestroyHouseName = houseName
        val ctx = requireContext()
        val dialogBinding = DialogDestroyHouseBinding.inflate(layoutInflater)
        dialogBinding.tvDestroyMessage.text = getString(R.string.dialog_destroy_house_message, houseName)
        dialogBinding.etDestroyHouseName.hint = houseName

        val initialDraft = destroyDraft.orEmpty()
        destroyDraft = initialDraft
        dialogBinding.etDestroyHouseName.setText(initialDraft)
        dialogBinding.etDestroyHouseName.setSelection(initialDraft.length)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.dialog_destroy_house_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_confirm_destroy) { _, _ ->
                val typed = dialogBinding.etDestroyHouseName.text?.toString().orEmpty()
                if (isDestroyHouseConfirmed(typed, houseName)) {
                    clearDialogTracking()
                    sharedViewModel.stateHolder.destroyHouse()
                }
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> clearDialogTracking() }
            .setOnDismissListener { clearDialogTracking() }
            .create()

        showTrackedDialog(ActiveDialog.DESTROY_HOUSE, dialog)

        val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        confirmButton?.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(
                    ContextCompat.getColor(ctx, R.color.tinta_apagada),
                    ContextCompat.getColor(ctx, R.color.pref_odeio),
                ),
            ),
        )
        confirmButton?.isEnabled = isDestroyHouseConfirmed(initialDraft, houseName)

        dialogBinding.etDestroyHouseName.doAfterTextChanged { text ->
            val current = text?.toString().orEmpty()
            destroyDraft = current
            confirmButton?.isEnabled = isDestroyHouseConfirmed(current, houseName)
        }
        dialogBinding.etDestroyHouseName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val current = dialogBinding.etDestroyHouseName.text?.toString().orEmpty()
                if (isDestroyHouseConfirmed(current, houseName)) {
                    clearDialogTracking()
                    sharedViewModel.stateHolder.destroyHouse()
                    dialog.dismiss()
                }
                true
            } else {
                false
            }
        }
    }

    private fun showLeaveAsMemberDialog(houseName: String) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_leave_house_title)
            .setMessage(getString(R.string.dialog_leave_house_message, houseName))
            .setPositiveButton(R.string.action_leave_house) { _, _ ->
                clearDialogTracking()
                sharedViewModel.stateHolder.leaveHouseAsMember()
            }
            .setNegativeButton(R.string.action_cancel) { _, _ -> clearDialogTracking() }
            .setOnDismissListener { clearDialogTracking() }
            .create()

        showTrackedDialog(ActiveDialog.LEAVE_MEMBER, dialog)
    }

    private fun showTrackedDialog(type: ActiveDialog, dialog: AlertDialog) {
        dismissExistingDialog()
        activeDialogType = type
        activeDialog = dialog
        dialog.show()
    }

    private fun dismissExistingDialog() {
        activeDialog?.setOnDismissListener(null)
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun dismissAndClearDialog() {
        dismissExistingDialog()
        clearDialogTracking()
    }

    private fun clearDialogTracking() {
        activeDialogType = ActiveDialog.NONE
        activeDialog = null
        renameDraft = null
        destroyDraft = null
        selectedNewAdminUid = null
        displayedTransferMemberUids = null
        displayedDestroyHouseName = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DIALOG, activeDialogType.name)
        outState.putString(STATE_RENAME_DRAFT, renameDraft)
        outState.putString(STATE_DESTROY_DRAFT, destroyDraft)
        outState.putString(STATE_NEW_ADMIN_UID, selectedNewAdminUid)
    }

    override fun onDestroyView() {
        dismissExistingDialog()
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val ADMIN_ONLY_DIALOGS = setOf(
            ActiveDialog.RENAME_HOUSE,
            ActiveDialog.TRANSFER_ADMIN,
            ActiveDialog.DESTROY_HOUSE,
        )
        const val STATE_DIALOG = "state_dialog"
        const val STATE_RENAME_DRAFT = "state_rename_draft"
        const val STATE_DESTROY_DRAFT = "state_destroy_draft"
        const val STATE_NEW_ADMIN_UID = "state_new_admin_uid"
    }
}
