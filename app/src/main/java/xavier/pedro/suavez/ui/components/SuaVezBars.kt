package xavier.pedro.suavez.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xavier.pedro.suavez.ui.state.MainTab
import xavier.pedro.suavez.ui.theme.SuaVezDimens
import xavier.pedro.suavez.ui.theme.SuaVezTheme

@Composable
fun SuaVezAppBar(
    houseName: String? = null,
    userName: String? = null,
    userId: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 6.dp)
            .height(SuaVezDimens.AppBarHeight)
            .padding(horizontal = SuaVezDimens.ScreenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuaVezWordmark(markSize = 28.dp, fontSize = 21.sp, gap = 6.dp)

        if (!houseName.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.faint),
            )
            Text(
                text = houseName,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!userName.isNullOrBlank() && userId != null) {
            SuaVezAvatar(
                name = userName,
                idForColor = userId,
                size = 36.dp,
                modifier = if (onAvatarClick != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onAvatarClick)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
fun SuaVezSubBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddClick: (() -> Unit)? = null,
    addContentDescription: String = "Adicionar",
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 6.dp)
            .height(SuaVezDimens.AppBarHeight)
            .padding(horizontal = SuaVezDimens.ScreenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SuaVezRoundButton(
            icon = SuaVezIconType.BACK,
            contentDescription = "Voltar",
            onClick = onBack,
            variant = SuaVezRoundButtonVariant.Default,
        )
        Text(
            text = title,
            style = typo.subBarTitle,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onAddClick != null) {
            SuaVezRoundButton(
                icon = SuaVezIconType.PLUS,
                contentDescription = addContentDescription,
                onClick = onAddClick,
                variant = SuaVezRoundButtonVariant.Primary,
            )
        }
    }
}

@Composable
fun SuaVezTabBar(
    activeTab: MainTab,
    hasHouse: Boolean,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SuaVezTheme.colors
    val typo = SuaVezTheme.typography
    val tabs = if (hasHouse) {
        listOf(MainTab.TASKS, MainTab.PREFERENCES, MainTab.HOUSE, MainTab.PROFILE)
    } else {
        listOf(MainTab.HOUSE, MainTab.PROFILE)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SuaVezDimens.TabBarHeight)
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = activeTab == tab
                val iconType = when (tab) {
                    MainTab.TASKS -> SuaVezIconType.TASKS
                    MainTab.PREFERENCES -> SuaVezIconType.SLIDERS
                    MainTab.HOUSE -> SuaVezIconType.HOME
                    MainTab.PROFILE -> SuaVezIconType.USER
                }
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { selected = isSelected }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelectTab(tab) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (isSelected) colors.mist else colors.surface.copy(alpha = 0f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        SuaVezIcon(
                            type = iconType,
                            tint = if (isSelected) colors.teal else colors.faint,
                            size = 24.dp,
                            strokeWidthDp = 1.8.dp,
                            softFill = if (isSelected) colors.teal.copy(alpha = 0.25f) else null,
                        )
                    }
                    Text(
                        text = tab.label,
                        style = if (isSelected) typo.tabActive else typo.tab,
                        color = if (isSelected) colors.tealDeep else colors.faint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
