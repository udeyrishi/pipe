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
        val myIdentifiable = MyIdentifiable(UUID.randomUUID())
        val myIdentifiable2 = MyIdentifiable(UUID.randomUUID())
        repo.add(null, myIdentifiable)
        repo.add(null, myIdentifiable2)

        assertEquals(myIdentifiable, repo[myIdentifiable.uuid]?.identifiableObject)
        assertEquals(myIdentifiable2, repo[myIdentifiable2.uuid]?.identifiableObject)
    }

    @Test
    fun getByUuidWorksWhenUuidNotFound() {
        assertNull(repo[UUID.randomUUID()])
        val myIdentifiable = MyIdentifiable(UUID.randomUUID())
        repo.add(null, myIdentifiable)
        assertNotNull(repo[myIdentifiable.uuid])
        assertNull(repo[UUID.randomUUID()])
    }

    @Test
    fun getByTagWorksWhenTagFound() {
        val myIdentifiable = MyIdentifiable(UUID.randomUUID())
        val myIdentifiable2 = MyIdentifiable(UUID.randomUUID())
        val myIdentifiable3 = MyIdentifiable(UUID.randomUUID())

        repo.add("tag1", myIdentifiable)
        repo.add("tag2", myIdentifiable2)
        repo.add("tag1", myIdentifiable3)

        val group1 = repo["tag1"]
        val group2 = repo["tag2"]

        assertEquals(listOf(myIdentifiable, myIdentifiable3), group1.map { it.identifiableObject })
        assertEquals(listOf(myIdentifiable2), group2.map { it.identifiableObject })
    }

    @Test
    fun getByTagWorksWhenTagNotFound() {
        repo.add("tag1", MyIdentifiable(UUID.randomUUID()))
        repo.add("tag2", MyIdentifiable(UUID.randomUUID()))

        val group1 = repo["tag3"]

        assertEquals(listOf<MyIdentifiable>(), group1.map { it.identifiableObject })
    }

    @Test
    fun removeIfWorks() {
        val myIdentifiable = MyIdentifiable(UUID.randomUUID())
        val myIdentifiable2 = MyIdentifiable(UUID.randomUUID())

        repo.add("tag1", myIdentifiable)
        repo.add("tag2", myIdentifiable2)
        repo.add("tag1", MyIdentifiable(UUID.randomUUID()))

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
        val item1 = MyIdentifiable(UUID.randomUUID())
        val item2 = MyIdentifiable(UUID.randomUUID())

        repo.add("tag1", item1)
        repo.add("tag2", MyIdentifiable(UUID.randomUUID()))
        repo.add("tag1", item2)

        val matches = repo.getMatching { it.tag == "tag1" }
        assertEquals(listOf(item1, item2), matches.map { it.identifiableObject })
    }

    @Test(expected = DuplicateUUIDException::class)
    fun `Fails if duplicate UUIDs are used`() {
        val uuid = UUID.randomUUID()
        repo.add(null, MyIdentifiable(uuid))
        repo.add(null, MyIdentifiable(uuid))
    }

    @Test
    fun `clear clears everything`() {
        val items = (0 .. 2).map {
            MyIdentifiable(UUID.randomUUID())
        }
        items.forEach {
            repo.add(null, it)
        }

        assertEquals(3, repo.size)
        repo.clear()
        assertEquals(0, repo.size)
    }

    @Test
    fun `size works`() {
        val items = (0 .. 2).map {
            MyIdentifiable(UUID.randomUUID())
        }

        assertEquals(0, repo.size)
        items.forEach {
            repo.add(null, it)
        }

        assertEquals(3, repo.size)
    }

    @Test
    fun `items works`() {
        val items = (0 .. 2).map {
            MyIdentifiable(UUID.randomUUID())
        }
        items.forEach {
            repo.add(null, it)
        }

        assertEquals(items, repo.items.map { it.identifiableObject })
        repo.clear()
        assertEquals(listOf<Record<MyIdentifiable>>(), repo.items)
    }

    private data class MyIdentifiable(override val uuid: UUID) : Identifiable
}
