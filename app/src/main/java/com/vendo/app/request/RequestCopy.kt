package com.vendo.app.request

import com.vendo.core.designsystem.components.VendoTone

/** Every human-facing string this screen needs for a raw backend code
 * (status/intent/flag/match method), in one place, so nothing on the
 * screen itself ever prints a snake_case token or a bare score. Backend
 * source: app/schemas/enums.py (status/intent/match method), and every
 * flags.append(...)/line_flags.append(...) call across
 * app/services/draft_builder.py, app/pipeline.py, and
 * app/services/prior_order.py's ambiguity reasons (surfaced here with a
 * "reorder_" prefix by draft_builder.build_reorder). An unrecognized code
 * still gets a readable (not raw) fallback rather than being hidden -
 * VeNdO's rule is "never hide uncertainty", not "never show what we don't
 * have copy for yet". */

data class StatusPresentation(val label: String, val tone: VendoTone)

fun requestStatusPresentation(status: String): StatusPresentation = when (status) {
    "new" -> StatusPresentation("New", VendoTone.Info)
    "in_review" -> StatusPresentation("In Review", VendoTone.Info)
    "callback" -> StatusPresentation("Needs Callback", VendoTone.Warning)
    "rejected" -> StatusPresentation("Rejected", VendoTone.Neutral)
    "committed" -> StatusPresentation("Committed", VendoTone.Positive)
    else -> StatusPresentation(humanize(status), VendoTone.Neutral)
}

fun orderTypeLabel(primaryIntent: String): String = when (primaryIntent) {
    "add_order" -> "New Order"
    "repeat_order" -> "Repeat Order"
    "repeat_order_adjusted" -> "Adjusted Repeat"
    "return_order" -> "Return"
    "other" -> "Not an Order"
    else -> humanize(primaryIntent)
}

/** True when VeNdO didn't recognize this recording as an order at all
 * (spec section 36) - the reviewer shouldn't be shown an empty item list
 * and an Accept button as if something just needs fixing. */
fun isNonOrder(primaryIntent: String): Boolean = primaryIntent == "other"

fun changeLabel(change: String?): String? = when (change) {
    "add" -> "Added"
    "remove" -> "Removed"
    "increase" -> "Increased"
    "decrease" -> "Decreased"
    else -> null
}

data class FlagPresentation(val message: String, val tone: VendoTone)

private val REQUEST_FLAG_COPY: Map<String, FlagPresentation> = mapOf(
    "audio_too_long" to FlagPresentation(
        "This recording is longer than VeNdO can process as one order.", VendoTone.Danger),
    "unrecognized_command" to FlagPresentation(
        "VeNdO couldn't understand this recording as an order.", VendoTone.Danger),
    "no_lines" to FlagPresentation(
        "No items were understood from this recording.", VendoTone.Danger),
    "customer_ambiguous" to FlagPresentation(
        "Multiple customers could match - confirm which one.", VendoTone.Danger),
    "customer_not_found" to FlagPresentation(
        "We couldn't identify the customer.", VendoTone.Danger),
    "return_order_reference_not_found" to FlagPresentation(
        "We couldn't find the order this return refers to.", VendoTone.Danger),
    "reorder_no_open_orders" to FlagPresentation(
        "This customer has no open orders to repeat.", VendoTone.Danger),
    "reorder_multiple_open_orders" to FlagPresentation(
        "This customer has more than one open order - it's unclear which to repeat.",
        VendoTone.Danger),
    "reorder_no_orders" to FlagPresentation(
        "This customer has no past orders to repeat.", VendoTone.Danger),
    "reorder_customer_not_resolved" to FlagPresentation(
        "The customer couldn't be identified, so no order could be repeated.",
        VendoTone.Danger),
    "reorder_no_date_reference" to FlagPresentation(
        "No date was given for which order to repeat.", VendoTone.Warning),
    "reorder_unparseable_date" to FlagPresentation(
        "The spoken date couldn't be understood.", VendoTone.Warning),
    "reorder_no_order_on_date" to FlagPresentation(
        "No order was found for that date.", VendoTone.Warning),
    "reorder_no_order_reference" to FlagPresentation(
        "No order number was given to repeat.", VendoTone.Warning),
    "reorder_invalid_reference" to FlagPresentation(
        "The order number mentioned wasn't understood.", VendoTone.Warning),
    "reorder_order_not_found" to FlagPresentation(
        "That order number couldn't be found for this customer.", VendoTone.Warning),
    "reorder_multiple_order_types" to FlagPresentation(
        "That order number matches more than one order.", VendoTone.Warning),
)

private val PARSE_ERROR_COPY: Map<String, String> = mapOf(
    "COMMAND_START_NOT_FOUND" to "VeNdO couldn't find the start of an order in this recording.",
    "CUSTOMER_DELIMITER_NOT_FOUND" to "VeNdO couldn't tell which part of the recording named the customer.",
    "ITEMS_DELIMITER_NOT_FOUND" to "VeNdO couldn't tell which part of the recording listed the items.",
    "COMMAND_END_NOT_FOUND" to "The recording seems to be missing its ending.",
    "NO_ITEMS_FOUND" to "No items were understood from this recording.",
    "ITEM_QUANTITY_NOT_FOUND" to "A quantity was missing for one of the items.",
    "ORDER_REFERENCE_NOT_FOUND" to "VeNdO couldn't tell which order was being referred to.",
    "REORDER_MODE_NOT_FOUND" to "VeNdO couldn't tell how this order should be repeated.",
)

/** Request-level flags as ready-to-show (message, tone) pairs, most severe
 * first, de-duplicated against the parse-error code that often rides
 * alongside "unrecognized_command" (app/pipeline.py appends both). */
fun requestFlagPresentations(flags: List<String>): List<FlagPresentation> {
    val seen = LinkedHashSet<FlagPresentation>()
    for (flag in flags) {
        val known = REQUEST_FLAG_COPY[flag]
        val parseError = PARSE_ERROR_COPY[flag]
        seen.add(
            known ?: parseError?.let { FlagPresentation(it, VendoTone.Danger) }
            ?: FlagPresentation(humanize(flag), VendoTone.Warning),
        )
    }
    return seen.sortedByDescending { it.tone.severity() }
}

private fun VendoTone.severity(): Int = when (this) {
    VendoTone.Danger -> 3
    VendoTone.Warning -> 2
    VendoTone.Info -> 1
    VendoTone.Positive, VendoTone.Neutral -> 0
}

private val LINE_FLAG_COPY: Map<String, String> = mapOf(
    "ambiguous_catalogue_match" to "Multiple products could match what was said.",
    "unknown_alias" to "We couldn't identify this product.",
    "quantity_parse_error" to "We couldn't work out the quantity.",
    "item_not_in_order" to "This item wasn't part of the original order.",
    "item_ambiguous_in_order" to "More than one item on the original order could match this - please pick the right one.",
)

fun lineFlagMessage(flag: String): String = LINE_FLAG_COPY[flag] ?: humanize(flag)

fun matchMethodLabel(method: String?): String? = when (method) {
    "exact" -> "Exact match"
    "alias" -> "Known alias"
    "fuzzy" -> "Approximate match"
    "substring" -> "Found in speech"
    "prior_order" -> "From a previous order"
    "manual" -> "Manually chosen"
    "offline_cache" -> "From offline cache"
    else -> null
}

/** score in [0,1] - never shown as a number, only as one of these three
 * words (spec: never "RapidFuzz score: 87"). */
fun candidateQualityLabel(score: Double): String = when {
    score >= 0.9 -> "Good match"
    score >= 0.75 -> "Possible match"
    else -> "Weak match"
}

private fun humanize(token: String): String =
    token.replace('_', ' ').replaceFirstChar { it.uppercase() }
