package io.github.tmarsteel.flyingnarrator.io

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

class CompactObjectArraySerializerTest : FreeSpec({
    val format = Json {
        serializersModule = SerializersModule {
            include(CompactObjectListSerializer.MODULE)
        }
        prettyPrint = true
        prettyPrintIndent = "    "
    }

    "base case" - {
        val data = TestWrapper(listOf(
            TestClass(1, "a", 10, TestNested(100)),
            TestClass(2, null, 20, TestNested(200)),
            TestClass(3, "c", 30, null),
        ))

        val expectedSerialForm = """
            {
                "objects": [
                    [
                        "a",
                        "b",
                        "c",
                        "d"
                    ],
                    [
                        1,
                        "a",
                        10,
                        {
                            "x": 100
                        }
                    ],
                    [
                        2,
                        null,
                        20,
                        {
                            "x": 200
                        }
                    ],
                    [
                        3,
                        "c",
                        30,
                        null
                    ]
                ]
            }
        """.trimIndent()

        "serialize" {
            format.encodeToString(data) shouldBe expectedSerialForm
        }

        "deserialize" {
            format.decodeFromString<TestWrapper>(expectedSerialForm) shouldBe data
        }
    }

    "empty array" - {
        val data = TestWrapper(emptyList())

        val expectedSerialForm = """
            {
                "objects": []
            }
        """.trimIndent()

        "serialize" {
            format.encodeToString(data) shouldBe expectedSerialForm
        }

        "deserialize" {
            format.decodeFromString<TestWrapper>(expectedSerialForm) shouldBe data
        }
    }
})

@Serializable
private data class TestWrapper(
    @Serializable(with = CompactObjectListSerializer::class)
    val objects: List<TestClass>
)

@Serializable
private data class TestClass(val a: Int, val b: String? = null, val c: Int, val d: TestNested? = null)

@Serializable
private data class TestNested(val x: Int)