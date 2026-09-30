package pt.aguiarvieira.xmuks.feature.settings

import pt.aguiarvieira.xmuks.core.data.prefs.Pref
import pt.aguiarvieira.xmuks.core.data.prefs.Prefs

/** Where a preference is listed. */
internal enum class Section(
    val title: Int,
) {
    Sending(R.string.section_sending),
    Timeline(R.string.section_timeline),
    Media(R.string.section_media),
    RoomList(R.string.section_room_list),
}

/** A preference as the settings screen shows it. */
internal class PrefEntry(
    val pref: Pref<*>,
    val title: Int,
    val description: Int,
    val section: Section,
    /** Kept in sync with gomuks web, but nothing in xmuks reads it yet. */
    val inXmuks: Boolean = true,
)

/** Labels for a choice's values (the rest show as they are, e.g. code themes and GIF services). */
internal val CHOICE_LABELS: Map<String?, Int> =
    mapOf(
        "default" to R.string.choice_default,
        "compact" to R.string.choice_compact,
        "spacious" to R.string.choice_spacious,
    )

/** Every preference xmuks lists, in order, grouped by [Section]. */
@Suppress("MaxLineLength") // one row per preference reads better than wrapped
internal val ENTRIES: List<PrefEntry> =
    listOf(
        PrefEntry(
            Prefs.sendReadReceipts,
            R.string.pref_send_read_receipts,
            R.string.pref_send_read_receipts_desc,
            Section.Sending
        ),
        PrefEntry(
            Prefs.sendTypingNotifications,
            R.string.pref_send_typing_notifications,
            R.string.pref_send_typing_notifications_desc,
            Section.Sending
        ),
        PrefEntry(
            Prefs.sendBundledUrlPreviews,
            R.string.pref_send_bundled_url_previews,
            R.string.pref_send_bundled_url_previews_desc,
            Section.Sending,
            inXmuks = false
        ),
        PrefEntry(Prefs.uploadDialog, R.string.pref_upload_dialog, R.string.pref_upload_dialog_desc, Section.Sending),
        PrefEntry(
            Prefs.hideFingerprint,
            R.string.pref_hide_fingerprint,
            R.string.pref_hide_fingerprint_desc,
            Section.Sending
        ),
        PrefEntry(
            Prefs.displayReadReceipts,
            R.string.pref_display_read_receipts,
            R.string.pref_display_read_receipts_desc,
            Section.Timeline
        ),
        PrefEntry(
            Prefs.showHiddenEvents,
            R.string.pref_show_hidden_events,
            R.string.pref_show_hidden_events_desc,
            Section.Timeline
        ),
        PrefEntry(
            Prefs.showRedactedEvents,
            R.string.pref_show_redacted_events,
            R.string.pref_show_redacted_events_desc,
            Section.Timeline
        ),
        PrefEntry(
            Prefs.showMembershipEvents,
            R.string.pref_show_membership_events,
            R.string.pref_show_membership_events_desc,
            Section.Timeline
        ),
        PrefEntry(
            Prefs.showProfileChanges,
            R.string.pref_show_profile_changes,
            R.string.pref_show_profile_changes_desc,
            Section.Timeline
        ),
        PrefEntry(
            Prefs.showDateSeparators,
            R.string.pref_show_date_separators,
            R.string.pref_show_date_separators_desc,
            Section.Timeline
        ),
        PrefEntry(
            Prefs.smallThreads,
            R.string.pref_small_threads,
            R.string.pref_small_threads_desc,
            Section.Timeline
        ),
        PrefEntry(
            Prefs.renderUrlPreviews,
            R.string.pref_render_url_previews,
            R.string.pref_render_url_previews_desc,
            Section.Timeline,
            inXmuks = false
        ),
        PrefEntry(
            Prefs.codeBlockLineWrap,
            R.string.pref_code_block_line_wrap,
            R.string.pref_code_block_line_wrap_desc,
            Section.Timeline
        ),
        PrefEntry(
            Prefs.showMediaPreviews,
            R.string.pref_show_media_previews,
            R.string.pref_show_media_previews_desc,
            Section.Media
        ),
        PrefEntry(
            Prefs.autoplayGifs,
            R.string.pref_autoplay_gifs,
            R.string.pref_autoplay_gifs_desc,
            Section.Media
        ),
        PrefEntry(
            Prefs.showInlineImages,
            R.string.pref_show_inline_images,
            R.string.pref_show_inline_images_desc,
            Section.Media
        ),
        PrefEntry(
            Prefs.maxImageWidth,
            R.string.pref_max_image_width,
            R.string.pref_max_image_width_desc,
            Section.Media
        ),
        PrefEntry(
            Prefs.showRoomEmojiPacks,
            R.string.pref_show_room_emoji_packs,
            R.string.pref_show_room_emoji_packs_desc,
            Section.Media
        ),
        PrefEntry(
            Prefs.roomListPreview,
            R.string.pref_room_list_preview,
            R.string.pref_room_list_preview_desc,
            Section.RoomList
        ),
        PrefEntry(
            Prefs.roomListStyle,
            R.string.pref_room_list_style,
            R.string.pref_room_list_style_desc,
            Section.RoomList
        ),
        PrefEntry(
            Prefs.pinFavorites,
            R.string.pref_pin_favorites,
            R.string.pref_pin_favorites_desc,
            Section.RoomList,
            inXmuks = false
        ),
        PrefEntry(
            Prefs.pinLowPriority,
            R.string.pref_pin_low_priority,
            R.string.pref_pin_low_priority_desc,
            Section.RoomList,
            inXmuks = false
        ),
        PrefEntry(
            Prefs.muteLowPriority,
            R.string.pref_mute_low_priority,
            R.string.pref_mute_low_priority_desc,
            Section.RoomList,
            inXmuks = false
        ),
        PrefEntry(
            Prefs.alphabeticalOrder,
            R.string.pref_alphabetical_order,
            R.string.pref_alphabetical_order_desc,
            Section.RoomList
        ),
    )
