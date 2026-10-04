package com.hermes.bridge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatApiTest {

    @Test
    fun parsesOpenAiStyleModelList() {
        val json = """{"object":"list","data":[{"id":"glm-5.3"},{"id":"glm-air"}]}"""
        assertEquals(listOf("glm-5.3", "glm-air"), ChatApi.parseModelIds(json))
    }

    @Test
    fun parsesModelListWithExtraMetadataFields() {
        // Real servers include extra fields (created, owned_by) around id.
        val json =
            """{"object":"list","data":[{"id":"glm-5.3","created":1,"owned_by":"x"},""" +
                """{"id":"glm-air","created":2}]}"""
        assertEquals(listOf("glm-5.3", "glm-air"), ChatApi.parseModelIds(json))
    }

    @Test
    fun returnsEmptyForUnparseableBody() {
        assertTrue(ChatApi.parseModelIds("not json at all").isEmpty())
    }
}
