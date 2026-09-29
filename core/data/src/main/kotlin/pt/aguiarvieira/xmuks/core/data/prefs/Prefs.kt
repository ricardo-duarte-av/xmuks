package pt.aguiarvieira.xmuks.core.data.prefs

import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope.Account
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope.Device
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope.RoomAccount
import pt.aguiarvieira.xmuks.core.data.prefs.PrefScope.RoomDevice

/**
 * gomuks' preferences (web/src/api/types/preferences), same keys, defaults and scopes, so a value
 * set in either client means the same in the other. Left out: those only meaningful in a browser
 * (custom CSS, window titles, favicon, title bar colours, pointer cursor, right-click menu,
 * keyboard shortcuts, SSE, web push, Element Call), the map provider (xmuks draws Google maps) and
 * the notification sound (Android's per-conversation settings own that).
 */
@Suppress("MagicNumber") // gomuks' defaults, as they are
object Prefs {
    private val ANY = listOf(RoomDevice, RoomAccount, Device, Account)
    private val GLOBAL = listOf(Device, Account)
    private val DEVICE_GLOBAL = listOf(Device)
    private val ROOM = listOf(RoomAccount, RoomDevice)

    val sendReadReceipts = Pref.Bool("send_read_receipts", true, ANY)
    val sendTypingNotifications = Pref.Bool("send_typing_notifications", true, ANY)
    val sendBundledUrlPreviews = Pref.Bool("send_bundled_url_previews", true, ANY)
    val displayReadReceipts = Pref.Bool("display_read_receipts", true, ANY)
    val showMediaPreviews = Pref.Bool("show_media_previews", false, ANY)
    val autoplayGifs = Pref.Bool("autoplay_gifs", false, ANY)
    val showInlineImages = Pref.Bool("show_inline_images", true, ANY)
    val showInviteAvatars = Pref.Bool("show_invite_avatars", false, GLOBAL)
    val codeBlockLineWrap = Pref.Bool("code_block_line_wrap", false, ANY)
    val codeBlockTheme = Pref.Choice("code_block_theme", "auto", ANY, CODE_BLOCK_THEMES)
    val showHiddenEvents = Pref.Bool("show_hidden_events", true, ANY)
    val showRedactedEvents = Pref.Bool("show_redacted_events", true, ANY)
    val showMembershipEvents = Pref.Bool("show_membership_events", true, ANY)
    val showProfileChanges = Pref.Bool("show_profile_changes", true, ANY)
    val renderUrlPreviews = Pref.Bool("render_url_previews", true, ANY)
    val smallReplies = Pref.Bool("small_replies", false, ANY)
    val smallThreads = Pref.Bool("small_threads", true, ANY)
    val showDateSeparators = Pref.Bool("show_date_separators", true, ANY)
    val showRoomEmojiPacks = Pref.Bool("show_room_emoji_packs", true, ANY)
    val uploadDialog = Pref.Bool("upload_dialog", true, ANY)
    val hideFingerprint = Pref.Bool("hide_fingerprint", false, ANY)
    val gifProvider = Pref.Choice("gif_provider", "klipy", ANY, listOf("giphy", "tenor", "klipy"))
    val maxImageWidth = Pref.Number("max_image_width", 320, ANY)
    val roomListPreview = Pref.Bool("room_list_preview", true, ANY)
    val roomListStyle = Pref.Choice("room_list_style", "default", GLOBAL, listOf("compact", "default", "spacious"))
    val pinFavorites = Pref.Bool("pin_favorites", false, GLOBAL)
    val pinLowPriority = Pref.Bool("pin_low_priority", false, GLOBAL)
    val muteLowPriority = Pref.Bool("mute_low_priority", false, GLOBAL)
    val alphabeticalOrder = Pref.Bool("alphabetical_order", false, GLOBAL)
    val roomViewType =
        Pref.Choice(
            "room_view_type",
            null,
            ROOM,
            listOf(null, "", "m.space", "org.matrix.msc3417.call", "fi.mau.msc2545.image_pack"),
        )
    val lowBandwidth = Pref.Bool("low_bandwidth", false, DEVICE_GLOBAL)

    /** Every preference, in gomuks' order (its settings screen's). */
    val all: List<Pref<*>> =
        listOf(
            sendReadReceipts,
            sendTypingNotifications,
            sendBundledUrlPreviews,
            displayReadReceipts,
            showMediaPreviews,
            autoplayGifs,
            showInlineImages,
            showInviteAvatars,
            codeBlockLineWrap,
            codeBlockTheme,
            showHiddenEvents,
            showRedactedEvents,
            showMembershipEvents,
            showProfileChanges,
            renderUrlPreviews,
            smallReplies,
            smallThreads,
            showDateSeparators,
            showRoomEmojiPacks,
            uploadDialog,
            hideFingerprint,
            gifProvider,
            maxImageWidth,
            roomListPreview,
            roomListStyle,
            pinFavorites,
            pinLowPriority,
            muteLowPriority,
            alphabeticalOrder,
            roomViewType,
            lowBandwidth,
        )
}

/** Chroma's styles, as gomuks lists them ("auto" follows the theme). */
private val CODE_BLOCK_THEMES =
    listOf(
        "auto",
        "abap",
        "algol_nu",
        "algol",
        "arduino",
        "autumn",
        "average",
        "base16-snazzy",
        "borland",
        "bw",
        "catppuccin-frappe",
        "catppuccin-latte",
        "catppuccin-macchiato",
        "catppuccin-mocha",
        "colorful",
        "doom-one2",
        "doom-one",
        "dracula",
        "emacs",
        "friendly",
        "fruity",
        "github-dark",
        "github",
        "gruvbox-light",
        "gruvbox",
        "hrdark",
        "hr_high_contrast",
        "igor",
        "lovelace",
        "manni",
        "modus-operandi",
        "modus-vivendi",
        "monokailight",
        "monokai",
        "murphy",
        "native",
        "nord",
        "onedark",
        "onesenterprise",
        "paraiso-dark",
        "paraiso-light",
        "pastie",
        "perldoc",
        "pygments",
        "rainbow_dash",
        "rose-pine-dawn",
        "rose-pine-moon",
        "rose-pine",
        "rrt",
        "solarized-dark256",
        "solarized-dark",
        "solarized-light",
        "swapoff",
        "tango",
        "tokyonight-day",
        "tokyonight-moon",
        "tokyonight-night",
        "tokyonight-storm",
        "trac",
        "vim",
        "vs",
        "vulcan",
        "witchhazel",
        "xcode-dark",
        "xcode",
    )
