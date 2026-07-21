package br.com.pld.customeranalysis.workbenchapi

import br.com.pld.customeranalysis.common.PrefixedUlid
import br.com.pld.customeranalysis.identityaccess.Actor
import br.com.pld.customeranalysis.identityaccess.ActorRole
import org.springframework.stereotype.Component

@Component
class ActorResolver {
    fun actor(actorId: String?, actorRole: String?): Actor = Actor(
        id = actorId?.takeIf(String::isNotBlank) ?: "dev-system",
        role = actorRole?.takeIf(String::isNotBlank)?.let(ActorRole::valueOf) ?: ActorRole.SYSTEM,
    )

    fun correlationId(headerValue: String?): String = headerValue?.takeIf(String::isNotBlank) ?: PrefixedUlid.ulid()
}
