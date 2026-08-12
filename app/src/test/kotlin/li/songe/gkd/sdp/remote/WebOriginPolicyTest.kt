package li.songe.gkd.sdp.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebOriginPolicyTest {
    @Test
    fun `main frame navigation has an exact scheme and host policy`() {
        assertEquals(WebNavigationDecision.ALLOW, WebOriginPolicy.decide("https://gkd.li/docs"))
        assertEquals(WebNavigationDecision.BLOCK, WebOriginPolicy.decide("https://gkd.li:444/docs"))
        assertEquals(WebNavigationDecision.INTERNAL, WebOriginPolicy.decide("gkd://settings"))
        assertEquals(WebNavigationDecision.EXTERNAL, WebOriginPolicy.decide("https://example.com"))
        listOf(
            "http://gkd.li",
            "file:///tmp/a",
            "content://authority/a",
            "data:text/html,test",
            "javascript:alert(1)",
            "intent://host",
            "ftp://example.com",
        ).forEach { url ->
            assertEquals(WebNavigationDecision.BLOCK, WebOriginPolicy.decide(url))
        }
    }

    @Test
    fun `mirror proxy accepts only fixed https package paths`() {
        assertTrue(
            WebOriginPolicy.isAllowedMirror(
                "https://registry.npmmirror.com/@gkd-kit/docs/1.0.0/files/index.html",
            ),
        )
        assertFalse(WebOriginPolicy.isAllowedMirror("http://registry.npmmirror.com/@gkd-kit/docs/a"))
        assertFalse(
            WebOriginPolicy.isAllowedMirror(
                "https://registry.npmmirror.com:444/@gkd-kit/docs/a",
            ),
        )
        assertFalse(WebOriginPolicy.isAllowedMirror("https://registry.npmmirror.com/other/a"))
        assertFalse(WebOriginPolicy.isAllowedMirror("https://evil.example/@gkd-kit/docs/a"))
    }

    @Test
    fun `every accepted semantic deep link resolves to an application destination`() {
        val expected = mapOf(
            "gkd://overview" to SemanticDeepLinkTarget.OVERVIEW,
            "gkd://self-control" to SemanticDeepLinkTarget.SELF_CONTROL,
            "gkd://settings" to SemanticDeepLinkTarget.SETTINGS,
            "gkd://usage-guard" to SemanticDeepLinkTarget.USAGE_GUARD,
            "gkd://usage-review" to SemanticDeepLinkTarget.USAGE_REVIEW,
            "gkd://action-log" to SemanticDeepLinkTarget.ACTION_LOG,
            "gkd://rules/subscriptions" to SemanticDeepLinkTarget.RULE_SUBSCRIPTIONS,
            "gkd://rules/apps" to SemanticDeepLinkTarget.RULE_APPS,
        )

        expected.forEach { (url, target) ->
            assertEquals(target, WebOriginPolicy.semanticDeepLinkTarget(url))
            assertEquals(WebNavigationDecision.INTERNAL, WebOriginPolicy.decide(url))
        }
        assertEquals(null, WebOriginPolicy.semanticDeepLinkTarget("gkd://settings/extra"))
        assertEquals(null, WebOriginPolicy.semanticDeepLinkTarget("gkd://overview?tab=1"))
    }

    @Test
    fun `every accepted legacy deep link resolves through the same navigation parser`() {
        val expected = mapOf(
            "gkd://page" to LegacyDeepLinkTarget.OVERVIEW,
            "gkd://page/" to LegacyDeepLinkTarget.OVERVIEW,
            "gkd://page/0" to LegacyDeepLinkTarget.OVERVIEW,
            "gkd://page?tab=0" to LegacyDeepLinkTarget.OVERVIEW,
            "gkd://page?tab=1" to LegacyDeepLinkTarget.SUBSCRIPTIONS,
            "gkd://page?tab=2" to LegacyDeepLinkTarget.APPS,
            "gkd://page?tab=3" to LegacyDeepLinkTarget.SETTINGS,
            "gkd://page/1" to LegacyDeepLinkTarget.ADVANCED,
            "gkd://page/2" to LegacyDeepLinkTarget.SNAPSHOT,
            "gkd://page/3" to LegacyDeepLinkTarget.CAPABILITY_CENTER,
            "gkd://page/4" to LegacyDeepLinkTarget.SELF_CONTROL,
            "gkd://invoke/1" to LegacyDeepLinkTarget.WECHAT_SCANNER,
        )

        expected.forEach { (url, target) ->
            assertEquals(target, WebOriginPolicy.legacyDeepLinkTarget(url))
            assertEquals(WebNavigationDecision.INTERNAL, WebOriginPolicy.decide(url))
        }
        assertEquals(null, WebOriginPolicy.legacyDeepLinkTarget("gkd://page?x=1&tab=2"))
        assertEquals(WebNavigationDecision.BLOCK, WebOriginPolicy.decide("gkd://page?x=1&tab=2"))
    }
}
