package com.conduit.chainservice.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import jakarta.annotation.PostConstruct

/**
 * Loads and caches contract ABIs from JSON files.
 * Provides type-safe access to contract functions and events.
 */
@Component
class AbiLoader {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    private lateinit var escrowContractAbi: JsonNode
    private lateinit var factoryContractAbi: JsonNode

    @PostConstruct
    fun loadAbis() {
        logger.info("Loading contract ABIs from resources...")

        escrowContractAbi = loadAbiFromResource("abi/EscrowContract.abi.json")
        factoryContractAbi = loadAbiFromResource("abi/EscrowContractFactory.abi.json")

        logger.info("Successfully loaded EscrowContract ABI with ${escrowContractAbi.size()} entries")
        logger.info("Successfully loaded EscrowContractFactory ABI with ${factoryContractAbi.size()} entries")
    }

    private fun loadAbiFromResource(resourcePath: String): JsonNode {
        return try {
            val resource = ClassPathResource(resourcePath)
            resource.inputStream.use { inputStream ->
                objectMapper.readTree(inputStream)
            }
        } catch (e: Exception) {
            logger.error("Failed to load ABI from $resourcePath", e)
            throw RuntimeException("Failed to load ABI from $resourcePath", e)
        }
    }

    /**
     * Get the full ABI for EscrowContract
     */
    fun getEscrowContractAbi(): String {
        return objectMapper.writeValueAsString(escrowContractAbi)
    }

    /**
     * Get the full ABI for EscrowContractFactory
     */
    fun getFactoryContractAbi(): String {
        return objectMapper.writeValueAsString(factoryContractAbi)
    }

    /**
     * Get function definition from ABI by name
     */
    fun getFunctionFromAbi(abi: JsonNode, functionName: String): JsonNode? {
        return abi.find {
            it.get("type")?.asText() == "function" &&
            it.get("name")?.asText() == functionName
        }
    }

    /**
     * Get event definition from ABI by name
     */
    fun getEventFromAbi(abi: JsonNode, eventName: String): JsonNode? {
        return abi.find {
            it.get("type")?.asText() == "event" &&
            it.get("name")?.asText() == eventName
        }
    }

    /**
     * Represents a function output parameter from ABI
     */
    data class OutputParameter(
        val name: String,
        val type: String,
        val typeReference: TypeReference<out Type<*>>
    )

    /**
     * Get getContractInfo function output types and names based on current ABI
     */
    fun getContractInfoOutputs(): List<OutputParameter> {
        val function = getFunctionFromAbi(escrowContractAbi, "getContractInfo")
            ?: throw IllegalStateException("getContractInfo function not found in ABI")

        val outputs = function.get("outputs") ?: throw IllegalStateException("No outputs in getContractInfo")

        return outputs.map { output ->
            val type = output.get("type")?.asText()
                ?: throw IllegalStateException("No type in output")
            val name = output.get("name")?.asText() ?: ""

            val typeReference = when (type) {
                "address" -> TypeReference.create(Address::class.java)
                "uint256" -> TypeReference.create(Uint256::class.java)
                "uint8" -> TypeReference.create(Uint8::class.java)
                "string" -> TypeReference.create(Utf8String::class.java)
                "bool" -> TypeReference.create(org.web3j.abi.datatypes.Bool::class.java)
                else -> throw IllegalStateException("Unsupported type: $type")
            }

            OutputParameter(name, type, typeReference)
        }
    }

    /**
     * Get getContractInfo function output types based on current ABI
     */
    fun getContractInfoOutputTypes(): List<TypeReference<out Type<*>>> {
        return getContractInfoOutputs().map { it.typeReference }
    }

    /**
     * Get createContract function from factory ABI
     */
    fun getCreateContractInputTypes(): List<TypeReference<out Type<*>>> {
        val function = getFunctionFromAbi(factoryContractAbi, "createContract")
            ?: throw IllegalStateException("createContract function not found in factory ABI")

        val inputs = function.get("inputs") ?: throw IllegalStateException("No inputs in createContract")

        return inputs.map { input ->
            val type = input.get("type")?.asText()
                ?: throw IllegalStateException("No type in input")

            when (type) {
                "address" -> TypeReference.create(Address::class.java)
                "uint256" -> TypeReference.create(Uint256::class.java)
                "uint8" -> TypeReference.create(Uint8::class.java)
                "string" -> TypeReference.create(Utf8String::class.java)
                "bool" -> TypeReference.create(org.web3j.abi.datatypes.Bool::class.java)
                else -> throw IllegalStateException("Unsupported type: $type")
            }
        }
    }

    /**
     * Represents an event parameter from ABI
     */
    data class EventParameter(
        val name: String,
        val type: String,
        val indexed: Boolean,
        val typeReference: TypeReference<out Type<*>>
    )

    /**
     * Get event definition from ABI by name
     */
    fun getEventDefinition(eventName: String, fromFactory: Boolean = false): List<EventParameter> {
        val abi = if (fromFactory) factoryContractAbi else escrowContractAbi
        val event = getEventFromAbi(abi, eventName)
            ?: throw IllegalStateException("Event $eventName not found in ABI")

        val inputs = event.get("inputs") ?: return emptyList()

        return inputs.map { input ->
            val type = input.get("type")?.asText()
                ?: throw IllegalStateException("No type in event input")
            val name = input.get("name")?.asText() ?: ""
            val indexed = input.get("indexed")?.asBoolean() ?: false

            val typeReference = when (type) {
                "address" -> TypeReference.create(Address::class.java, indexed)
                "uint256" -> TypeReference.create(Uint256::class.java, indexed)
                "uint8" -> TypeReference.create(org.web3j.abi.datatypes.generated.Uint8::class.java, indexed)
                "string" -> TypeReference.create(Utf8String::class.java, indexed)
                "bool" -> TypeReference.create(org.web3j.abi.datatypes.Bool::class.java, indexed)
                else -> throw IllegalStateException("Unsupported type: $type")
            }

            EventParameter(name, type, indexed, typeReference)
        }
    }

    /**
     * Create Web3j Event object from ABI
     */
    fun createEvent(eventName: String, fromFactory: Boolean = false): org.web3j.abi.datatypes.Event {
        val parameters = getEventDefinition(eventName, fromFactory)
        return org.web3j.abi.datatypes.Event(
            eventName,
            parameters.map { it.typeReference }
        )
    }

    /**
     * Get event topics for event parsing
     */
    fun getEventSignature(eventName: String): String {
        val event = getEventFromAbi(escrowContractAbi, eventName)
            ?: throw IllegalStateException("Event $eventName not found in ABI")

        val inputs = event.get("inputs") ?: return "$eventName()"

        val paramTypes = inputs.joinToString(",") { it.get("type")?.asText() ?: "" }
        return "$eventName($paramTypes)"
    }

    /**
     * Represents a function input parameter from ABI
     */
    data class InputParameter(
        val name: String,
        val type: String
    )

    /**
     * Get function input parameters from ABI
     * @param functionName The name of the function
     * @param fromFactory Whether to look in factory ABI (true) or escrow contract ABI (false)
     */
    fun getFunctionInputs(functionName: String, fromFactory: Boolean = false): List<InputParameter> {
        val abi = if (fromFactory) factoryContractAbi else escrowContractAbi
        val function = getFunctionFromAbi(abi, functionName)
            ?: throw IllegalStateException("Function $functionName not found in ${if (fromFactory) "factory" else "escrow"} ABI")

        val inputs = function.get("inputs") ?: return emptyList()

        return inputs.map { input ->
            val type = input.get("type")?.asText()
                ?: throw IllegalStateException("No type in input")
            val name = input.get("name")?.asText() ?: ""
            InputParameter(name, type)
        }
    }

    /**
     * Build a Web3j Function object for a given function name with provided input values
     * @param functionName The name of the function in the ABI
     * @param inputValues The actual values to encode (must match the ABI input types in order)
     * @param fromFactory Whether to look in factory ABI (true) or escrow contract ABI (false)
     * @param outputTypes Optional output types for view functions
     */
    fun buildFunction(
        functionName: String,
        inputValues: List<Type<*>>,
        fromFactory: Boolean = false,
        outputTypes: List<TypeReference<out Type<*>>> = emptyList()
    ): org.web3j.abi.datatypes.Function {
        val inputs = getFunctionInputs(functionName, fromFactory)

        // Validate that input values match expected count
        if (inputValues.size != inputs.size) {
            throw IllegalArgumentException(
                "Function $functionName expects ${inputs.size} inputs but got ${inputValues.size}"
            )
        }

        // Build and return the Function object
        return org.web3j.abi.datatypes.Function(
            functionName,
            inputValues,
            outputTypes
        )
    }

    /**
     * Get function signature from ABI (e.g., "createEscrowContract(address,address,address,uint256,uint256,string)")
     */
    fun getFunctionSignature(functionName: String, fromFactory: Boolean = false): String {
        val inputs = getFunctionInputs(functionName, fromFactory)
        val paramTypes = inputs.joinToString(",") { it.type }
        return "$functionName($paramTypes)"
    }

    /**
     * Get function output types from ABI
     * @param functionName The name of the function
     * @param fromFactory Whether to look in factory ABI (true) or escrow contract ABI (false)
     */
    fun getFunctionOutputTypes(functionName: String, fromFactory: Boolean = false): List<TypeReference<out Type<*>>> {
        val abi = if (fromFactory) factoryContractAbi else escrowContractAbi
        val function = getFunctionFromAbi(abi, functionName)
            ?: throw IllegalStateException("Function $functionName not found in ${if (fromFactory) "factory" else "escrow"} ABI")

        val outputs = function.get("outputs") ?: return emptyList()

        return outputs.map { output ->
            val type = output.get("type")?.asText()
                ?: throw IllegalStateException("No type in output")

            when (type) {
                "address" -> TypeReference.create(Address::class.java)
                "uint256" -> TypeReference.create(Uint256::class.java)
                "uint8" -> TypeReference.create(Uint8::class.java)
                "string" -> TypeReference.create(Utf8String::class.java)
                "bool" -> TypeReference.create(org.web3j.abi.datatypes.Bool::class.java)
                else -> throw IllegalStateException("Unsupported output type: $type")
            }
        }
    }
}
