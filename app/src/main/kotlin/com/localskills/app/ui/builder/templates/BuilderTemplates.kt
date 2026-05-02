package com.localskills.app.ui.builder.templates

import com.localskills.app.engine.builder.DraftField
import com.localskills.app.engine.builder.DraftLimits
import com.localskills.app.engine.builder.DraftPattern
import com.localskills.app.engine.builder.DraftRule
import com.localskills.app.engine.builder.ManifestDraft
import com.localskills.app.skill.manifest.FieldType
import com.localskills.app.skill.manifest.InputKind
import com.localskills.app.skill.manifest.RuleAction

/**
 * Pre-baked starting points for the Skill Builder. The templates exist only
 * to seed a fresh [ManifestDraft] — they are not skills themselves and are
 * never persisted in the database.
 */
data class BuilderTemplate(
    val key: String,
    val title: String,
    val tagline: String,
    val factory: () -> ManifestDraft,
)

object BuilderTemplates {

    val all: List<BuilderTemplate> = listOf(
        BuilderTemplate(
            key = "blank",
            title = "Blank skill",
            tagline = "Start from an empty form",
            factory = ::blank,
        ),
        BuilderTemplate(
            key = "expense",
            title = "Expense parser",
            tagline = "Pull merchant, amount, date from a receipt or SMS",
            factory = ::expense,
        ),
        BuilderTemplate(
            key = "notes",
            title = "Notes summarizer",
            tagline = "Distill long notes into title, summary, action items",
            factory = ::notesSummarizer,
        ),
        BuilderTemplate(
            key = "recipe",
            title = "Recipe extractor",
            tagline = "Get title, ingredients, steps from any text or image",
            factory = ::recipeExtractor,
        ),
    )

    fun byKey(key: String): BuilderTemplate? = all.firstOrNull { it.key == key }

    private fun blank(): ManifestDraft = ManifestDraft()

    private fun expense(): ManifestDraft = ManifestDraft(
        id = "expense-parser.v1",
        name = "Expense Parser",
        description = "Extracts merchant, amount, date, and category from a transaction.",
        inputs = setOf(InputKind.TEXT, InputKind.IMAGE),
        instruction = "Extract merchant name, total amount in INR, transaction date (ISO-8601), and a short category (food, fuel, travel, bills, other).",
        outputFields = listOf(
            DraftField("merchant", FieldType.TEXT),
            DraftField("amount", FieldType.NUMBER),
            DraftField("currency", FieldType.CURRENCY),
            DraftField("txn_date", FieldType.DATE),
            DraftField("category", FieldType.TEXT),
        ),
        patterns = listOf(
            DraftPattern("amount_inr", "(?i)(?:rs\\.?|inr|₹)\\s?([0-9,]+(?:\\.[0-9]{1,2})?)"),
        ),
        rules = listOf(
            DraftRule(
                condition = "amount > 5000",
                action = RuleAction.NOTIFY,
                title = "Large expense",
                body = "Logged a large expense at {merchant}",
            ),
        ),
        limits = DraftLimits(),
    )

    private fun notesSummarizer(): ManifestDraft = ManifestDraft(
        id = "notes-summarizer.v1",
        name = "Notes Summarizer",
        description = "Turns rambling notes into a tight summary with action items.",
        inputs = setOf(InputKind.TEXT),
        instruction = "Read the notes and return a short title, a 2-3 sentence summary, and up to 5 action items as a single newline-joined string.",
        outputFields = listOf(
            DraftField("title", FieldType.TEXT),
            DraftField("summary", FieldType.TEXT),
            DraftField("action_items", FieldType.TEXT),
        ),
        rules = emptyList(),
        limits = DraftLimits(),
    )

    private fun recipeExtractor(): ManifestDraft = ManifestDraft(
        id = "recipe-extractor.v1",
        name = "Recipe Extractor",
        description = "Pulls structured recipe data from text or images.",
        inputs = setOf(InputKind.TEXT, InputKind.IMAGE),
        instruction = "Extract the recipe title, a newline-joined list of ingredients, a newline-joined list of numbered steps, total time in minutes, and number of servings.",
        outputFields = listOf(
            DraftField("title", FieldType.TEXT),
            DraftField("ingredients", FieldType.TEXT),
            DraftField("steps", FieldType.TEXT),
            DraftField("total_minutes", FieldType.NUMBER),
            DraftField("servings", FieldType.NUMBER),
        ),
        rules = emptyList(),
        limits = DraftLimits(),
    )
}
