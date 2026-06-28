package br.com.screening.domain.port

/**
 * Porta de saída para o classificador LLM.
 * Abstrai o provedor de LLM, permitindo substituição sem impacto no domínio.
 */
interface LlmClassifierPort {

    /**
     * Envia o prompt ao LLM e retorna a resposta bruta.
     * Em caso de falha (timeout, erro HTTP, JSON inválido), retorna LlmResponse com erro.
     */
    fun classify(prompt: String): LlmResponse
}

/**
 * Resposta do LLM, encapsulando sucesso ou falha.
 */
data class LlmResponse(
    val classification: String?,
    val confidence: Double?,
    val reason: String?,
    val rawResponse: String?,
    val success: Boolean,
    val errorMessage: String? = null
)
