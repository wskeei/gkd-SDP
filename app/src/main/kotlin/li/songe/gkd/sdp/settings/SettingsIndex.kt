package li.songe.gkd.sdp.settings

import li.songe.gkd.sdp.R

/** Stable search index for the settings tab. */
object SettingsIndex {
    val entries: List<SettingsEntry> = listOf(
        SettingsEntry(
            id = "capabilities",
            group = SettingsGroup.RUNTIME_CAPABILITIES,
            titleRes = R.string.settings_capabilities_title,
            // i18n-ignore: legacy fallback or non-display heuristic data
            searchTitle = "运行能力与权限",
            titleEn = "Capabilities & permissions",
            // i18n-ignore: legacy fallback or non-display heuristic data
            keywords = listOf("无障碍", "自动化", "Shizuku", "悬浮窗", "通知", "电池"),
            // i18n-ignore: legacy fallback or non-display heuristic data
            aliases = listOf("能力中心", "权限中心"),
            target = SettingsTarget.Route("capability_center"),
        ),
        SettingsEntry(
            id = "self_control",
            group = SettingsGroup.SELF_CONTROL,
            titleRes = R.string.settings_self_control_title,
            // i18n-ignore: legacy fallback or non-display heuristic data
            searchTitle = "数字自律",
            titleEn = "Self-control",
            // i18n-ignore: legacy fallback or non-display heuristic data
            keywords = listOf("使用申请", "专注模式", "拦截", "锁定", "自动重开", "复盘"),
            // i18n-ignore: legacy fallback or non-display heuristic data
            aliases = listOf("自律", "应用拦截", "网址拦截"),
            target = SettingsTarget.InPage("self_control_settings"),
        ),
        SettingsEntry(
            id = "rules_subscriptions_triggers",
            group = SettingsGroup.RULES_SUBSCRIPTIONS_TRIGGERS,
            titleRes = R.string.settings_rules_title,
            // i18n-ignore: legacy fallback or non-display heuristic data
            searchTitle = "规则、订阅与触发",
            titleEn = "Rules, subscriptions & triggers",
            // i18n-ignore: legacy fallback or non-display heuristic data
            keywords = listOf("订阅", "规则组", "应用列表", "触发"),
            // i18n-ignore: legacy fallback or non-display heuristic data
            aliases = listOf("规则", "订阅管理"),
            target = SettingsTarget.InPage("rules_settings"),
        ),
        SettingsEntry(
            id = "display_theme_accessibility",
            group = SettingsGroup.DISPLAY_THEME_ACCESSIBILITY,
            titleRes = R.string.settings_display_theme_title,
            // i18n-ignore: legacy fallback or non-display heuristic data
            searchTitle = "显示、主题与无障碍",
            titleEn = "Display, theme & accessibility",
            // i18n-ignore: legacy fallback or non-display heuristic data
            keywords = listOf("深色", "动态取色", "字号", "语言", "无障碍"),
            // i18n-ignore: legacy fallback or non-display heuristic data
            aliases = listOf("外观", "主题"),
            target = SettingsTarget.InPage("appearance_settings"),
        ),
        SettingsEntry(
            id = "privacy_data",
            group = SettingsGroup.PRIVACY_DATA,
            titleRes = R.string.settings_privacy_title,
            // i18n-ignore: legacy fallback or non-display heuristic data
            searchTitle = "隐私与数据",
            titleEn = "Privacy & data",
            // i18n-ignore: legacy fallback or non-display heuristic data
            keywords = listOf("备份", "删除", "导出", "支持包", "明文订阅"),
            // i18n-ignore: legacy fallback or non-display heuristic data
            aliases = listOf("数据", "隐私"),
            target = SettingsTarget.Route("privacy_data"),
        ),
        SettingsEntry(
            id = "diagnostics_developer",
            group = SettingsGroup.DIAGNOSTICS_DEVELOPER,
            titleRes = R.string.settings_diagnostics_title,
            // i18n-ignore: legacy fallback or non-display heuristic data
            searchTitle = "诊断与开发者工具",
            titleEn = "Diagnostics & developer tools",
            // i18n-ignore: legacy fallback or non-display heuristic data
            keywords = listOf("日志", "远程调试", "HTTP", "Shizuku", "快照"),
            // i18n-ignore: legacy fallback or non-display heuristic data
            aliases = listOf("高级", "诊断"),
            target = SettingsTarget.Route("advanced"),
        ),
        SettingsEntry(
            id = "about_updates",
            group = SettingsGroup.ABOUT_UPDATES,
            titleRes = R.string.settings_about_title,
            // i18n-ignore: legacy fallback or non-display heuristic data
            searchTitle = "关于与更新",
            titleEn = "About & updates",
            // i18n-ignore: legacy fallback or non-display heuristic data
            keywords = listOf("版本", "更新", "GitHub", "许可证"),
            // i18n-ignore: legacy fallback or non-display heuristic data
            aliases = listOf("关于"),
            target = SettingsTarget.InPage("about_settings"),
        ),
    )
}
