package com.threadmap.intellij.psi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EndpointLabelTest {
    @Test fun `maps annotation short names to verbs`() {
        assertEquals("GET", EndpointLabel.verb("GetMapping"))
        assertEquals("POST", EndpointLabel.verb("PostMapping"))
        assertEquals("PUT", EndpointLabel.verb("PutMapping"))
        assertEquals("DELETE", EndpointLabel.verb("DeleteMapping"))
        assertEquals("PATCH", EndpointLabel.verb("PatchMapping"))
        assertEquals("ANY", EndpointLabel.verb("RequestMapping"))
    }

    @Test fun `joins class and method path with single slashes`() {
        assertEquals("/api/merchants/recruit", EndpointLabel.path("/api/merchants", "/recruit"))
        assertEquals("/api/merchants/recruit", EndpointLabel.path("api/merchants", "recruit"))
    }

    @Test fun `handles missing parts`() {
        assertEquals("/recruit", EndpointLabel.path(null, "recruit"))
        assertEquals("/api", EndpointLabel.path("api", null))
        assertEquals("/", EndpointLabel.path(null, null))
    }
}
