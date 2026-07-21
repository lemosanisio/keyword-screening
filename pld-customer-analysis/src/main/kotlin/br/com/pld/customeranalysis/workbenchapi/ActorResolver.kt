package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class ActorResolver {
    fun commandActor(actorId: String?, actorRole: String?): Actor {
        val resolvedActorId = actorId?.takeIf(String::isNotBlank)
            ?: throw badRequest("X-Actor-Id header is required")
        val resolvedRole = actorRole?.takeIf(String::isNotBlank)
            ?: throw badRequest("X-Actor-Role header is required")

        return Actor(
            id = resolvedActorId,
            role = runCatching { ActorRole.valueOf(resolvedRole) }
                .getOrElse { throw badRequest("X-Actor-Role header is invalid") },
        )
    }

    fun correlationId(headerValue: String?): String = headerValue?.takeIf(String::isNotBlank) ?: PrefixedUlid.ulid()

    private fun badRequest(reason: String): ResponseStatusException = ResponseStatusException(HttpStatus.BAD_REQUEST, reason)
}
