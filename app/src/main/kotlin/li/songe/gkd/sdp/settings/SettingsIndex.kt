package li.songe.gkd.sdp.settings

/** Stable search index for the settings tab. */
object SettingsIndex {
    val entries: List<SettingsEntry> = listOf(
        SettingsEntry(
            group = SettingsGroup.RUNTIME_CAPABILITIES,
            title = "运行能力与权限",
            titleEn = "Capabilities & permissions",
            keywords = listOf("无障碍", "自动化", "Shizuku", "悬浮窗", "通知", "电池"),
            aliases = listOf("能力中心", "权限中心"),
        ),
        SettingsEntry(
            group = SettingsGroup.SELF_CONTROL,
            title = "数字自律",
            titleEn = "Self-control",
            keywords = listOf("使用申请", "专注模式", "拦截", "锁定", "自动重开", "复盘"),
            aliases = listOf("自律", "应用拦截", "网址拦截"),
        ),
        SettingsEntry(
            group = SettingsGroup.RULES_SUBSCRIPTIONS_TRIGGERS,
            title = "规则、订阅与触发",
            titleEn = "Rules, subscriptions & triggers",
            keywords = listOf("订阅", "规则组", "应用列表", "触发"),
            aliases = listOf("规则", "订阅管理"),
        ),
        SettingsEntry(
            group = SettingsGroup.DISPLAY_THEME_ACCESSIBILITY,
            title = "显示、主题与无障碍",
            titleEn = "Display, theme & accessibility",
            keywords = listOf("深色", "动态取色", "字号", "语言", "无障碍"),
            aliases = listOf("外观", "主题"),
        ),
        SettingsEntry(
            group = SettingsGroup.PRIVACY_DATA,
            title = "隐私与数据",
            titleEn = "Privacy & data",
            keywords = listOf("备份", "删除", "导出", "支持包", "明文订阅"),
            aliases = listOf("数据", "隐私"),
        ),
        SettingsEntry(
            group = SettingsGroup.DIAGNOSTICS_DEVELOPER,
            title = "诊断与开发者工具",
            titleEn = "Diagnostics & developer tools",
            keywords = listOf("日志", "远程调试", "HTTP", "Shizuku", "快照"),
            aliases = listOf("高级", "诊断"),
        ),
        SettingsEntry(
            group = SettingsGroup.ABOUT_UPDATES,
            title = "关于与更新",
            titleEn = "About & updates",
            keywords = listOf("版本", "更新", "GitHub", "许可证"),
            aliases = listOf("关于"),
        ),
    )
}

fun SettingsGroup.label(): String = when (this) {
    SettingsGroup.RUNTIME_CAPABILITIES -> "运行能力与权限"
    SettingsGroup.SELF_CONTROL -> "数字自律"
    SettingsGroup.RULES_SUBSCRIPTIONS_TRIGGERS -> "规则、订阅与触发"
    SettingsGroup.DISPLAY_THEME_ACCESSIBILITY -> "显示、主题与无障碍"
    SettingsGroup.PRIVACY_DATA -> "隐私与数据"
    SettingsGroup.DIAGNOSTICS_DEVELOPER -> "诊断与开发者工具"
    SettingsGroup.ABOUT_UPDATES -> "关于与更新"
}
