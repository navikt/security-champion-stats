package navikt.appsec.securitychampionapp.integrations.postgress

import navikt.appsec.securitychampionapp.app.scoring.DeltaEligibleCategory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class DeltaEligibleCategoryRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val categoryMapper = RowMapper { rs, _ ->
        DeltaEligibleCategory(
            categoryId = rs.getInt("category_id"),
            categoryName = rs.getString("category_name"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }

    fun findAll(): List<DeltaEligibleCategory> =
        jdbcTemplate.query(
            """
                SELECT category_id, category_name, created_at
                FROM delta_eligible_categories
                ORDER BY LOWER(category_name), category_id
            """.trimIndent(),
            categoryMapper,
        )

    @Transactional
    fun add(categoryId: Int, categoryName: String, actorNavNoEmail: String): DeltaEligibleCategory {
        val category = jdbcTemplate.queryForObject(
            """
                INSERT INTO delta_eligible_categories (category_id, category_name, created_by_nav_no_email)
                VALUES (?, ?, ?)
                RETURNING category_id, category_name, created_at
            """.trimIndent(),
            categoryMapper,
            categoryId,
            categoryName,
            actorNavNoEmail,
        )!!
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (actor_nav_no_email, action, before_values, after_values)
                VALUES (?, 'DELTA_ELIGIBLE_CATEGORY_ADDED', '{}'::jsonb, jsonb_build_object(
                    'deltaCategoryId', CAST(? AS INTEGER), 'deltaCategoryName', ?
                ))
            """.trimIndent(),
            actorNavNoEmail,
            category.categoryId,
            category.categoryName,
        )
        return category
    }

    @Transactional
    fun remove(categoryId: Int, actorNavNoEmail: String): Boolean {
        val category = jdbcTemplate.query(
            """
                DELETE FROM delta_eligible_categories
                WHERE category_id = ?
                RETURNING category_id, category_name, created_at
            """.trimIndent(),
            categoryMapper,
            categoryId,
        ).firstOrNull() ?: return false
        jdbcTemplate.update(
            """
                INSERT INTO program_scoring_audit (actor_nav_no_email, action, before_values, after_values)
                VALUES (?, 'DELTA_ELIGIBLE_CATEGORY_REMOVED', jsonb_build_object(
                    'deltaCategoryId', CAST(? AS INTEGER), 'deltaCategoryName', ?
                ), '{}'::jsonb)
            """.trimIndent(),
            actorNavNoEmail,
            category.categoryId,
            category.categoryName,
        )
        return true
    }
}
