package li.songe.gkd.sdp.util

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import li.songe.gkd.sdp.R
import li.songe.gkd.sdp.ui.component.PerfIcon

sealed interface Option<T> {
    val value: T
    @get:StringRes
    val labelRes: Int
    val options: List<Option<T>>
}

sealed interface OptionIcon {
    val icon: ImageVector
}

sealed interface OptionMenuLabel {
    @get:StringRes
    val menuLabelRes: Int
}

fun <V, T : Option<V>> Iterable<T>.findOption(value: V): T {
    return find { it.value == value } ?: first()
}

sealed class AppSortOption(override val value: Int, @StringRes override val labelRes: Int) : Option<Int> {
    override val options get() = objects

    data object ByAppName : AppSortOption(0, R.string.option_sort_by_name)
    data object ByActionTime : AppSortOption(2, R.string.option_sort_by_action_time)
    data object ByUsedTime : AppSortOption(3, R.string.option_sort_by_used_time)

    companion object {
        val objects by lazy { listOf(ByAppName, ByUsedTime, ByActionTime) }
    }
}

sealed class UpdateTimeOption(
    override val value: Long,
    @StringRes override val labelRes: Int
) : Option<Long> {
    override val options get() = objects

    data object Pause : UpdateTimeOption(-1, R.string.option_update_pause)
    data object Everyday : UpdateTimeOption(24 * 60 * 60_000, R.string.option_update_daily)
    data object Every3Days : UpdateTimeOption(24 * 60 * 60_000 * 3, R.string.option_update_3d)
    data object Every7Days : UpdateTimeOption(24 * 60 * 60_000 * 7, R.string.option_update_7d)

    companion object {
        val objects by lazy { listOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

sealed class DarkThemeOption(
    override val value: Boolean?,
    @StringRes override val labelRes: Int,
    @StringRes override val menuLabelRes: Int,
    override val icon: ImageVector
) : Option<Boolean?>, OptionIcon, OptionMenuLabel {
    override val options get() = objects

    data object FollowSystem : DarkThemeOption(null, R.string.option_theme_auto, R.string.option_theme_auto, PerfIcon.AutoMode)
    data object AlwaysEnable : DarkThemeOption(true, R.string.option_theme_enabled, R.string.option_theme_menu_dark, PerfIcon.DarkMode)
    data object AlwaysDisable : DarkThemeOption(false, R.string.option_theme_disabled, R.string.option_theme_menu_light, PerfIcon.LightMode)

    companion object {
        val objects by lazy { listOf(FollowSystem, AlwaysEnable, AlwaysDisable) }
    }
}

sealed class DisplayDensityOption(
    override val value: Float,
    @StringRes override val labelRes: Int,
) : Option<Float> {
    override val options get() = objects

    data object Compact : DisplayDensityOption(0.9f, R.string.option_density_compact)
    data object Standard : DisplayDensityOption(1f, R.string.option_density_standard)
    data object Comfortable : DisplayDensityOption(1.15f, R.string.option_density_comfortable)
    data object Large : DisplayDensityOption(1.3f, R.string.option_density_large)

    companion object {
        val objects by lazy { listOf(Compact, Standard, Comfortable, Large) }
    }
}

sealed class LanguageOption(
    override val value: String,
    @StringRes override val labelRes: Int,
) : Option<String> {
    override val options get() = objects

    data object FollowSystem : LanguageOption("", R.string.option_language_follow_system)
    data object SimplifiedChinese : LanguageOption("zh-CN", R.string.option_language_simplified_chinese)

    companion object {
        val objects by lazy { listOf(FollowSystem, SimplifiedChinese) }
    }
}

sealed class EnableGroupOption(
    override val value: Boolean?,
    @StringRes override val labelRes: Int
) : Option<Boolean?> {
    override val options get() = objects

    data object FollowSubs : EnableGroupOption(null, R.string.option_enable_follow_subscription)
    data object AllEnable : EnableGroupOption(true, R.string.option_enable_all)
    data object AllDisable : EnableGroupOption(false, R.string.option_disable_all)

    companion object {
        val objects by lazy { listOf(FollowSubs, AllEnable, AllDisable) }
    }
}

sealed class RuleSortOption(override val value: Int, @StringRes override val labelRes: Int) : Option<Int> {
    override val options get() = objects

    data object ByDefault : RuleSortOption(0, R.string.option_rule_sort_default)
    data object ByActionTime : RuleSortOption(1, R.string.option_rule_sort_action_time)
    data object ByRuleName : RuleSortOption(2, R.string.option_rule_sort_name)

    companion object {
        val objects by lazy { listOf(ByDefault, ByActionTime, ByRuleName) }
    }
}

sealed class UpdateChannelOption(
    override val value: Int,
    @StringRes override val labelRes: Int,
) : Option<Int> {
    override val options get() = objects

    data object Stable : UpdateChannelOption(
        0,
        R.string.option_channel_stable,
    )

    data object Beta : UpdateChannelOption(
        1,
        R.string.option_channel_beta,
    )

    companion object {
        val objects by lazy { listOf(Stable, Beta) }
    }
}

sealed interface BinaryOption : Option<Int> {
    fun include(flag: Int): Boolean = (value and flag) != 0
    fun invert(flag: Int): Int = value xor flag

    companion object {
        fun combine(options: Collection<BinaryOption>): Int {
            return options.fold(0) { a, b -> a or b.value }
        }
    }
}


sealed class AppGroupOption(
    override val value: Int,
    @StringRes override val labelRes: Int
) : BinaryOption {
    override val options get() = allObjects

    data object SystemGroup : AppGroupOption(1 shl 0, R.string.option_app_group_system)
    data object UserGroup : AppGroupOption(1 shl 1, R.string.option_app_group_user)
    data object UnInstalledGroup : AppGroupOption(1 shl 2, R.string.option_app_group_uninstalled)

    companion object {
        val normalObjects by lazy { listOf(SystemGroup, UserGroup) }
        val allObjects by lazy { listOf(SystemGroup, UserGroup, UnInstalledGroup) }
    }
}

sealed class AutomatorModeOption(
    override val value: Int,
    @StringRes override val labelRes: Int,
) : Option<Int> {
    override val options get() = objects

    data object A11yMode : AutomatorModeOption(1, R.string.option_automator_a11y)
    data object AutomationMode : AutomatorModeOption(2, R.string.option_automator_automation)

    companion object {
        val objects by lazy { listOf(A11yMode, AutomationMode) }
    }
}
