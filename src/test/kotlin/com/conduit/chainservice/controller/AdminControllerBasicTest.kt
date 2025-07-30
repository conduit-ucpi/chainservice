package com.conduit.chainservice.controller

import com.conduit.chainservice.escrow.EscrowTransactionService
import com.conduit.chainservice.escrow.models.AdminResolveContractRequest
import com.conduit.chainservice.service.ContractQueryService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AdminControllerBasicTest {

    private lateinit var mockMvc: MockMvc
    
    @Mock
    private lateinit var escrowTransactionService: EscrowTransactionService
    
    @Mock
    private lateinit var contractQueryService: ContractQueryService
    
    private lateinit var adminController: AdminController
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        adminController = AdminController(escrowTransactionService, contractQueryService)
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build()
    }

    @Test
    fun `resolveContract should return 403 for non-admin users`() {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val request = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Test resolution"
        )

        mockMvc.perform(
            post("/api/admin/contracts/$contractAddress/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("userType", "user")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Access denied - admin privileges required"))
    }

    @Test
    fun `resolveContract should return 400 for negative percentages`() {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val request = AdminResolveContractRequest(
            buyerPercentage = -10.0,
            sellerPercentage = 110.0,
            resolutionNote = "Invalid resolution"
        )

        mockMvc.perform(
            post("/api/admin/contracts/$contractAddress/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("userType", "admin")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Percentages cannot be negative"))
    }

    @Test
    fun `resolveContract should return 400 when percentages don't sum to 100`() {
        val contractAddress = "0x1234567890abcdef1234567890abcdef12345678"
        val request = AdminResolveContractRequest(
            buyerPercentage = 50.0,
            sellerPercentage = 40.0,
            resolutionNote = "Invalid percentages"
        )

        mockMvc.perform(
            post("/api/admin/contracts/$contractAddress/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("userType", "admin")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Percentages must sum to 100"))
    }

    @Test
    fun `resolveContract should return 400 for non-contract addresses`() {
        val invalidId = "invalid-contract-id"
        val request = AdminResolveContractRequest(
            buyerPercentage = 60.0,
            sellerPercentage = 40.0,
            resolutionNote = "Test resolution"
        )

        mockMvc.perform(
            post("/api/admin/contracts/$invalidId/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("userType", "admin")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Contract address lookup not implemented - please provide contract address directly"))
    }
}