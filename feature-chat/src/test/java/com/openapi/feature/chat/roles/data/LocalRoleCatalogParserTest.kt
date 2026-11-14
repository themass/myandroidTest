package com.openapi.feature.chat.roles.data


import com.openapi.feature.chat.roles.data.RolesParser
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 打包的离线角色目录。
 *
 * **这份文件必须与服务端目录同源。** 以前它是另外手工维护的 14 条 `agency-*`，
 * 而服务端是 `academic-*` / `specialized-*` 一套 id；服务端目录接口一坏，客户端就静默
 * 退回这份打包目录，于是用户选了「客服专员」，发出去的 `roleId` 服务端根本不认识，
 * 按回退规则回了默认助手——看起来就是「切了角色但回答的不是那个角色」。
 *
 * 所以这里的断言写成「与服务端目录一致」的形式，而不是固定某个条数：
 * 条数会变，id 必须对得上。
 */
class LocalRoleCatalogParserTest {

    private fun assetBody(): String {
        // Gradle 单元测试的工作目录是模块目录。
        val file = File("src/main/assets/personas/agency-agents-import.json")
        return file.readText(Charsets.UTF_8)
    }

    private fun assetJson() = JsonParser.parseString(assetBody()).asJsonObject

    @Test
    fun bundledCatalogParsesAndEveryRoleHasAnId() {
        val catalog = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "zh"))
        assertTrue("目录不能为空", catalog.roles.isNotEmpty())
        assertTrue(catalog.roles.all { it.local })
        assertTrue("id 不能为空", catalog.roles.all { it.id.isNotBlank() })
        // id 必须落在某个分组里，否则选择器里这条角色无处可去
        assertTrue(
            "有角色没有对应分组: " + catalog.roles.filter { r -> catalog.divisions.none { it.id == r.divisionId } }
                .map { it.id }.take(5),
            catalog.roles.all { r -> catalog.divisions.any { it.id == r.divisionId } },
        )
        assertEquals(catalog.roles.size, catalog.divisions.sumOf { it.count })
    }

    @Test
    fun idsMatchTheServerCatalogNamespace() {
        // 服务端的 id 都是 `<division>-<name>` 形式；旧的 agency-* 命名已经不存在了。
        // 这条守住「同一套 id」——只在这里断言前缀，不绑定具体条数。
        val catalog = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "zh"))
        assertTrue(
            "打包目录里不应再有 agency-* 的旧 id: " + catalog.roles.filter { it.id.startsWith("agency-") }.map { it.id },
            catalog.roles.none { it.id.startsWith("agency-") },
        )
        assertTrue(catalog.roles.all { it.id.contains("-") })
    }

    @Test
    fun zhUsesChineseNamesAndEnglishFallsBackToSourceNames() {
        val zh = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "zh")).roles.associateBy { it.id }
        val en = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "en")).roles.associateBy { it.id }
        // 取一条两边都有的角色，确认语言分支真的起作用
        val id = zh.keys.first { en.containsKey(it) && zh.getValue(it).name.isNotBlank() }
        assertEquals(zh.getValue(id).name, zh.getValue(id).name)
        assertTrue("英文名不能是空", en.getValue(id).name.isNotBlank())
        // 至少有一条角色的中英文名不同，否则说明语言参数根本没被用上
        assertTrue(
            "中英文名完全相同，语言分支可能失效",
            zh.keys.any { en[it]?.name != null && en.getValue(it).name != zh.getValue(it).name },
        )
    }

    @Test
    fun pageFiltersByDivisionAndPaginates() {
        val catalog = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "zh"))
        val division = catalog.divisions.first().id
        val inDivision = catalog.page(division = division, query = "", featuredOnly = false, limit = 500, offset = 0)
        assertEquals(catalog.divisions.first().count, inDivision.total)

        val paged = catalog.page(division = null, query = "", featuredOnly = false, limit = 3, offset = 0)
        assertEquals(3, paged.items.size)
        assertTrue(paged.hasMore)

        val beyond = catalog.page(division = null, query = "", featuredOnly = false, limit = 3,
                                  offset = catalog.roles.size + 10)
        assertTrue("越界页应为空", beyond.items.isEmpty())
    }

    @Test
    fun divisionLabelsComeFromTheCatalog() {
        val divisions = assetJson().getAsJsonArray("divisions")
        assertTrue(divisions.size() > 0)
        assertTrue(
            "分组必须有中文名，否则选择器里显示的是英文 id",
            divisions.all { it.asJsonObject.get("label").asString.isNotBlank() },
        )
    }

    @Test
    fun brokenOrWrongShapeIsNull() {
        assertNull(RolesParser.parseLocalCatalog("not json", "zh"))
        assertNull(RolesParser.parseLocalCatalog("""{"code":0,"data":{"items":[]}}""", "zh"))
    }

    @Test
    fun customerServiceRoleExistsInTheBundledCatalog() {
        // 这条是那次线上问题的回归：用户选「客服专员」，服务端不认识这个 id，
        // 回退成默认助手。打包目录必须包含服务端实际存在的客服类角色。
        val catalog = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "zh"))
        val byId = catalog.roles.associateBy { it.id }
        // 服务端叫「客户服务专员」，id 是 specialized-customer-service
        assertNotNull("打包目录里没有 specialized-customer-service", byId["specialized-customer-service"])
        assertTrue(
            "客服类角色应能在中文名里找到",
            catalog.roles.any { it.name.contains("客户服务") || it.name.contains("客服") },
        )
    }
}
