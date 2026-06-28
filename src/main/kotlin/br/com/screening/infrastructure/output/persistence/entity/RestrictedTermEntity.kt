package br.com.screening.infrastructure.output.persistence.entity

import br.com.screening.domain.model.Category
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "restricted_term")
class RestrictedTermEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val term: String = "",

    @Enumerated(EnumType.STRING)
    val category: Category = Category.AML,

    val active: Boolean = true,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
