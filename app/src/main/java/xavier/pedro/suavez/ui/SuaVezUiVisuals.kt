package xavier.pedro.suavez.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import coil.dispose
import coil.load
import coil.transform.CircleCropTransformation
import xavier.pedro.suavez.R
import xavier.pedro.suavez.data.model.TaskWeightLevel
import xavier.pedro.suavez.ui.state.TaskIconHint
import xavier.pedro.suavez.ui.state.avatarIndexFor
import xavier.pedro.suavez.ui.state.initialsOf
import kotlin.math.roundToInt

internal val AVATAR_COLORS = intArrayOf(
    R.color.avatar_1,
    R.color.avatar_2,
    R.color.avatar_3,
    R.color.avatar_4,
)

internal data class EffortVisualStyle(
    val bg: Int,
    val iconTint: Int,
    val dotColor: Int,
    val pillText: Int,
    val labelRes: Int,
)

internal fun TextView.bindSuaVezAvatar(name: String, idForColor: String) {
    text = initialsOf(name)
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ContextCompat.getColor(context, AVATAR_COLORS[avatarIndexFor(idForColor)]))
    }
}

internal fun bindSuaVezAvatar(
    photoView: ImageView,
    initialsView: TextView,
    name: String,
    idForColor: String,
    fotoUrl: String?,
) {
    initialsView.bindSuaVezAvatar(name, idForColor)
    val cleanUrl = fotoUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanUrl == null) {
        photoView.tag = null
        photoView.dispose()
        photoView.setImageDrawable(null)
        photoView.isVisible = false
        return
    }
    if (photoView.tag == cleanUrl && photoView.drawable != null) {
        photoView.isVisible = true
        return
    }
    photoView.tag = cleanUrl
    photoView.isVisible = false
    photoView.load(cleanUrl) {
        transformations(CircleCropTransformation())
        listener(
            onSuccess = { _, _ -> photoView.isVisible = true },
            onError = { _, _ ->
                photoView.tag = null
                photoView.isVisible = false
            },
        )
    }
}

internal fun Context.effortVisualStyle(level: TaskWeightLevel): EffortVisualStyle = when (level) {
    TaskWeightLevel.LIGHT -> EffortVisualStyle(
        bg = ContextCompat.getColor(this, R.color.nevoa),
        iconTint = ContextCompat.getColor(this, R.color.verde_casa),
        dotColor = ContextCompat.getColor(this, R.color.verde_casa),
        pillText = ContextCompat.getColor(this, R.color.verde_fundo),
        labelRes = R.string.effort_light,
    )
    TaskWeightLevel.MEDIUM -> EffortVisualStyle(
        bg = ContextCompat.getColor(this, R.color.ambar_vez_suave),
        iconTint = ContextCompat.getColor(this, R.color.ambar_traco),
        dotColor = ContextCompat.getColor(this, R.color.ambar_vez),
        pillText = ContextCompat.getColor(this, R.color.ambar_vez_texto),
        labelRes = R.string.effort_medium,
    )
    TaskWeightLevel.HEAVY -> EffortVisualStyle(
        bg = ColorUtils.setAlphaComponent(
            ContextCompat.getColor(this, R.color.pref_odeio),
            if (isNightMode()) 56 else 31,
        ),
        iconTint = ContextCompat.getColor(this, R.color.pref_odeio),
        dotColor = ContextCompat.getColor(this, R.color.pref_odeio),
        pillText = ContextCompat.getColor(this, R.color.pref_odeio_texto),
        labelRes = R.string.effort_heavy,
    )
}

internal fun TaskIconHint.iconRes(): Int = when (this) {
    TaskIconHint.DROP -> R.drawable.ic_drop
    TaskIconHint.TRASH -> R.drawable.ic_trash
    TaskIconHint.CART -> R.drawable.ic_cart
    TaskIconHint.RECEIPT -> R.drawable.ic_receipt
    TaskIconHint.FRIDGE -> R.drawable.ic_fridge
    TaskIconHint.LEAF -> R.drawable.ic_leaf
    TaskIconHint.CALENDAR -> R.drawable.ic_calendar
}

internal fun Context.isNightMode(): Boolean {
    val mask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mask == Configuration.UI_MODE_NIGHT_YES
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
