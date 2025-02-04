package com.michelvilleneuve.fieldcad

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.ViewTreeObserver
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Objects
import android.graphics.Rect
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.RelativeLayout
import androidx.core.view.GestureDetectorCompat
import androidx.viewpager2.widget.ViewPager2
import com.michelvilleneuve.fieldcad.MyCustomDrawingView.Shape

class MainActivity : AppCompatActivity(), PageChangeListener {

    private var pages = mutableListOf<DrawingPage>()
    private var currentPageIndex = -1
    private lateinit var drawingView: MyCustomDrawingView
    private lateinit var pageNameTextView: TextView


    private val modeResetHandler = Handler(Looper.getMainLooper())
    private val modeResetRunnable = Runnable {
        drawingView.setDrawingMode(DrawingMode.NONE)
        resetButtonColors()
    }

    private lateinit var buttonFreehand: AppCompatImageButton
    private lateinit var buttonAuto: AppCompatImageButton
    private lateinit var buttonCircle: AppCompatImageButton
    private lateinit var buttonArc: AppCompatImageButton
    private lateinit var buttonRectangle: AppCompatImageButton
    private lateinit var buttonText: AppCompatImageButton
    private lateinit var buttonErase: AppCompatImageButton
    private lateinit var buttonEraseEffect: AppCompatImageButton
    private lateinit var buttonClear: AppCompatImageButton

    private val gson = Gson()
    private val sharedPreferences by lazy {
        getSharedPreferences("drawing_app", Context.MODE_PRIVATE)
    }

    private lateinit var viewPager: ViewPager2
    private lateinit var pageAdapter: PageAdapter
    private lateinit var gestureDetector: GestureDetectorCompat
    var isHandDrawnModeEnabled = false
    private lateinit var myCustomDrawingView: MyCustomDrawingView

    private var areGripsDisplayed = false
    var selectedShape: Shape? = null


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        myCustomDrawingView = findViewById(R.id.drawingView)



        // Setup ViewPager2 and the adapter
        viewPager = findViewById(R.id.viewPager)
        pageAdapter = PageAdapter(pages)
        viewPager.adapter = pageAdapter

        val rootLayout = findViewById<RelativeLayout>(R.id.rootLayout)


        if (rootLayout == null) {
            throw NullPointerException("rootLayout is null. Check your XML layout for correct ID.")
        }

        drawingView = findViewById(R.id.drawingView)

        Objects.requireNonNull(getSupportActionBar())?.setDisplayShowHomeEnabled(true);
        getSupportActionBar()?.setLogo(R.drawable.mvblanc_logo_24);
        getSupportActionBar()?.setDisplayUseLogoEnabled(true);


        pageNameTextView = findViewById(R.id.pageNameTextView)

        buttonFreehand = findViewById(R.id.buttonFreehand)
        buttonAuto = findViewById(R.id.buttonAuto)
        buttonCircle = findViewById(R.id.buttonCircle)
        buttonArc = findViewById(R.id.buttonArc)
        buttonRectangle = findViewById(R.id.buttonRectangle)
        buttonText = findViewById(R.id.buttonText)

        buttonEraseEffect = findViewById(R.id.buttonEraseEffect)
        buttonClear = findViewById(R.id.buttonClear)



     //   setupButtonListeners()
        loadPages()

        if (pages.isEmpty()) {
            addPage("Page 1")
        }

        findViewById<AppCompatImageButton>(R.id.buttonFreehand).setOnClickListener {
            drawingView.setDrawingMode(DrawingMode.FREEHAND)
        }
        findViewById<AppCompatImageButton>(R.id.buttonAuto).setOnClickListener {
            drawingView.setDrawingMode(DrawingMode.AUTO)
        }
        findViewById<AppCompatImageButton>(R.id.buttonCircle).setOnClickListener {
            drawingView.setDrawingMode(DrawingMode.CIRCLE)
        }
        findViewById<AppCompatImageButton>(R.id.buttonArc).setOnClickListener {
            drawingView.setDrawingMode(DrawingMode.ARC)
        }
        findViewById<AppCompatImageButton>(R.id.buttonRectangle).setOnClickListener {
            drawingView.setDrawingMode(DrawingMode.RECTANGLE)
        }
        findViewById<AppCompatImageButton>(R.id.buttonText).setOnClickListener {
            drawingView.setDrawingMode(DrawingMode.TEXT)
        }


        findViewById<AppCompatImageButton>(R.id.buttonEraseEffect).setOnClickListener {
            drawingView.setDrawingMode(DrawingMode.ERASER_EFFECT)
        }
        findViewById<AppCompatImageButton>(R.id.buttonClear).setOnClickListener {
            drawingView.clearCanvas()
        }
        drawingView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                drawingView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                drawingView.zoomToFitPage()
            }
        })
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            rootLayout.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootLayout.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight > screenHeight * 0.15) {
                // Keyboard is open, adjust UI
            } else {
                // Keyboard is closed, restore original UI
            }

        }
        // Set a listener for page change
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPageIndex = position
                loadCurrentPage()
            }
        })

        // Get reference to the custom drawing view
        val myCustomDrawingView: MyCustomDrawingView = findViewById(R.id.drawingView)

        // Set the page change listener for the drawing view
        myCustomDrawingView.setPageChangeListener(this)


        // Initialize the gesture detector with a new instance of GestureListener (not linked to drawingView)
        gestureDetector = GestureDetectorCompat(this, PageSwipeGestureListener())

        // Get reference to the TextView for the page name
        val pageNameTextView: TextView = findViewById(R.id.pageNameTextView)

        // Set the touch listener to handle swipe gestures ONLY on the TextView
        pageNameTextView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        isHandDrawnModeEnabled = sharedPref.getBoolean("hand_drawn_mode", false)
        // Set the hand-drawn mode based on saved preference
        myCustomDrawingView.setHandDrawnMode(isHandDrawnModeEnabled)

    }

    private inner class PageSwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 50
        private val SWIPE_VELOCITY_THRESHOLD = 50
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 != null && e2 != null) {
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe right (Previous Page)
                            loadPreviousPage()
                        } else {
                            // Swipe left (Next Page)
                            loadNextPage()
                        }
                        return true
                    }
                }
            }
            return false

        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Retrieve the current mode from shared preferences
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val isHandDrawnModeEnabled = sharedPref.getBoolean("hand_drawn_mode", false)

        // Update the menu item title based on the mode
        menu?.findItem(R.id.action_toggle_hand_draw)?.title = if (isHandDrawnModeEnabled) {
            "Straight Mode"
        } else {
            "Hand Mode"
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_page -> {
                addPage("Page ${pages.size + 1}")
                true
            }
            R.id.action_switch_page -> {
                showPageSelectionDialog()
                true
            }
            R.id.action_save -> {
                saveData()
                true
            }
            R.id.action_delete_page -> {
                deleteCurrentPage()
                true
            }
            R.id.action_toggle_hand_draw -> {
                // Toggle hand-drawn mode on and off
                val isEnabled = !myCustomDrawingView.isHandDrawnModeEnabled
                myCustomDrawingView.setHandDrawnMode(isEnabled)

                // Save the preference
                val sharedPref = getPreferences(Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putBoolean("hand_drawn_mode", isEnabled)
                    apply()
                }

                // Update the title based on the mode
                if (isEnabled) {
                    item.title = "Straight Mode"
                } else {
                    item.title = "Hand Mode"
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    fun deleteCurrentPage() {
        if (pages.size > 1) { // Ensure at least one page remains
            pages.removeAt(currentPageIndex)
            if (currentPageIndex >= pages.size) {
                currentPageIndex = pages.size - 1
            }
            loadCurrentPage()
            drawingView.invalidate()
            AlertDialog.Builder(this)
                .setMessage("Page deleted.")
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Cannot delete the last remaining page.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    private fun saveData() {
        saveCurrentPage()
  //      val gson = Gson()
        val json = gson.toJson(pages)
        val file = File(filesDir, "drawing_pages.json")
        file.writeText(json)
        AlertDialog.Builder(this)
            .setMessage("Page saved.")
            .show()
        invalidate()
    }

    private fun loadData() {
        val file = File(filesDir, "drawing_pages.json")
        if (file.exists()) {
            val json = file.readText()
            val gson = Gson()
            val loadedPages = gson.fromJson(json, Array<DrawingPage>::class.java).toMutableList()
            pages = loadedPages
        }
    }
 //   private fun updateActionBarTitle() {
 //       supportActionBar?.title = "M2TS - page ${currentPageIndex + 1}"
 //   }

    private fun addPage(name: String) {
        val newPage = DrawingPage(name)
        pages.add(newPage)
        currentPageIndex = pages.size - 1
        pageAdapter.notifyDataSetChanged()
        viewPager.setCurrentItem(currentPageIndex, true)
        loadCurrentPage()
        saveData()
    }

    fun switchPage(index: Int) {
        if (index >= 0 && index < pages.size) {
            saveCurrentPage()
            currentPageIndex = index
            loadCurrentPage()
            drawingView.invalidate()
        }
    }

    private fun saveCurrentPage() {
        if (currentPageIndex >= 0) {
            val currentPage = pages[currentPageIndex]
            currentPage.lines = drawingView.lines
            currentPage.texts = drawingView.texts
            currentPage.circles = drawingView.circles
            currentPage.arcs = drawingView.arcs
            currentPage.rectangles = drawingView.rectangles
        }
    }
    private fun loadCurrentPage() {
        if (currentPageIndex >= 0) {
            val currentPage = pages[currentPageIndex]
            val totalPages = pages.size

            drawingView.lines = currentPage.lines
            drawingView.texts = currentPage.texts
            drawingView.circles = currentPage.circles
            drawingView.arcs = currentPage.arcs
            drawingView.rectangles = currentPage.rectangles

            pageNameTextView.text = "Page ${currentPageIndex + 1} of Page $totalPages   < swipe left to right >"
            drawingView.invalidate()
        }
    }
    override fun loadNextPage() {
        if (currentPageIndex < pages.size - 1) {
            currentPageIndex++
            loadCurrentPage()
        }
    }

    override fun loadPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            loadCurrentPage()
        }
    }

    private fun loadCurrentPage3() {
        if (currentPageIndex >= 0) {
            val currentPage = pages[currentPageIndex]
            val totalPages = pages.size

            // Set the content of the drawing view to the current page
            drawingView.lines = currentPage.lines
            drawingView.texts = currentPage.texts
            drawingView.circles = currentPage.circles
            drawingView.arcs = currentPage.arcs
            drawingView.rectangles = currentPage.rectangles

            // Display the name of the current page
            pageNameTextView.text = currentPage.name

            // Invalidate the view to force a redraw
            drawingView.invalidate()

            // Update the display for the current page number and total pages
            val currentPageNumber = currentPageIndex + 1  // Convert to 1-based index
            val pageDisplayText = "Page $currentPageNumber of Page $totalPages"

            // Assuming you have a TextView to display this page information
            pageNameTextView.text = pageDisplayText  // Replace with your actual TextView
        }
    }
    private fun loadCurrentPage2() {
        if (currentPageIndex >= 0) {
            val currentPage = pages[currentPageIndex]
            val totalPages = pages.size

            drawingView.lines = currentPage.lines
            drawingView.texts = currentPage.texts
            drawingView.circles = currentPage.circles
            drawingView.arcs = currentPage.arcs
            drawingView.rectangles = currentPage.rectangles
            pageNameTextView.text = currentPage.name
            drawingView.invalidate()
        }
    }

    private fun loadCurrentPage1() {
        if (currentPageIndex >= 0) {
            val currentPage = pages[currentPageIndex]
            pageAdapter.notifyItemChanged(currentPageIndex)
        }
    }

    private fun saveCurrentPage1() {
        if (currentPageIndex >= 0) {
            val currentPage = pages[currentPageIndex]
            currentPage.lines = drawingView.lines
            currentPage.texts = drawingView.texts
            currentPage.circles = drawingView.circles
            currentPage.arcs = drawingView.arcs
            currentPage.rectangles = drawingView.rectangles
        }
    }

    private fun showPageSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_page_selection, null)
        val listView = dialogView.findViewById<ListView>(R.id.pageListView)
        val pageNames = pages.map { it.name }.toTypedArray()

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pageNames)
        listView.adapter = adapter

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
            .setCancelable(true)
            .setNegativeButton("Cancel", null)
            .create()

        val dialog = builder.show()

        listView.setOnItemClickListener { _, _, which, _ ->
            currentPageIndex = which
            loadCurrentPage()
            dialog.dismiss()
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            showRenameDialog(position, dialog)
            true
        }
    }

    private fun showRenameDialog(position: Int, parentDialog: AlertDialog) {
        parentDialog.dismiss() // Dismiss the parent dialog

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Rename Page")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(pages[position].name)

        builder.setView(input)

        builder.setPositiveButton("Rename") { dialog, _ ->
            val newName = input.text.toString()
            if (newName.isNotBlank()) {
                pages[position].name = newName
                saveData() // Save the updated name to file
                showPageSelectionDialog() // Refresh the page selection dialog
            }
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
            showPageSelectionDialog() // Refresh the page selection dialog after cancel
        }

        builder.show()
    }

    private fun setupButtonListeners() {
        buttonFreehand.setOnClickListener { updateButtonSelection(buttonFreehand) }
        buttonAuto.setOnClickListener { updateButtonSelection(buttonAuto) }
        buttonCircle.setOnClickListener { updateButtonSelection(buttonCircle) }
        buttonArc.setOnClickListener { updateButtonSelection(buttonArc) }
        buttonRectangle.setOnClickListener { updateButtonSelection(buttonRectangle) }
        buttonText.setOnClickListener { updateButtonSelection(buttonText) }
        buttonErase.setOnClickListener { updateButtonSelection(buttonErase) }
        buttonEraseEffect.setOnClickListener { updateButtonSelection(buttonEraseEffect) }
        buttonClear.setOnClickListener { updateButtonSelection(buttonClear) }
    }

    private fun updateButtonSelection(selectedButton: AppCompatImageButton) {
        val buttons = listOf(buttonFreehand, buttonAuto, buttonCircle, buttonArc,
            buttonRectangle, buttonText, buttonErase, buttonEraseEffect, buttonClear)
        for (button in buttons) {
            button.isSelected = (button == selectedButton)
        }
    }
    private fun resetButtonColors() {
        buttonFreehand.isSelected = false
        buttonAuto.isSelected = false
        buttonCircle.isSelected = false
        buttonArc.isSelected = false
        buttonRectangle.isSelected = false
        buttonText.isSelected = false
        buttonEraseEffect.isSelected = false
        buttonErase.isSelected = false

    }
    private fun savePages() {
        saveCurrentPage()
        val json = gson.toJson(pages)
        sharedPreferences.edit().putString("pages", json).apply()
    }

    // Load pages from shared preferences
    private fun loadPages() {
        val json = sharedPreferences.getString("pages", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<DrawingPage>>() {}.type
            pages = gson.fromJson(json, type)
            currentPageIndex = 0
            if (pages.isNotEmpty()) {
                loadCurrentPage()
            }
        }
    }
    override fun onPause() {
        super.onPause()
        savePages()
    }
    override fun onDestroy() {

        selectedShape = null
        areGripsDisplayed = false
        DrawingMode.NONE
        super.onDestroy()
        modeResetHandler.removeCallbacks(modeResetRunnable)
    }
    private fun setupButton(button: AppCompatImageButton) {
        button.setOnClickListener {
            // Toggle the selected state
            button.isSelected = !button.isSelected
            resetOtherButtons(button)
        }
    }

    private fun resetOtherButtons(selectedButton: AppCompatImageButton) {
        val buttons = listOf(buttonFreehand, buttonAuto, buttonCircle, buttonArc, buttonRectangle, buttonText, buttonErase, buttonClear)
        for (button in buttons) {
            if (button != selectedButton) {
                button.isSelected = false
            }
        }
    }
    private var isHandleMode = false

    private fun toggleHandleMode() {
        isHandleMode = !isHandleMode
        if (isHandleMode) {
            Toast.makeText(this, "Handle mode activated", Toast.LENGTH_SHORT).show()
        } else {
            drawingView.resetSelections()
            Toast.makeText(this, "Handle mode deactivated", Toast.LENGTH_SHORT).show()
        }
    }
    private fun invalidate() {
        // Redraw the view, e.g., by calling invalidate() on your custom view
    }


}

