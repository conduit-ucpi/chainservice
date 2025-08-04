package com.conduit.chainservice.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigInteger
import java.time.Instant

class ContractModelsTest {

    @Test
    fun `ContractInfo should create with all required fields`() {
        // Given
        val contractInfo = ContractInfo(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            buyer = "0x9876543210fedcba9876543210fedcba98765432",
            seller = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdef",
            amount = BigInteger.valueOf(1000000),
            expiryTimestamp = Instant.now().plusSeconds(3600).epochSecond,
            description = "Test contract",
            funded = true,
            status = ContractStatus.ACTIVE,
            createdAt = Instant.now()
        )

        // Then
        assertNotNull(contractInfo)
        assertEquals(ContractStatus.ACTIVE, contractInfo.status)
        assertTrue(contractInfo.funded)
        assertEquals(BigInteger.valueOf(1000000), contractInfo.amount)
    }



    @Test
    fun `ContractStatus enum should have all expected values`() {
        // When
        val statuses = ContractStatus.values()

        // Then
        assertEquals(6, statuses.size)
        assertTrue(statuses.contains(ContractStatus.CREATED))
        assertTrue(statuses.contains(ContractStatus.ACTIVE))
        assertTrue(statuses.contains(ContractStatus.EXPIRED))
        assertTrue(statuses.contains(ContractStatus.DISPUTED))
        assertTrue(statuses.contains(ContractStatus.RESOLVED))
        assertTrue(statuses.contains(ContractStatus.CLAIMED))
    }

    @Test
    fun `EventType enum should have all expected values`() {
        // When
        val eventTypes = EventType.values()

        // Then
        assertEquals(5, eventTypes.size)
        assertTrue(eventTypes.contains(EventType.CONTRACT_CREATED))
        assertTrue(eventTypes.contains(EventType.FUNDS_DEPOSITED))
        assertTrue(eventTypes.contains(EventType.DISPUTE_RAISED))
        assertTrue(eventTypes.contains(EventType.DISPUTE_RESOLVED))
        assertTrue(eventTypes.contains(EventType.FUNDS_CLAIMED))
    }

    @Test
    fun `ContractEvent should create with all required fields`() {
        // Given
        val contractEvent = ContractEvent(
            eventType = EventType.CONTRACT_CREATED,
            timestamp = Instant.now(),
            transactionHash = "0xabcdef1234567890",
            blockNumber = BigInteger.valueOf(1000000),
            data = mapOf("amount" to "1000000", "description" to "Test contract")
        )

        // Then
        assertNotNull(contractEvent)
        assertEquals(EventType.CONTRACT_CREATED, contractEvent.eventType)
        assertEquals("0xabcdef1234567890", contractEvent.transactionHash)
        assertEquals(BigInteger.valueOf(1000000), contractEvent.blockNumber)
        assertEquals(2, contractEvent.data.size)
    }

    @Test
    fun `ContractEventHistory should create with events list`() {
        // Given
        val events = listOf(
            ContractEvent(
                eventType = EventType.CONTRACT_CREATED,
                timestamp = Instant.now(),
                transactionHash = "0xabcdef1234567890",
                blockNumber = BigInteger.valueOf(1000000)
            )
        )
        val history = ContractEventHistory(
            contractAddress = "0x1234567890abcdef1234567890abcdef12345678",
            events = events
        )

        // Then
        assertNotNull(history)
        assertEquals("0x1234567890abcdef1234567890abcdef12345678", history.contractAddress)
        assertEquals(1, history.events.size)
        assertEquals(EventType.CONTRACT_CREATED, history.events[0].eventType)
    }
}