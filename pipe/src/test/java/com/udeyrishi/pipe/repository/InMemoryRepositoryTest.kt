package com.udeyrishi.pipe.repository

import com.udeyrishi.pipe.util.Identifiable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

@RunWith(JUnit4::class)
class InMemoryRepositoryTest {
    private lateinit var repo: InMemoryRepository<MyIdentifiable>

    @Before
    fun setup() {
        repo = InMemoryRepository()
    }

    @Test
    fun addWorks() {
        val myIdentifiable = repo.add(tag = null) { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        val myIdentifiable2 = repo.add(tag = null) { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        assertEquals(0L, myIdentifiable.position)
        assertEquals(1L, myIdentifiable2.position)

        assertEquals(myIdentifiable, repo[myIdentifiable.uuid]?.identifiableObject)
        assertEquals(myIdentifiable2, repo[myIdentifiable2.uuid]?.identifiableObject)
    }

    @Test
    fun getByUuidWorksWhenUuidNotFound() {
        assertNull(repo[UUID.randomUUID()])
        val myIdentifiable = repo.add(tag = null) { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }
        assertNotNull(repo[myIdentifiable.uuid])
        assertNull(repo[UUID.randomUUID()])
    }

    @Test
    fun getByTagWorksWhenTagFound() {
        val myIdentifiable = repo.add(tag = "tag1") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        val myIdentifiable2 = repo.add(tag = "tag2") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        val myIdentifiable3 = repo.add(tag = "tag1") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        val group1 = repo["tag1"]
        val group2 = repo["tag2"]

        assertEquals(listOf(myIdentifiable, myIdentifiable3), group1.map { it.identifiableObject })
        assertEquals(listOf(myIdentifiable2), group2.map { it.identifiableObject })
    }

    @Test
    fun getByTagWorksWhenTagNotFound() {
        repo.add(tag = "tag1") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        repo.add(tag = "tag2") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        val group1 = repo["tag3"]

        assertEquals(listOf<MyIdentifiable>(), group1.map { it.identifiableObject })
    }

    @Test
    fun removeIfWorks() {
        repo.add(tag = "tag1") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        val myIdentifiable2 = repo.add(tag = "tag2") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        repo.add(tag = "tag1") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        repo.removeIf {
            it.tag == "tag1"
        }

        val group1 = repo["tag1"]
        val group2 = repo["tag2"]

        assertEquals(listOf<MyIdentifiable>(), group1.map { it.identifiableObject })
        assertEquals(listOf(myIdentifiable2), group2.map { it.identifiableObject })
    }

    @Test
    fun getMatchingWorks() {
        val item1 = repo.add(tag = "tag1") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        repo.add(tag = "tag2") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        val item2 = repo.add(tag = "tag1") { newUUID, position ->
            MyIdentifiable(newUUID, position)
        }

        val matches = repo.getMatching { it.tag == "tag1" }
        assertEquals(listOf(item1, item2), matches.map { it.identifiableObject })
    }

    private data class MyIdentifiable(override val uuid: UUID, val position: Long) : Identifiable
}
