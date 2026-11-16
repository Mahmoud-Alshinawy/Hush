package app.hushfocus.hush.rules

/** All Instagram-specific matching facts; fixture names refer to screens/instagram/. */
object InstagramRules {
    // home_with_hidden_profile_nodes.json: package of the captured application.
    const val PACKAGE_NAME = "com.instagram.android"
    // home_with_hidden_profile_nodes.json: focused application window, distinct from system bars.
    const val APPLICATION_WINDOW = "application"

    // home_with_hidden_profile_nodes.json: the five bottom-tab buttons and their parent.
    const val TAB_BAR = "com.instagram.android:id/tab_bar"
    // home_with_hidden_profile_nodes.json: selected Home tab.
    const val HOME_TAB = "com.instagram.android:id/feed_tab"
    // reels_tab_selected.json: selected Reels tab.
    const val REELS_TAB = "com.instagram.android:id/clips_tab"
    // home_with_hidden_profile_nodes.json: DMs tab, not the hidden inbox subtree.
    const val DMS_TAB = "com.instagram.android:id/direct_tab"
    // explore_grid.json: selected Search/Explore tab.
    const val SEARCH_TAB = "com.instagram.android:id/search_tab"
    // home_with_hidden_profile_nodes.json: tab button only; not proof of a Profile screen.
    const val PROFILE_TAB = "com.instagram.android:id/profile_tab"
    val BOTTOM_TABS = setOf(HOME_TAB, REELS_TAB, DMS_TAB, SEARCH_TAB, PROFILE_TAB)

    // home_with_hidden_profile_nodes.json: Home toolbar above the posts and stories.
    const val HOME_BAR = "com.instagram.android:id/main_feed_action_bar"
    // home_with_hidden_profile_nodes.json: clickable title control containing the logo.
    const val HOME_TITLE_CONTROL = "com.instagram.android:id/action_bar_title_view"
    // home_with_hidden_profile_nodes.json: logo descendant of that title control.
    const val HOME_LOGO = "com.instagram.android:id/title_logo"
    // home_with_hidden_profile_nodes.json: Home scrolling viewport.
    const val HOME_VIEWPORT = "com.instagram.android:id/sticky_header_list"
    // home_with_hidden_profile_nodes.json: content area including Instagram's bars.
    const val HOME_CONTENT = "com.instagram.android:id/layout_container_main_panel"
    // home_with_hidden_profile_nodes.json: story tray within the list.
    const val STORIES_TRAY = "com.instagram.android:id/reels_tray_container"
    // home_overlay_stories_row.json: alternate story tray outside the list.
    const val OVERLAY_STORIES_TRAY = "com.instagram.android:id/overlay_stories_tray_container"
    val STORIES_TRAYS = setOf(STORIES_TRAY, OVERLAY_STORIES_TRAY)

    // story_viewer.json: story player, distinct from the clips player.
    const val STORY_ROOT = "com.instagram.android:id/reel_viewer_root"
    // reel_from_dm_reply_box.json: full-screen clips root.
    const val CLIPS_ROOT = "com.instagram.android:id/root_clips_layout"
    // reel_from_dm_reply_box.json: container enclosing the clips pager.
    const val CLIPS_CONTAINER = "com.instagram.android:id/clips_viewer_container"
    // reel_from_dm_reply_box.json: only this pager can supply reel swipe evidence.
    const val REEL_PAGER = "com.instagram.android:id/clips_viewer_view_pager"
    // reel_from_dm_reply_box.json: class also recorded by the pager's scroll events.
    const val REEL_PAGER_CLASS = "androidx.viewpager.widget.ViewPager"
    // reel_from_dm_reply_box.json: author tokens are read only from this label within the pager.
    const val REEL_AUTHOR = "com.instagram.android:id/clips_author_username"
    // reel_from_dm_reply_box.json: direct evidence of the DM reply box.
    const val REPLY_BOX = "com.instagram.android:id/reply_bar_edittext"

    // dm_chat.json: thread root containing the message list.
    const val DM_THREAD = "com.instagram.android:id/thread_view_root"
    // dm_chat.json: message list within the thread, not a generic recycler view.
    const val DM_MESSAGE_LIST = "com.instagram.android:id/message_list"
    // dm_chat.json: DM composer, distinct from the comments composer.
    const val DM_COMPOSER = "com.instagram.android:id/row_thread_composer_edittext"
    // dm_chat.json: verified thread back control; Home's left toolbar icon is not used.
    const val DM_BACK = "com.instagram.android:id/header_left_button"

    // profile_own.json and profile_other.json: active profile pager, not the cached header.
    const val PROFILE_ROOT = "com.instagram.android:id/profile_viewpager"
    // profile_other.json: toolbar containing the anonymous back icon.
    const val PROFILE_BAR = "com.instagram.android:id/profile_action_bar"
    // profile_other.json: single back icon in the toolbar's left group.
    const val PROFILE_LEFT_BUTTONS = "com.instagram.android:id/left_action_bar_buttons"
    // profile_other.json: anonymous ImageView; profile_own.json has a named create button instead.
    const val PROFILE_BACK_CLASS = "android.widget.ImageView"
    // dm_inbox.json: visible inbox toolbar; cached_inbox_during_reel.json proves the visibility gate.
    const val INBOX_ROOT = "com.instagram.android:id/direct_inbox_action_bar"
    // search_focused_recent_results.json: native search section, independent of query text.
    const val SEARCH_RESULTS_ROOT = "com.instagram.android:id/search_section_container"

    // explore_grid.json: Explore's toolbar.
    const val EXPLORE_BAR = "com.instagram.android:id/explore_action_bar"
    // explore_grid.json: actionable search field.
    const val SEARCH_BOX = "com.instagram.android:id/action_bar_search_edit_text"
    // explore_grid.json: grid viewport; this ID alone also occurs in other sheets.
    const val EXPLORE_GRID = "com.instagram.android:id/recycler_view"
    // explore_grid.json: grid-card descendants disambiguate the viewport.
    const val EXPLORE_CARD = "com.instagram.android:id/grid_card_layout_container"

    // following_feed.json and favorites_feed.json: list-screen family.
    const val VARIANT_LIST = "com.instagram.android:id/layout_listview_parent_container"
    // following_feed.json and favorites_feed.json: toolbar whose direct-child structure differs.
    const val VARIANT_BAR = "com.instagram.android:id/action_bar"
    // following_feed.json and favorites_feed.json: shared title/back group.
    const val VARIANT_TITLE_GROUP = "com.instagram.android:id/action_bar_new_title_container"
    // following_feed.json and favorites_feed.json: opaque title-confirmation token.
    const val VARIANT_TITLE = "com.instagram.android:id/action_bar_title"
    // following_feed.json and favorites_feed.json: verified back control.
    const val VARIANT_BACK = "com.instagram.android:id/action_bar_button_back"
    // favorites_feed.json: additional toolbar sibling; no wording or title width is inspected.
    const val VARIANT_BUTTON_CLASS = "android.widget.Button"
    // following_feed.json: the toolbar has only the title/back group.
    const val FOLLOWING_BAR_CHILDREN = 1
    // favorites_feed.json: the toolbar additionally has a separate Button.
    const val FAVORITES_BAR_CHILDREN = 2

    // logo_menu_two_entries.json: popup IDs; identity also requires logoMenuExpected.
    const val CONTEXT_MENU = "com.instagram.android:id/context_menu"
    // logo_menu_two_entries.json: immediate list of menu rows.
    const val CONTEXT_MENU_LIST = "com.instagram.android:id/context_menu_options_list"
    // logo_menu_two_entries.json: clickable rows selected by position.
    const val CONTEXT_MENU_ROW = "com.instagram.android:id/context_menu_item"
    // logo_menu_two_entries.json: tokens only; never interpreted as translated words.
    const val CONTEXT_MENU_LABEL = "com.instagram.android:id/context_menu_item_label"
    // logo_menu_two_entries.json and spec §2.1.2: exactly two entries are actionable.
    const val LOGO_MENU_ENTRIES = 2
    // comments_over_reel.json: visible bottom-sheet container.
    const val BOTTOM_SHEET = "com.instagram.android:id/bottom_sheet_container"
    // share_over_reel.json: share sheet over a still-visible pager.
    const val SHARE_SHEET = "com.instagram.android:id/direct_private_share_container_view"
    // more_over_reel.json: action sheet, not the Home logo menu.
    const val ACTION_SHEET = "com.instagram.android:id/action_sheet_container"
    val SHEET_ROOTS = setOf(CONTEXT_MENU, BOTTOM_SHEET, SHARE_SHEET, ACTION_SHEET)

    // home_with_hidden_profile_nodes.json + spec §2.1.3: tab width <= one third of its window.
    const val MAX_REELS_TAB_WIDTH_FRACTION = 1.0 / 3.0
    // home_with_hidden_profile_nodes.json + spec §2.1.3: tab height <= one quarter of its window.
    const val MAX_REELS_TAB_HEIGHT_FRACTION = 0.25
    // home_with_hidden_profile_nodes.json + spec §2.1.3: centre in the bottom 30%.
    const val MIN_REELS_TAB_CENTRE_FRACTION = 0.70
    // home_with_hidden_profile_nodes.json + spec §2.1.3: at least two non-overlapping peer tabs.
    const val MIN_OTHER_BOTTOM_TABS = 2
    // reel_from_dm_reply_box.json supplies pager bounds; spec §2.1.1 supplies the swipe threshold.
    const val REEL_SWIPE_FRACTION = 0.12
    // home_with_hidden_profile_nodes.json supplies the viewport; §2.1.2 supplies 30%, pending row capture.
    const val CAUGHT_UP_VISIBLE_FRACTION = 0.30
    // feed_variant_without_title.json + spec §4: brief UNKNOWN classification hold.
    const val UNKNOWN_HOLD_MS = 350L

    // Home/Reels/logo-menu fixtures: existing Inspector privacy allow-list, never classification input.
    val inspectorLabels = setOf("Home", "Reels", "Search and explore", "Explore", "Search",
        "Messages", "Favorites", "Following", "Profile")

    /** Unverified signals stay null; tests may inject explicitly synthetic IDs. */
    data class SupplementalSignals(
        val profileRoot: String? = null,
        val inboxRoot: String? = null,
        val searchResultsRoot: String? = null,
        val caughtUpRow: String? = null,
        val olderPostsLink: String? = null,
    )

    val supplementalSignals = SupplementalSignals(
        profileRoot = PROFILE_ROOT, inboxRoot = INBOX_ROOT, searchResultsRoot = SEARCH_RESULTS_ROOT,
    )
}
