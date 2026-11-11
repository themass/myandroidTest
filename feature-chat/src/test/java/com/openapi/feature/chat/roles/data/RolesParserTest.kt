package com.openapi.feature.chat.roles.data


import com.openapi.feature.chat.roles.data.RoleColors
import com.openapi.feature.chat.roles.data.RolesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RolesParserTest {

    @Test
    fun parsesDivisions() {
        val body = """{"code":0,"message":"ok","data":[
            {"id":"engineering","label":"Engineering","icon":"Code","color":"#3B82F6","count":66},
            {"id":"design","label":"Design","icon":"PenTool","color":"#EC4899","count":10}]}"""
        val list = RolesParser.parseDivisions(body)!!
        assertEquals(2, list.size)
        assertEquals("engineering", list[0].id)
        assertEquals(66, list[0].count)
        assertEquals(0xFF3B82F6, list[0].colorArgb)
    }

    @Test
    fun parsesRolePageAndHasMore() {
        val body = """{"code":0,"message":"ok","data":{"items":[
            {"id":"a","division":"x","name":"A","description":"d","emoji":"🖥️","color":"cyan","vibe":"v","featured":true},
            {"id":"b","division":"x","name":"B","description":"d","emoji":"🧪","color":"nope","vibe":"","featured":false}],
            "total":5,"limit":2,"offset":0}}"""
        val page = RolesParser.parseRolePage(body)!!
        assertEquals(2, page.items.size)
        assertTrue(page.items[0].featured)
        assertEquals(0xFF06B6D4, page.items[0].colorArgb)
        assertEquals(RoleColors.FALLBACK, page.items[1].colorArgb)
        assertTrue(page.hasMore)
    }

    @Test
    fun lastPageHasNoMore() {
        val body = """{"code":0,"data":{"items":[{"id":"a","name":"A"}],"total":3,"offset":2}}"""
        assertFalse(RolesParser.parseRolePage(body)!!.hasMore)
    }

    @Test
    fun nonZeroCodeAndGarbageAreFailuresNotEmptyResults() {
        assertNull(RolesParser.parseDivisions("""{"code":400,"data":null,"message":"bad"}"""))
        assertNull(RolesParser.parseRolePage("not json"))
        assertNull(RolesParser.parseRolePage("""{"code":0,"data":{"total":1}}"""))
        assertNull(RolesParser.parseRole("""{"code":404,"data":null}"""))
    }

    @Test
    fun skipsMalformedItemsButKeepsTheRest() {
        val body = """{"code":0,"data":{"items":[{"name":"no id"},{"id":"ok","name":"OK"}],"total":2,"offset":0}}"""
        val page = RolesParser.parseRolePage(body)!!
        assertEquals(listOf("ok"), page.items.map { it.id })
    }

    @Test
    fun colorParsing() {
        assertEquals(0xFF2563EB, RoleColors.parse("#2563eb"))
        assertEquals(0xFFAABBCC, RoleColors.parse("#abc"))
        assertEquals(RoleColors.FALLBACK, RoleColors.parse(null))
        assertEquals(RoleColors.FALLBACK, RoleColors.parse(""))
        assertEquals(RoleColors.FALLBACK, RoleColors.parse("#12"))
        assertEquals(0xFF3B82F6, RoleColors.parse(" Blue "))
    }
}
