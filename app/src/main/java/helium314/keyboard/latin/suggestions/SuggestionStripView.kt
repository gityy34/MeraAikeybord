/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.suggestions

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.DragEvent
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.children
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.addPinnedKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getPinnedToolbarKeys
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.removePinnedKey
import helium314.keyboard.latin.utils.saveToolbarSelection
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min
import androidx.core.view.isGone
import helium314.keyboard.latin.utils.onClickToolbarKey
import helium314.keyboard.latin.utils.onLongClickToolbarKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("InflateParams")
class SuggestionStripView(context: Context, attrs: AttributeSet?, defStyle: Int) :
    RelativeLayout(context, attrs, defStyle), View.OnClickListener, OnLongClickListener, OnSharedPreferenceChangeListener {

    /** Construct a [SuggestionStripView] for showing suggestions to be picked by the user. */
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.attr.suggestionStripViewStyle)

    interface Listener {
        fun pickSuggestionManually(word: SuggestedWordInfo?)
        fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean)
        fun removeSuggestion(word: String?)
        fun removeExternalSuggestions()
        fun onSwipeDownOnToolbar()
    }

    private val moreSuggestionsContainer: View
    private val wordViews = ArrayList<TextView>()
    private val debugInfoViews = ArrayList<TextView>()
    private val dividerViews = ArrayList<View>()

    init {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.suggestions_strip, this)
        moreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null)

        val colors = Settings.getValues().mColors
        colors.setBackground(this, ColorType.STRIP_BACKGROUND)
        repeat(SuggestedWords.MAX_SUGGESTIONS) {
            val word = TextView(context, null, R.attr.suggestionWordStyle)
            word.contentDescription = resources.getString(R.string.spoken_empty_suggestion)
            word.setOnClickListener(this)
            word.setOnLongClickListener(this)
            colors.setBackground(word, ColorType.STRIP_BACKGROUND)
            wordViews.add(word)
            val divider = inflater.inflate(R.layout.suggestion_divider, null)
            dividerViews.add(divider)
            val info = TextView(context, null, R.attr.suggestionWordStyle)
            info.setTextColor(colors.get(ColorType.KEY_TEXT))
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP)
            debugInfoViews.add(info)
        }

        DEBUG_SUGGESTIONS = context.prefs().getBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, Defaults.PREF_SHOW_SUGGESTION_INFOS)
    }

    // toolbar views, drawables and setup
    private val toolbar: ViewGroup = findViewById(R.id.toolbar)
    private val toolbarContainer: View = findViewById(R.id.toolbar_container)
    private val pinnedKeys: ViewGroup = findViewById(R.id.pinned_keys)
    private val suggestionsStrip: ViewGroup = findViewById(R.id.suggestions_strip)
    private val toolbarExpandKey = findViewById<ImageButton>(R.id.suggestions_strip_toolbar_key)
    private val toolbarMenuKey = findViewById<ImageButton>(R.id.suggestions_strip_tools_menu)
    private val panelShortcuts = findViewById<LinearLayout>(R.id.toolbar_panel_shortcuts)
    private val panelShortcutScroller = findViewById<View>(R.id.toolbar_panel_shortcuts_scroller)
    private var toolbarPanel: ToolbarToolsPanel? = null
    private var toolbarDraft: ToolbarEditorState? = null
    private var panelTools = emptyList<ToolbarKey>()
    private var obscuredViews = emptyList<Pair<View, Int>>()
    private var revealedWrapper = false
    private var normalToolbarVisible = false
    private var configuredPins = emptyList<ToolbarKey>()
    private val incognitoIcon = KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.INCOGNITO.name, context)
    private val toolbarArrowIcon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context)
    private val defaultToolbarBackground: Drawable = toolbarExpandKey.background
    private val enabledToolKeyBackground = GradientDrawable()
    private var direction = 1 // 1 if LTR, -1 if RTL

    private val toolbarKeyLayoutParams = LinearLayout.LayoutParams(
        resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
        LinearLayout.LayoutParams.MATCH_PARENT
    )

    init {
        val colors = Settings.getValues().mColors

        // expand key
        // weird way of setting size (default is config_suggestions_strip_edge_key_width)
        // but better not change it or people will complain
        val toolbarHeight = min(toolbarExpandKey.layoutParams.height, resources.getDimension(R.dimen.config_suggestions_strip_height).toInt())
        toolbarExpandKey.layoutParams.height = toolbarHeight
        toolbarExpandKey.layoutParams.width = toolbarHeight // we want it square
        colors.setBackground(toolbarExpandKey, ColorType.STRIP_BACKGROUND) // necessary because background is re-used for defaultToolbarBackground
        colors.setColor(toolbarExpandKey, ColorType.TOOL_BAR_EXPAND_KEY)
        colors.setColor(toolbarExpandKey.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)

        // background indicator for pinned keys
        val color = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) or -0x1000000 // ignore alpha (in Java this is more readable 0xFF000000)
        enabledToolKeyBackground.colors = intArrayOf(color, Color.TRANSPARENT)
        enabledToolKeyBackground.gradientType = GradientDrawable.RADIAL_GRADIENT
        enabledToolKeyBackground.gradientRadius = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height) / 2.1f

        val mToolbarMode = if (isGone) ToolbarMode.HIDDEN else Settings.getValues().mToolbarMode
        if (mToolbarMode == ToolbarMode.TOOLBAR_KEYS) {
            setToolbarVisibility(true)
        }

        toolbarMenuKey.setOnClickListener { onToolsButtonPressed() }
        colors.setColor(toolbarMenuKey, ColorType.TOOL_BAR_KEY)
        colors.setBackground(toolbarMenuKey, ColorType.STRIP_BACKGROUND)
        panelShortcuts.setOnDragListener { _, event -> onShortcutDrag(event) }
        refreshToolbarButtons()
        toolbarContainer.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val newWidth = right - left
            if (newWidth > 0 && newWidth != oldRight - oldLeft) {
                toolbar.post { toolbar.minimumWidth = toolbarContainer.width }
            }
        }

        updateKeys()
    }

    private lateinit var listener: Listener
    private var suggestedWords = SuggestedWords.emptyInstance
    private var startIndexOfMoreSuggestions = 0
    private var isExternalSuggestionVisible = false // Required to disable the more suggestions if other suggestions are visible
    private val layoutHelper = SuggestionStripLayoutHelper(context, attrs, defStyle, wordViews, dividerViews, debugInfoViews)
    private val moreSuggestionsView = moreSuggestionsContainer.findViewById<MoreSuggestionsView>(R.id.more_suggestions_view).apply {
        val slidingListener = object : SimpleOnGestureListener() {
            override fun onScroll(down: MotionEvent?, me: MotionEvent, deltaX: Float, deltaY: Float): Boolean {
                if (down == null) return false
                val dy = me.y - down.y
                val dx = me.x - down.x

                if (Settings.getValues().mToolbarSwipeDownToHide && dy > 50.dpToPx(resources) && abs(dy) > abs(dx)) {
                    listener.onSwipeDownOnToolbar()
                    return true
                }

                return if (!isExternalSuggestionVisible && toolbarContainer.visibility != VISIBLE && deltaY > 0 && dy < (-10).dpToPx(resources)) showMoreSuggestions()
                else false
            }
        }
        gestureDetector = GestureDetector(context, slidingListener)
    }

    // public stuff

    val isShowingMoreSuggestionPanel get() = moreSuggestionsView.isShowingInParent
    val isShowingToolbarPanel get() = toolbarPanel?.isVisible == true

    /** A connection back to the input method. */
    fun setListener(newListener: Listener, inputView: View) {
        listener = newListener
        moreSuggestionsView.listener = newListener
        moreSuggestionsView.mainKeyboardView = inputView.findViewById(R.id.keyboard_view)
        toolbarPanel = inputView.findViewById(R.id.toolbar_tools_panel)
        toolbarPanel?.layoutDirection = layoutDirection
        updateToolbarButtons()
        refreshPinnedKeys()
    }

    fun setRtl(isRtlLanguage: Boolean) {
        val newLayoutDirection: Int
        if (!Settings.getValues().mVarToolbarDirection)
            newLayoutDirection = LAYOUT_DIRECTION_LOCALE
        else {
            newLayoutDirection = if (isRtlLanguage) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
            direction = if (isRtlLanguage) -1 else 1
            toolbarExpandKey.scaleX = (if (toolbarContainer.visibility != VISIBLE) 1f else -1f) * direction
        }
        layoutDirection = newLayoutDirection
        suggestionsStrip.layoutDirection = newLayoutDirection
        toolbarPanel?.layoutDirection = newLayoutDirection
    }

    fun setToolbarVisibility(toolbarVisible: Boolean) {
        normalToolbarVisible = toolbarVisible
        applyStripVisibility()
    }

    private fun applyStripVisibility() {
        val panelOpen = isShowingToolbarPanel
        val emptySuggestionRow = canOpenToolbarControls() &&
            Settings.getValues().mToolbarMode == ToolbarMode.EXPANDABLE &&
            !isExternalSuggestionVisible && suggestedWords.isEmpty()
        val showQuickTools = (normalToolbarVisible || emptySuggestionRow) && toolbar.children.any { it.isVisible }
        pinnedKeys.isVisible = !panelOpen && !showQuickTools
        suggestionsStrip.isVisible = !panelOpen && !showQuickTools
        toolbarContainer.isVisible = !panelOpen && showQuickTools
        panelShortcutScroller.isVisible = panelOpen
        if (DEBUG_SUGGESTIONS) {
            for (view in debugInfoViews) {
                view.visibility = suggestionsStrip.visibility
            }
        }

        toolbarExpandKey.scaleX = (if (normalToolbarVisible) -1f else 1f) * direction
    }

    fun setSuggestions(suggestions: SuggestedWords, isRtlLanguage: Boolean) {
        clear()
        setRtl(isRtlLanguage)
        suggestedWords = suggestions
        startIndexOfMoreSuggestions = layoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
            context, suggestedWords, suggestionsStrip, this
        )
        isExternalSuggestionVisible = false
        updateKeys()
    }

    fun setExternalSuggestionView(view: View?, addCloseButton: Boolean) {
        clear()
        isExternalSuggestionVisible = true

        if (addCloseButton) {
            val wrapper = LinearLayout(context)
            suggestionsStrip.doOnNextLayout {
                wrapper.layoutParams = LinearLayout.LayoutParams(suggestionsStrip.width - 30.dpToPx(resources), LayoutParams.MATCH_PARENT)
            }
            wrapper.addView(view)
            suggestionsStrip.addView(wrapper)

            val closeButton = createToolbarKey(context, ToolbarKey.CLOSE_HISTORY)
            closeButton.layoutParams = toolbarKeyLayoutParams
            setupKey(closeButton, Settings.getValues().mColors)
            closeButton.setOnClickListener {
                listener.removeExternalSuggestions()
            }
            suggestionsStrip.addView(closeButton)
        } else {
            suggestionsStrip.addView(view)
        }

        if (Settings.getValues().mAutoHideToolbar) setToolbarVisibility(false)
        else applyStripVisibility()
    }

    fun setMoreSuggestionsHeight(remainingHeight: Int) {
        layoutHelper.setMoreSuggestionsHeight(remainingHeight)
    }

    fun dismissMoreSuggestionsPanel() {
        moreSuggestionsView.dismissPopupKeysPanel()
    }

    fun dismissToolbarControls() {
        val panel = toolbarPanel ?: return
        if (!panel.isVisible) return
        toolbarDraft = null
        panelTools = emptyList()
        panelShortcuts.removeAllViews()
        panel.clearContents()
        panel.isVisible = false
        if (revealedWrapper) (panel.parent as? View)?.isGone = true
        revealedWrapper = false
        for ((view, importance) in obscuredViews) view.importantForAccessibility = importance
        obscuredViews = emptyList()
        updateToolbarButtons()
    }

    fun handleToolbarBack(): Boolean {
        if (!isShowingToolbarPanel) return false
        if (toolbarDraft == null) dismissToolbarControls()
        else {
            toolbarDraft = null
            renderToolPanel()
        }
        return true
    }

    // overrides: necessarily public, but not used from outside

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        when (key) {
            Settings.PREF_TOOLBAR_KEYS -> {
                refreshToolbarButtons()
                return
            }
            Settings.PREF_PINNED_TOOLBAR_KEYS -> {
                refreshToolbarButtons()
                if (isShowingToolbarPanel && toolbarDraft == null) renderToolPanel()
                return
            }
        }
        setToolbarButtonsActivatedStateOnPrefChange(pinnedKeys, key)
        setToolbarButtonsActivatedStateOnPrefChange(toolbar, key)
        if (key == Settings.PREF_ALWAYS_INCOGNITO_MODE)
            GlobalScope.launch { delay(10); withContext(Dispatchers.Main) { updateKeys() } }
    }

    override fun onVisibilityChanged(view: View, visibility: Int) {
        super.onVisibilityChanged(view, visibility)
        // workaround for a bug with inline suggestions views that just keep showing up otherwise, https://github.com/HeliBorg/HeliBoard/pull/386
        if (view === this) {
            if (visibility != VISIBLE) dismissToolbarControls()
            if (visibility == VISIBLE) applyStripVisibility() else suggestionsStrip.visibility = visibility
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dismissToolbarControls()
        dismissMoreSuggestionsPanel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) {
            // Changing the pin count can change the width of the suggestions after this layout.
            post { if (isAttachedToWindow) refreshPinnedKeys() }
        }
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
        // Don't populate accessibility event with suggested words and voice key.
        return true
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        // Detecting sliding up finger to show MoreSuggestionsView.
        return !isShowingToolbarPanel && moreSuggestionsView.shouldInterceptTouchEvent(motionEvent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        moreSuggestionsView.touchEvent(motionEvent)
        return true
    }

    override fun onClick(view: View) {
        val tag = view.tag
        if (tag is ToolbarKey) {
            onClickToolbarKey(view) { listener.onCodeInput(it, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false) }
            return
        }
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
        if (view === toolbarExpandKey) {
            setToolbarVisibility(toolbarContainer.visibility != VISIBLE)
        }

        // tag for word views is set in SuggestionStripLayoutHelper (setupWordViewsTextAndColor, layoutPunctuationSuggestions)
        if (tag is Int) {
            if (tag >= suggestedWords.size) {
                return
            }
            val wordInfo = suggestedWords.getInfo(tag)
            listener.pickSuggestionManually(wordInfo)
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (view.tag is ToolbarKey) {
            onLongClickToolbarKey(view)
            return true
        }
        AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
        return if (view is TextView && wordViews.contains(view)) {
            onLongClickSuggestion(view)
        } else {
            showMoreSuggestions()
        }
    }

    // actually private stuff

    private fun onLongClickToolbarKey(view: View) {
        val tag = view.tag as? ToolbarKey ?: return
        if (!Settings.getValues().mQuickPinToolbarKeys || view.parent === pinnedKeys) {
            onLongClickToolbarKey(view) { code, isRepeat -> listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, isRepeat) }
        } else if (view.parent === toolbar) {
            AudioAndHapticFeedbackManager.getInstance().performHapticFeedback(this, HapticEvent.KEY_LONG_PRESS)
            val prefs = context.prefs()
            if (tag in getPinnedToolbarKeys(prefs)) removePinnedKey(prefs, tag)
            else addPinnedKey(prefs, tag)
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we only return false mostly anyway
    private fun onLongClickSuggestion(wordView: TextView): Boolean {
        var showIcon = true
        if (wordView.tag is Int) {
            val index = wordView.tag as Int
            val type = suggestedWords.getInfo(index).sourceDict
            if (type == Dictionary.DICTIONARY_USER_TYPED || type == Dictionary.DICTIONARY_HARDCODED)
                showIcon = false
        }
        if (showIcon) {
            val icon = KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_BIN, context)!!
            Settings.getValues().mColors.setColor(icon, ColorType.REMOVE_SUGGESTION_ICON)
            val w = icon.intrinsicWidth
            val h = icon.intrinsicHeight
            wordView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
            wordView.ellipsize = TextUtils.TruncateAt.END
            val downOk = AtomicBoolean(false)
            wordView.setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP && downOk.get()) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        removeSuggestion(wordView)
                        wordView.cancelLongPress()
                        wordView.isPressed = false
                        return@setOnTouchListener true
                    }
                } else if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    val x = motionEvent.x
                    val y = motionEvent.y
                    if (0 < x && x < w && 0 < y && y < h) {
                        downOk.set(true)
                    }
                }
                false
            }
        }
        if (DebugFlags.DEBUG_ENABLED && (isShowingMoreSuggestionPanel || !showMoreSuggestions())) {
            showSourceDict(wordView)
            return true
        }
        return showMoreSuggestions()
    }

    private fun showMoreSuggestions(): Boolean {
        if (isShowingToolbarPanel) return false
        if (suggestedWords.size <= startIndexOfMoreSuggestions) {
            return false
        }
        if (!moreSuggestionsView.show(
                suggestedWords, startIndexOfMoreSuggestions, moreSuggestionsContainer, layoutHelper, this
        ))
            return false
        for (i in 0..<startIndexOfMoreSuggestions) {
            wordViews[i].isPressed = false
        }
        return true
    }

    private fun showSourceDict(wordView: TextView) {
        val word = wordView.text.toString()
        val index = wordView.tag as? Int ?: return
        val suggestedWords = suggestedWords
        if (index >= suggestedWords.size) return
        val info = suggestedWords.getInfo(index)
        if (info.word != word) return

        if (isShowingMoreSuggestionPanel) {
            moreSuggestionsView.dismissPopupKeysPanel()
        }
        var text = info.sourceDict.mDictType + ":" + info.sourceDict.mLocale
        if (index == 1 && suggestedWords.typedWordInfo != null && suggestedWords.typedWordInfo.sourceDict.mDictType != Dictionary.TYPE_USER_TYPED) {
            val dict = suggestedWords.typedWordInfo.sourceDict
            text += "\n${suggestedWords.typedWordInfo.word}: ${dict.mDictType}:${dict.mLocale}"
        }
        KeyboardSwitcher.getInstance().showToast(text, true)
    }

    private fun removeSuggestion(wordView: TextView) {
        val word = wordView.text.toString()
        listener.removeSuggestion(word)
        moreSuggestionsView.dismissPopupKeysPanel()
        // show suggestions, but without the removed word
        val newSuggestedWords = SuggestedWords(
            suggestedWords.filter { it.word != word },
            suggestedWords.rawSuggestions?.filter { it.word != word },
            suggestedWords.typedWordInfo, suggestedWords.typedWordValid, suggestedWords.willAutoCorrect,
            suggestedWords.isObsoleteSuggestions, suggestedWords.inputStyle, suggestedWords.sequenceNumber
        )
        setSuggestions(newSuggestedWords, direction != 1)
        suggestionsStrip.isVisible = true

        // Show the toolbar if no suggestions are left and the "Auto show toolbar" setting is enabled
        if (this.suggestedWords.isEmpty() && Settings.getValues().mAutoShowToolbar) {
            setToolbarVisibility(true)
        }
    }

    private fun clear() {
        suggestionsStrip.removeAllViews()
        if (DEBUG_SUGGESTIONS) removeAllDebugInfoViews()
        if (!toolbarContainer.isVisible && !isShowingToolbarPanel)
            suggestionsStrip.isVisible = true
        dismissMoreSuggestionsPanel()
        for (word in wordViews) {
            word.setOnTouchListener(null)
        }
    }

    private fun removeAllDebugInfoViews() {
        for (debugInfoView in debugInfoViews) {
            val parent = debugInfoView.parent
            if (parent is ViewGroup) {
                parent.removeView(debugInfoView)
            }
        }
    }

    fun updateVoiceKey() {
        val show = Settings.getValues().mShowsVoiceInputKey
        toolbar.findViewWithTag<View>(ToolbarKey.VOICE)?.isVisible = show
        if (!canOpenToolbarControls()) dismissToolbarControls()
        updateToolbarButtons()
        refreshPinnedKeys()
    }

    private fun updateKeys() {
        updateVoiceKey()
        isExternalSuggestionVisible = false
    }

    private fun updateToolbarButtons() {
        val settingsValues = Settings.getValues()
        val showTools = canOpenToolbarControls()
        val toolbarIsExpandable = settingsValues.mToolbarMode == ToolbarMode.EXPANDABLE
        val incognito = settingsValues.mIncognitoModeEnabled
        toolbarMenuKey.isVisible = showTools
        val editing = toolbarDraft != null
        val icon = when {
            editing -> R.drawable.sym_keyboard_done_rounded
            isShowingToolbarPanel -> R.drawable.ic_close
            else -> R.drawable.ic_toolbar_grid
        }
        if (toolbarMenuKey.tag != icon) {
            toolbarMenuKey.tag = icon
            toolbarMenuKey.setImageResource(icon)
            toolbarMenuKey.isActivated = true
            settingsValues.mColors.setColor(toolbarMenuKey, ColorType.TOOL_BAR_KEY)
        }
        toolbarMenuKey.contentDescription = resources.getString(when {
            editing -> R.string.toolbar_editor_done
            isShowingToolbarPanel -> R.string.toolbar_close_tools
            else -> R.string.toolbar_more_options
        })
        if (incognito) {
            toolbarExpandKey.setImageDrawable(incognitoIcon)
            toolbarExpandKey.isVisible = true
        } else {
            toolbarExpandKey.setImageDrawable(toolbarArrowIcon)
            toolbarExpandKey.isVisible = !showTools && toolbarIsExpandable
        }

        toolbarExpandKey.setOnClickListener(if (!showTools && toolbarIsExpandable) this else null)
        toolbarExpandKey.contentDescription = resources.getString(
            if (incognito) R.string.incognito else R.string.more_keys_strip_description
        )
        applyStripVisibility()
    }

    private fun canOpenToolbarControls(): Boolean {
        val settings = Settings.getValues()
        return toolbarPanel != null && settings.mToolbarMode != ToolbarMode.HIDDEN && !settings.mIsLocked &&
            !settings.mInputAttributes.mIsPasswordField
    }

    private fun onToolsButtonPressed() {
        val panel = toolbarPanel ?: return
        if (!canOpenToolbarControls() || !::listener.isInitialized || !toolbarMenuKey.isShown) return
        val draft = toolbarDraft
        if (draft != null) {
            toolbarDraft = null
            saveToolbarSelection(context.prefs(), true, panelTools, draft.enabled)
            renderToolPanel()
        } else if (panel.isVisible) {
            dismissToolbarControls()
        } else {
            val wrapper = panel.parent as? ViewGroup ?: return
            val keyboardView = moreSuggestionsView.mainKeyboardView
            val keyboard = keyboardView.keyboard ?: return
            panel.layoutParams = panel.layoutParams.apply {
                height = keyboard.mOccupiedHeight + keyboardView.paddingTop + keyboardView.paddingBottom
            }
            dismissMoreSuggestionsPanel()
            keyboardView.cancelAllOngoingEvents()
            val regularTools = getEnabledToolbarKeys(context.prefs())
            panelTools = (featuredPanelTools + regularTools + ToolbarKey.entries)
                .distinct().filter { key ->
                    isToolbarToolAvailable(key) && key != ToolbarKey.BACKGROUND_GATHERING &&
                        (key != ToolbarKey.CLEAR_CLIPBOARD || key in regularTools || key in configuredPins)
                }
            revealedWrapper = wrapper.isGone
            if (revealedWrapper) wrapper.isVisible = true
            obscuredViews = wrapper.children.filter { it !== panel }.map { it to it.importantForAccessibility }.toList()
            for ((view, _) in obscuredViews)
                view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            panel.isVisible = true
            renderToolPanel()
        }
    }

    private fun panelPins(): List<ToolbarKey> {
        val prefs = context.prefs()
        val pins = if (prefs.contains(Settings.PREF_PINNED_TOOLBAR_KEYS)) configuredPins
            else getEnabledToolbarKeys(prefs).filter { it in panelTools }.distinct().take(4)
        return pins.filter { it in panelTools }
    }

    private fun renderToolPanel() {
        val panel = toolbarPanel ?: return
        val draft = toolbarDraft
        val pins = draft?.enabled ?: panelPins()
        renderPanelShortcuts(pins, draft)
        updateToolbarButtons()
        val unpinned = (draft?.items ?: panelTools).filterNot { it in pins }
        if (draft == null) {
            panel.showTools(unpinned, ::activatePanelTool) {
                toolbarDraft = ToolbarEditorState(panelTools, pins)
                renderToolPanel()
            }
        } else {
            panel.showEditor(unpinned, { key ->
                if (draft.dropOnPins(key, null)) renderToolPanel()
            }, { key ->
                val removed = draft.dropOnTools(key)
                if (removed) post { if (toolbarDraft === draft) renderToolPanel() }
                removed
            })
        }
    }

    private fun renderPanelShortcuts(keys: List<ToolbarKey>, draft: ToolbarEditorState?) {
        panelShortcuts.removeAllViews()
        val colors = Settings.getValues().mColors
        panelShortcuts.background = if (draft == null) null else GradientDrawable().apply {
            cornerRadius = 8.dpToPx(resources).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(1.dpToPx(resources), colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND))
        }
        if (keys.isEmpty()) {
            val hint = TextView(context).apply {
                setText(if (draft == null) R.string.toolbar_panel_title else R.string.toolbar_pin_drop_zone)
                setTextColor(colors.get(ColorType.KEY_TEXT))
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            panelShortcuts.addView(hint, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        for (key in keys) {
            val button = createToolbarKey(context, key)
            button.layoutParams = LinearLayout.LayoutParams(toolbarKeyLayoutParams)
            if (draft != null) button.isActivated = true
            button.contentDescription = key.toolbarPanelLabel(context)
            colors.setColor(button, ColorType.TOOL_BAR_KEY)
            colors.setBackground(button, ColorType.STRIP_BACKGROUND)
            button.setOnClickListener {
                if (draft == null) activatePanelTool(key)
                else if (draft.dropOnTools(key)) renderToolPanel()
            }
            if (draft != null) {
                val name = key.toolbarPanelLabel(context)
                button.contentDescription = context.getString(R.string.toolbar_unpin_tool, name)
                button.setOnLongClickListener { startToolbarToolDrag(button, key) }
                for ((offset, label) in listOf(-1 to R.string.toolbar_move_earlier, 1 to R.string.toolbar_move_later)) {
                    val target = draft.neighbor(key, offset) ?: continue
                    ViewCompat.addAccessibilityAction(button, context.getString(label, name)) { _, _ ->
                        draft.move(key, target).also { if (it) renderToolPanel() }
                    }
                }
            }
            panelShortcuts.addView(button)
        }
    }

    private fun onShortcutDrag(event: DragEvent): Boolean {
        val draft = toolbarDraft ?: return false
        val key = (event.localState as? ToolbarToolDrag)?.key ?: return false
        if (event.action != DragEvent.ACTION_DROP) return true
        val target = panelShortcuts.children.firstOrNull { event.x >= it.left && event.x < it.right }?.tag as? ToolbarKey
        val changed = draft.dropOnPins(key, target)
        if (changed) post { if (toolbarDraft === draft) renderToolPanel() }
        return changed
    }

    private fun activatePanelTool(key: ToolbarKey) {
        if (!isShowingToolbarPanel || !canOpenToolbarControls() || toolbarDraft != null ||
            !isToolbarToolAvailable(key)
        ) return
        val code = getCodeForToolbarKey(key)
        dismissToolbarControls()
        if (code != KeyCode.UNSPECIFIED)
            listener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)
    }

    private fun isToolbarToolAvailable(key: ToolbarKey): Boolean =
        when (key) {
            ToolbarKey.CLOSE_HISTORY -> false
            ToolbarKey.VOICE -> Settings.getValues().mShowsVoiceInputKey
            else -> true
        }

    private fun visiblePinnedKeys(): List<ToolbarKey> {
        val keyWidth = toolbarKeyLayoutParams.width
        val reservedWidth = (if (toolbarMenuKey.isVisible) toolbarMenuKey.layoutParams.width else 0) +
            (if (toolbarExpandKey.isVisible) toolbarExpandKey.layoutParams.width else 0)
        val capacity = pinnedToolbarCapacity(width, reservedWidth, 132.dpToPx(resources), keyWidth)
        return configuredPins.asSequence().filter(::isToolbarToolAvailable).take(capacity).toList()
    }

    private fun refreshToolbarButtons() {
        val prefs = context.prefs()
        val keys = if (Settings.getValues().mToolbarMode == ToolbarMode.EXPANDABLE &&
            prefs.contains(Settings.PREF_PINNED_TOOLBAR_KEYS))
            getPinnedToolbarKeys(prefs)
        else getEnabledToolbarKeys(prefs)
        toolbar.removeAllViews()
        for (key in keys.distinct()) addToolbarButton(toolbar, key)
        toolbar.findViewWithTag<View>(ToolbarKey.VOICE)?.isVisible = Settings.getValues().mShowsVoiceInputKey
        refreshPinConfiguration()
        applyStripVisibility()
    }

    private fun refreshPinConfiguration() {
        configuredPins = getPinnedToolbarKeys(context.prefs()).distinct()
        if (Settings.getValues().mQuickPinToolbarKeys) {
            for (index in 0 until toolbar.childCount) {
                val button = toolbar.getChildAt(index)
                val key = button.tag as? ToolbarKey ?: continue
                button.background = if (key in configuredPins)
                    enabledToolKeyBackground.constantState?.newDrawable(resources)
                else defaultToolbarBackground.constantState?.newDrawable(resources)
            }
        }
        refreshPinnedKeys()
    }

    private fun refreshPinnedKeys() {
        val visible = visiblePinnedKeys()
        if (visible.size == pinnedKeys.childCount && visible.indices.all { pinnedKeys.getChildAt(it).tag == visible[it] })
            return
        pinnedKeys.removeAllViews()
        for (key in visible) addToolbarButton(pinnedKeys, key)
    }

    private fun addToolbarButton(parent: ViewGroup, key: ToolbarKey) {
        val button = createToolbarKey(context, key)
        button.layoutParams = LinearLayout.LayoutParams(toolbarKeyLayoutParams)
        setupKey(button, Settings.getValues().mColors)
        parent.addView(button)
    }

    private fun setupKey(view: ImageButton, colors: Colors) {
        view.setOnClickListener(this)
        view.setOnLongClickListener(this)
        (view.layoutParams as LinearLayout.LayoutParams).weight = 1f
        colors.setColor(view, ColorType.TOOL_BAR_KEY)
        colors.setBackground(view, ColorType.STRIP_BACKGROUND)
    }

    companion object {
        private val featuredPanelTools = listOf(
            ToolbarKey.TOOLBAR_APPEARANCE, ToolbarKey.CLIPBOARD, ToolbarKey.EMOJI, ToolbarKey.DPAD,
            ToolbarKey.FLOATING, ToolbarKey.ONE_HANDED, ToolbarKey.UNDO, ToolbarKey.SETTINGS,
            ToolbarKey.NEXT_LANGUAGE, ToolbarKey.NUMPAD, ToolbarKey.VOICE, ToolbarKey.REDO,
        )
        @JvmField
        var DEBUG_SUGGESTIONS = false
        private const val DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.5f
        private val TAG = SuggestionStripView::class.java.simpleName
    }
}
