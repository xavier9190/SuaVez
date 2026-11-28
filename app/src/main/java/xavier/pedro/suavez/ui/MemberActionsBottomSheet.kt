package xavier.pedro.suavez.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import xavier.pedro.suavez.R
import xavier.pedro.suavez.data.model.HouseRole
import xavier.pedro.suavez.databinding.BottomSheetMemberActionsBinding
import xavier.pedro.suavez.ui.state.SuaVezSharedViewModel
import xavier.pedro.suavez.ui.state.UiMember

class MemberActionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMemberActionsBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SuaVezSharedViewModel by activityViewModels {
        SuaVezSharedViewModel.Factory
    }

    private val memberUid: String? by lazy {
        arguments?.getString(ARG_MEMBER_UID)
    }

    private var currentMember: UiMember? = null
    private var confirmDialog: AlertDialog? = null
    private var pendingConfirmAction: ConfirmAction = ConfirmAction.NONE

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
        _binding = BottomSheetMemberActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val savedAction = savedInstanceState?.getString(STATE_CONFIRM_ACTION)
        pendingConfirmAction = ConfirmAction.entries.firstOrNull { it.name == savedAction }
            ?: ConfirmAction.NONE

        binding.btnMakeAdmin.setOnClickListener {
            showConfirmDialog(ConfirmAction.PROMOTE)
        }
        binding.btnRemoveMember.setOnClickListener {
            showConfirmDialog(ConfirmAction.REMOVE)
        }
        binding.btnCancelMemberActions.setOnClickListener {
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.uiState.collect { state ->
                    val target = state.members.firstOrNull { it.uid == memberUid }
                    if (state.currentHouse == null ||
                        !state.isAdmin ||
                        target == null ||
                        target.role == HouseRole.ADMIN
                    ) {
                        dismissAllowingStateLoss()
                        return@collect
                    }

                    currentMember = target
                    binding.tvMemberAvatar.bindSuaVezAvatar(target.nome, target.uid)
                    binding.tvMemberName.text = target.nome

                    if (pendingConfirmAction != ConfirmAction.NONE && confirmDialog == null) {
                        showConfirmDialog(pendingConfirmAction)
                    }
                }
            }
        }
    }

    private fun showConfirmDialog(action: ConfirmAction) {
        val member = currentMember ?: return
        pendingConfirmAction = action
        confirmDialog?.setOnDismissListener(null)
        confirmDialog?.dismiss()

        val (title, message, positiveRes) = when (action) {
            ConfirmAction.PROMOTE -> Triple(
                getString(R.string.dialog_promote_admin_title),
                getString(R.string.dialog_promote_admin_message, member.nome),
                R.string.action_make_admin,
            )
            ConfirmAction.REMOVE -> Triple(
                getString(R.string.dialog_remove_member_title, member.nome),
                getString(R.string.dialog_remove_member_message),
                R.string.action_confirm_remove,
            )
            ConfirmAction.NONE -> return
        }

        confirmDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel) { _, _ ->
                pendingConfirmAction = ConfirmAction.NONE
            }
            .setPositiveButton(positiveRes) { _, _ ->
                pendingConfirmAction = ConfirmAction.NONE
                when (action) {
                    ConfirmAction.PROMOTE -> sharedViewModel.stateHolder.promoteToAdmin(member.uid)
                    ConfirmAction.REMOVE -> sharedViewModel.stateHolder.removeMember(member.uid)
                    ConfirmAction.NONE -> Unit
                }
                dismiss()
            }
            .setOnDismissListener {
                pendingConfirmAction = ConfirmAction.NONE
                confirmDialog = null
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CONFIRM_ACTION, pendingConfirmAction.name)
    }

    override fun onDestroyView() {
        confirmDialog?.setOnDismissListener(null)
        confirmDialog?.dismiss()
        confirmDialog = null
        super.onDestroyView()
        _binding = null
    }

    private enum class ConfirmAction {
        NONE,
        PROMOTE,
        REMOVE,
    }

    companion object {
        const val TAG = "MemberActionsBottomSheet"
        private const val ARG_MEMBER_UID = "arg_member_uid"
        private const val STATE_CONFIRM_ACTION = "state_confirm_action"

        fun newInstance(memberUid: String): MemberActionsBottomSheet =
            MemberActionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEMBER_UID, memberUid)
                }
            }
    }
}
