package com.openapi.roles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalRoleCatalogParserTest {

    private fun assetBody(): String {
        // Gradle 单元测试的工作目录是模块目录 app/。
        val file = File("src/main/assets/personas/agency-agents-import.json")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun bundledFileParsesToEightLocalRoles() {
        val catalog = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "zh"))
        assertEquals(8, catalog.roles.size)
        assertTrue(catalog.roles.all { it.local })
        assertTrue(catalog.roles.all { it.id.startsWith("agency-") })
        assertEquals(setOf("service", "care", "career", "study"), catalog.divisions.map { it.id }.toSet())
        assertEquals(8, catalog.divisions.sumOf { it.count })
    }

    @Test
    fun zhUsesChineseNamesAndEnglishFallsBackToSourceNames() {
        val zh = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "zh")).roles
        val en = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "en")).roles
        val zhById = zh.associateBy { it.id }
        val enById = en.associateBy { it.id }
        assertEquals("老人照护助手", zhById["agency-healthcare-aging-parent-care-companion"]!!.name)
        assertEquals("Aging Parent Care Companion", enById["agency-healthcare-aging-parent-care-companion"]!!.name)
    }

    @Test
    fun pageFiltersByDivisionQueryAndPaginates() {
        val catalog = requireNotNull(RolesParser.parseLocalCatalog(assetBody(), "zh"))
        val career = catalog.page(division = "career", query = "", featuredOnly = false, limit = 30, offset = 0)
        assertEquals(3, career.total)
        assertEquals(3, career.items.size)
        assertFalse(career.hasMore)

        val search = catalog.page(division = null, query = "简历", featuredOnly = false, limit = 30, offset = 0)
        assertEquals(listOf("agency-resume-tailor"), search.items.map { it.id })

        val paged = catalog.page(division = null, query = "", featuredOnly = false, limit = 3, offset = 0)
        assertEquals(3, paged.items.size)
        assertTrue(paged.hasMore)
    }

    @Test
    fun brokenOrWrongShapeIsNull() {
        assertNull(RolesParser.parseLocalCatalog("not json", "zh"))
        assertNull(RolesParser.parseLocalCatalog("""{"code":0,"data":{"items":[]}}""", "zh"))
    }
}
