package com.buildit.app.ui.designer

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.DragEvent
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.buildit.core.model.ComponentId
import com.buildit.core.model.ComponentSchema
import com.buildit.core.model.ComponentType
import com.buildit.core.model.PaletteItem
import com.buildit.core.model.PropertyValues
import android.widget.Switch as AndroidSwitch

/** What is being dragged onto the canvas. Passed as the drag's local state. */
sealed interface DragPayload {
    data class NewItem(val item: PaletteItem) : DragPayload
    data class Existing(val id: ComponentId) : DragPayload
}

const val DRAG_LABEL = "buildit/component"

fun dragClipData(text: String): ClipData =
    ClipData(DRAG_LABEL, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(text))

/** Drag shadow showing a small labelled pill (used for palette items). */
class LabelShadowBuilder(context: Context, private val label: String) : View.DragShadowBuilder() {
    private val density = context.resources.displayMetrics.density
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 14 * density; typeface = Typeface.DEFAULT_BOLD }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE01A5CC4.toInt() }
    private val pad = 12 * density
    private val w = (text.measureText(label) + pad * 2).toInt()
    private val h = (text.textSize + pad * 1.5f).toInt()

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(w, h)
        outShadowTouchPoint.set(w / 2, h + (8 * density).toInt())
    }

    override fun onDrawShadow(canvas: Canvas) {
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, bg)
        canvas.drawText(label, pad, h / 2f - (text.descent() + text.ascent()) / 2, text)
    }
}

/**
 * The designer's device preview. Renders the screen's component tree as *real* Android
 * views (so the preview matches the generated app), intercepts all touches for selection,
 * and accepts platform drag-and-drop on every container.
 */
@SuppressLint("ViewConstructor")
class DesignCanvas(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onSelect(id: ComponentId?)
        fun onDrop(payload: DragPayload, parentId: ComponentId, index: Int)
    }

    var listener: Listener? = null
    var accentColor: Int = 0xFF1A5CC4.toInt()

    private val density = resources.displayMetrics.density
    private var root: ComponentSchema? = null
    private var selected: ComponentId? = null
    private var hover: ComponentId? = null
    private val views = LinkedHashMap<ComponentId, View>()
    private val schemas = HashMap<ComponentId, ComponentSchema>()

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            listener?.onSelect(hitTest(e.x, e.y))
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            val id = hitTest(e.x, e.y) ?: return
            if (id == root?.id) return
            val view = views[id] ?: return
            listener?.onSelect(id)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.startDragAndDrop(dragClipData(id.value), DragShadowBuilder(view), DragPayload.Existing(id), 0)
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent) = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouchEvent(event)

    /**
     * Shows [newRoot]. Unchanged components keep their existing View instances (only the
     * edited parts are rebuilt), so edits don't cause flicker or lose scroll position.
     */
    fun render(newRoot: ComponentSchema, newSelected: ComponentId?) {
        if (newRoot !== root) {
            val oldViews = HashMap(views)
            val oldSchemas = HashMap(schemas)
            views.clear()
            schemas.clear()
            val rootView = reconcile(newRoot, null, oldViews, oldSchemas)
            if (childCount != 1 || getChildAt(0) !== rootView) {
                removeAllViews()
                (rootView.parent as? ViewGroup)?.removeView(rootView)
                addView(rootView)
            }
            root = newRoot
        }
        selected = newSelected
        refreshHighlights()
    }

    // ------------------------------------------------------------------ building

    private fun reconcile(
        c: ComponentSchema,
        parentType: ComponentType?,
        oldViews: Map<ComponentId, View>,
        oldSchemas: Map<ComponentId, ComponentSchema>,
    ): View {
        val previous = oldSchemas[c.id]
        val existing = oldViews[c.id]
        val view = if (existing != null && previous != null && previous.type == c.type && previous.properties == c.properties) {
            existing
        } else {
            PreviewViews.create(context, c)
        }
        view.layoutParams = PreviewViews.layoutParams(c, parentType, density)
        views[c.id] = view
        schemas[c.id] = c
        if (view is ViewGroup && c.type.isContainer) {
            val children = c.children.map { reconcile(it, c.type, oldViews, oldSchemas) }
            val current = (0 until view.childCount).map(view::getChildAt)
            if (current != children) {
                view.removeAllViews()
                children.forEach { child ->
                    (child.parent as? ViewGroup)?.removeView(child)
                    view.addView(child)
                }
            }
            val min = if (c.children.isEmpty()) (56 * density).toInt() else 0
            view.minimumHeight = min
            view.minimumWidth = min
            view.setOnDragListener(containerDragListener(c.id))
        }
        return view
    }

    private fun containerDragListener(id: ComponentId) = OnDragListener { v, e ->
        when (e.action) {
            DragEvent.ACTION_DRAG_STARTED -> e.clipDescription?.label == DRAG_LABEL
            DragEvent.ACTION_DRAG_ENTERED -> { hover = id; refreshHighlights(); true }
            DragEvent.ACTION_DRAG_LOCATION -> true
            DragEvent.ACTION_DRAG_EXITED -> { if (hover == id) hover = null; refreshHighlights(); true }
            DragEvent.ACTION_DROP -> {
                hover = null
                refreshHighlights()
                val payload = e.localState as? DragPayload ?: return@OnDragListener false
                listener?.onDrop(payload, id, dropIndex(v as ViewGroup, schemas[id]!!, e.x, e.y))
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> { if (hover != null) { hover = null; refreshHighlights() }; true }
            else -> false
        }
    }

    /** Insertion index from the drop point: by midpoint for LinearLayouts, append otherwise. */
    private fun dropIndex(group: ViewGroup, c: ComponentSchema, x: Float, y: Float): Int {
        if (c.type != ComponentType.LINEAR_LAYOUT) return group.childCount
        val horizontal = (group as LinearLayout).orientation == LinearLayout.HORIZONTAL
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            val mid = if (horizontal) (child.left + child.right) / 2f else (child.top + child.bottom) / 2f
            if ((if (horizontal) x else y) < mid) return i
        }
        return group.childCount
    }

    // ------------------------------------------------------------------ selection

    private fun refreshHighlights() {
        for ((id, view) in views) {
            val c = schemas[id] ?: continue
            view.foreground = when {
                id == hover -> outline(accentColor, 2f, fillAlpha = 0x33)
                id == selected -> outline(accentColor, 2f)
                c.type.isContainer -> outline(0x55888888, 1f, dashed = true)
                else -> null
            }
        }
    }

    private fun outline(color: Int, widthDp: Float, dashed: Boolean = false, fillAlpha: Int = 0) = GradientDrawable().apply {
        if (dashed) setStroke((widthDp * density).toInt().coerceAtLeast(1), color, 4 * density, 3 * density)
        else setStroke((widthDp * density).toInt(), color)
        setColor((fillAlpha shl 24) or (accentColor and 0x00FFFFFF))
    }

    /** Deepest component whose bounds contain (x, y) in canvas coordinates. */
    private fun hitTest(x: Float, y: Float): ComponentId? {
        val origin = IntArray(2).also { getLocationInWindow(it) }
        val loc = IntArray(2)
        val rect = Rect()
        fun visit(c: ComponentSchema): ComponentId? {
            val v = views[c.id] ?: return null
            if (!v.isShown) return null
            v.getLocationInWindow(loc)
            rect.set(loc[0] - origin[0], loc[1] - origin[1], loc[0] - origin[0] + v.width, loc[1] - origin[1] + v.height)
            if (!rect.contains(x.toInt(), y.toInt())) return null
            for (child in c.children.asReversed()) visit(child)?.let { return it }
            return c.id
        }
        return root?.let(::visit)
    }
}

/** Builds preview views and layout params from component properties. */
object PreviewViews {

    fun create(ctx: Context, c: ComponentSchema): View {
        val p = c.properties
        val view: View = when (c.type) {
            ComponentType.LINEAR_LAYOUT -> LinearLayout(ctx).apply {
                orientation = if (p["orientation"] == "horizontal") LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                gravity(p["gravity"])?.let { gravity = it }
            }
            ComponentType.RELATIVE_LAYOUT -> RelativeLayout(ctx)
            ComponentType.FRAME_LAYOUT -> FrameLayout(ctx)
            ComponentType.CONSTRAINT_LAYOUT -> ConstraintLayout(ctx)
            ComponentType.SCROLL_VIEW -> ScrollView(ctx)
            ComponentType.HORIZONTAL_SCROLL_VIEW -> HorizontalScrollView(ctx)
            ComponentType.CARD_VIEW -> CardView(ctx).apply {
                radius = dp(ctx, PropertyValues.dp(p["cardCornerRadius"]) ?: 0).toFloat()
                cardElevation = dp(ctx, PropertyValues.dp(p["cardElevation"]) ?: 0).toFloat()
                color(p["cardBackgroundColor"])?.let { setCardBackgroundColor(it) }
            }
            ComponentType.TEXT_VIEW -> TextView(ctx)
            ComponentType.BUTTON -> Button(ctx)
            ComponentType.EDIT_TEXT -> EditText(ctx).apply {
                isFocusable = false
                isCursorVisible = false
                hint = p["hint"]
                color(p["textColorHint"])?.let { setHintTextColor(it) }
                inputType = when (p["inputType"]) {
                    "textPassword" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    "number" -> InputType.TYPE_CLASS_NUMBER
                    "textMultiLine" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> InputType.TYPE_CLASS_TEXT
                }
            }
            ComponentType.IMAGE_VIEW -> ImageView(ctx).apply {
                setImageResource(android.R.drawable.ic_menu_gallery)
                setBackgroundColor(0x22888888)
                scaleType = scale(p["scaleType"])
            }
            ComponentType.IMAGE_BUTTON -> ImageButton(ctx).apply { setImageResource(android.R.drawable.ic_menu_gallery) }
            ComponentType.CHECK_BOX -> CheckBox(ctx).apply { isChecked = p["checked"] == "true" }
            ComponentType.RADIO_BUTTON -> RadioButton(ctx).apply { isChecked = p["checked"] == "true" }
            ComponentType.SWITCH -> AndroidSwitch(ctx).apply { isChecked = p["checked"] == "true" }
            ComponentType.PROGRESS_BAR ->
                (if (p["progressStyle"] == "horizontal") ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal) else ProgressBar(ctx)).apply {
                    p["max"]?.toIntOrNull()?.let { max = it }
                    p["progress"]?.toIntOrNull()?.let { progress = it }
                    if (p["progressStyle"] == "horizontal") isIndeterminate = p["indeterminate"] == "true"
                }
            ComponentType.SEEK_BAR -> SeekBar(ctx).apply {
                p["max"]?.toIntOrNull()?.let { max = it }
                p["progress"]?.toIntOrNull()?.let { progress = it }
            }
            ComponentType.SPINNER -> placeholder(ctx, "Spinner  ▾", Gravity.CENTER_VERTICAL or Gravity.START)
            ComponentType.LIST_VIEW -> placeholder(ctx, "ListView\nItem 1\nItem 2\nItem 3", Gravity.START)
            ComponentType.RECYCLER_VIEW -> placeholder(ctx, "RecyclerView\nItem 1\nItem 2\nItem 3", Gravity.START)
            ComponentType.WEB_VIEW -> placeholder(ctx, "WebView", Gravity.CENTER)
            ComponentType.VIDEO_VIEW -> placeholder(ctx, "▶  VideoView", Gravity.CENTER).apply { setBackgroundColor(0xFF222222.toInt()); setTextColor(Color.WHITE) }
            ComponentType.CUSTOM_VIEW -> placeholder(ctx, p["customClass"]?.substringAfterLast('.') ?: "View", Gravity.CENTER)
        }

        if (view is TextView && c.type in TEXT_TYPES) {
            p["text"]?.let { view.text = it }
            PropertyValues.sp(p["textSize"])?.let { view.setTextSize(TypedValue.COMPLEX_UNIT_SP, it) }
            color(p["textColor"])?.let { view.setTextColor(it) }
            when (p["textStyle"]) {
                "bold" -> view.setTypeface(view.typeface, Typeface.BOLD)
                "italic" -> view.setTypeface(view.typeface, Typeface.ITALIC)
                "bold|italic" -> view.setTypeface(view.typeface, Typeface.BOLD_ITALIC)
            }
            gravity(p["gravity"])?.let { view.gravity = it }
        }
        PropertyValues.dp(p["padding"])?.let { val px = dp(ctx, it); view.setPadding(px, px, px, px) }
        if (c.type != ComponentType.CARD_VIEW) color(p["background"])?.let { view.setBackgroundColor(it) }
        view.isEnabled = p["enabled"] != "false"
        // Hidden views stay editable in the designer, just faded.
        if (p["visibility"] == "gone" || p["visibility"] == "invisible") view.alpha = 0.35f
        view.isFocusable = false
        return view
    }

    fun layoutParams(c: ComponentSchema, parentType: ComponentType?, density: Float): ViewGroup.LayoutParams {
        val p = c.properties
        fun size(v: String?) = when (v) {
            "match_parent" -> ViewGroup.LayoutParams.MATCH_PARENT
            null, "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
            else -> PropertyValues.dp(v)?.let { (it * density).toInt() } ?: ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val w = size(p["layout_width"])
        val h = size(p["layout_height"])
        val layoutGravity = gravity(p["layout_gravity"])
        val lp: ViewGroup.MarginLayoutParams = when (parentType) {
            ComponentType.LINEAR_LAYOUT -> LinearLayout.LayoutParams(w, h).apply {
                weight = p["layout_weight"]?.toFloatOrNull() ?: 0f
                layoutGravity?.let { gravity = it }
            }
            ComponentType.RELATIVE_LAYOUT -> RelativeLayout.LayoutParams(w, h).apply {
                if (p["layout_centerInParent"] == "true") addRule(RelativeLayout.CENTER_IN_PARENT)
                if (p["layout_centerHorizontal"] == "true") addRule(RelativeLayout.CENTER_HORIZONTAL)
                if (p["layout_centerVertical"] == "true") addRule(RelativeLayout.CENTER_VERTICAL)
                if (p["layout_alignParentTop"] == "true") addRule(RelativeLayout.ALIGN_PARENT_TOP)
                if (p["layout_alignParentBottom"] == "true") addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                if (p["layout_alignParentStart"] == "true") addRule(RelativeLayout.ALIGN_PARENT_START)
                if (p["layout_alignParentEnd"] == "true") addRule(RelativeLayout.ALIGN_PARENT_END)
            }
            ComponentType.CONSTRAINT_LAYOUT -> ConstraintLayout.LayoutParams(w, h).apply {
                if (p["constrainTop"] == "true") topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                if (p["constrainBottom"] == "true") bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                if (p["constrainStart"] == "true") startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                if (p["constrainEnd"] == "true") endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            }
            else -> FrameLayout.LayoutParams(w, h).apply { layoutGravity?.let { gravity = it } }
        }
        PropertyValues.dp(p["layout_margin"])?.let { val px = (it * density).toInt(); lp.setMargins(px, px, px, px) }
        return lp
    }

    private val TEXT_TYPES = setOf(
        ComponentType.TEXT_VIEW, ComponentType.BUTTON, ComponentType.EDIT_TEXT,
        ComponentType.CHECK_BOX, ComponentType.RADIO_BUTTON, ComponentType.SWITCH,
    )

    private fun placeholder(ctx: Context, label: String, g: Int) = TextView(ctx).apply {
        text = label
        gravity = g
        setTextColor(0xFF555555.toInt())
        setBackgroundColor(0x22888888)
        val pad = dp(ctx, 8)
        setPadding(pad, pad, pad, pad)
        minHeight = dp(ctx, 48)
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun color(v: String?): Int? = v?.let { runCatching { Color.parseColor(it) }.getOrNull() }

    private fun scale(v: String?) = when (v) {
        "centerCrop" -> ImageView.ScaleType.CENTER_CROP
        "center" -> ImageView.ScaleType.CENTER
        "centerInside" -> ImageView.ScaleType.CENTER_INSIDE
        "fitXY" -> ImageView.ScaleType.FIT_XY
        else -> ImageView.ScaleType.FIT_CENTER
    }

    fun gravity(v: String?): Int? {
        if (v.isNullOrEmpty()) return null
        var g = 0
        for (part in v.split('|')) g = g or when (part) {
            "left" -> Gravity.LEFT
            "right" -> Gravity.RIGHT
            "top" -> Gravity.TOP
            "bottom" -> Gravity.BOTTOM
            "center" -> Gravity.CENTER
            "center_horizontal" -> Gravity.CENTER_HORIZONTAL
            "center_vertical" -> Gravity.CENTER_VERTICAL
            "start" -> Gravity.START
            "end" -> Gravity.END
            else -> 0
        }
        return g
    }
}
