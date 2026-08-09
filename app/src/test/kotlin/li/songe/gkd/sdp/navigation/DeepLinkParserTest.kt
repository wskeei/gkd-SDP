package li.songe.gkd.sdp.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import li.songe.gkd.sdp.ui.AppOpsAllowRoute

class DeepLinkParserTest {
    @Test
    fun parsesSemanticDestinations() {
        val expected = mapOf(
            "gkd://overview" to AppDestination.OVERVIEW,
            "gkd://self-control" to AppDestination.SELF_CONTROL,
            "gkd://rules/subscriptions" to AppDestination.RULES_SUBSCRIPTIONS,
            "gkd://rules/apps" to AppDestination.RULES_APPS,
            "gkd://settings" to AppDestination.SETTINGS,
            "gkd://settings/capabilities" to AppDestination.SETTINGS_CAPABILITIES,
            "gkd://settings/privacy-data" to AppDestination.SETTINGS_PRIVACY_DATA,
            "gkd://usage-guard" to AppDestination.USAGE_GUARD,
            "gkd://usage-review" to AppDestination.USAGE_REVIEW,
            "gkd://action-log" to AppDestination.ACTION_LOG,
        )
        expected.forEach { (uri, destination) ->
            assertEquals(DeepLinkParseResult.Destination(destination), DeepLinkParser.parse(uri))
        }
    }

    @Test
    fun convertsLegacyLinksImmediately() {
        assertEquals(
            DeepLinkParseResult.Destination(AppDestination.RULES_APPS),
            DeepLinkParser.parse("gkd://page?tab=2"),
        )
        assertEquals(
            DeepLinkParseResult.Destination(AppDestination.SELF_CONTROL),
            DeepLinkParser.parse("gkd://page/4"),
        )
        val legacyPages = mapOf(
            "gkd://page/0" to AppDestination.OVERVIEW,
            "gkd://page/1" to AppDestination.LEGACY_ADVANCED,
            "gkd://page/2" to AppDestination.LEGACY_SNAPSHOT,
            "gkd://page/3" to AppDestination.LEGACY_APP_OPS,
            "gkd://page/4" to AppDestination.SELF_CONTROL,
        )
        legacyPages.forEach { (uri, destination) ->
            assertEquals(DeepLinkParseResult.Destination(destination), DeepLinkParser.parse(uri))
            assertEquals(destination.toNavKey(), (DeepLinkParser.parse(uri) as DeepLinkParseResult.Destination).value.toNavKey())
        }
        val legacyTabs = mapOf(
            "gkd://page?tab=0" to AppDestination.OVERVIEW,
            "gkd://page?tab=1" to AppDestination.RULES_SUBSCRIPTIONS,
            "gkd://page?tab=2" to AppDestination.RULES_APPS,
            "gkd://page?tab=3" to AppDestination.SETTINGS,
        )
        legacyTabs.forEach { (uri, destination) ->
            assertEquals(DeepLinkParseResult.Destination(destination), DeepLinkParser.parse(uri))
        }
    }

    @Test
    fun rejectsUnknownOrAmbiguousLinks() {
        listOf(
            "https://overview",
            "gkd://unknown",
            "gkd://settings?tab=1",
            "gkd://rules/unknown",
            "gkd://overview#fragment",
            "gkd://overview:443",
            "gkd://user@overview",
        ).forEach { uri ->
            assertEquals(DeepLinkParseResult.Invalid, DeepLinkParser.parse(uri))
        }
    }

    @Test
    fun capabilitiesDestinationOpensTheAppOpsCenter() {
        assertEquals(AppOpsAllowRoute, AppDestination.SETTINGS_CAPABILITIES.toNavKey())
    }
}
